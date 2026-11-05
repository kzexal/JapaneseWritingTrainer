package com.example.japanesewritingtrainer

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * FSRS-rs 6.6.2 scheduler.
 *
 * The upstream library calculates memory states and review intervals.
 * Learning/relearning steps remain application policy and are intentionally
 * kept separate from the FSRS memory model.
 */
class Fsrs6Scheduler(
    private val desiredRetention: Double = DEFAULT_DESIRED_RETENTION,
    private val maximumIntervalDays: Int = DEFAULT_MAXIMUM_INTERVAL_DAYS,
    private val calculateStates: (
        stability: Double?,
        difficulty: Double?,
        elapsedDays: Int,
        desiredRetention: Double
    ) -> FsrsReviewResult = { stability, difficulty, elapsedDays, retention ->
        UpstreamFsrsNative.review(
            stability = stability,
            difficulty = difficulty,
            elapsedDays = elapsedDays,
            desiredRetention = retention
        )
    }
) : CardReviewScheduler {

    init {
        require(desiredRetention.isFinite() && desiredRetention in 0.01..0.999) {
            "desiredRetention must be between 0.01 and 0.999"
        }
        require(maximumIntervalDays >= 1) {
            "maximumIntervalDays must be at least 1"
        }
    }

    override fun review(
        progress: CardProgress,
        rating: ReviewRating,
        nowEpochMillis: Long
    ): CardProgress {
        val previousReview = progress.lastReviewedAtEpochMillis
        val elapsedDays = previousReview?.let {
            ((nowEpochMillis - it).coerceAtLeast(0L)).toDouble() / MILLIS_PER_DAY
        } ?: 0.0
        val elapsedWholeDays = floor(elapsedDays).toInt().coerceAtLeast(0)

        // A partial memory state is treated as a new card, matching the
        // previous scheduler's behavior and the JNI bridge's input contract.
        val hasMemoryState =
            progress.stabilityDays != null && progress.difficulty != null

        val states = calculateStates(
            if (hasMemoryState) progress.stabilityDays else null,
            if (hasMemoryState) progress.difficulty else null,
            elapsedWholeDays,
            desiredRetention
        )

        val memory = when (rating) {
            ReviewRating.AGAIN -> states.again
            ReviewRating.HARD -> states.hard
            ReviewRating.GOOD -> states.good
            ReviewRating.EASY -> states.easy
        }

        val wasReview = progress.status == CardStatus.REVIEW
        val isLearningRetry = progress.status == CardStatus.LEARNING
        val nextStatus: CardStatus
        val intervalMillis: Long
        val scheduledDays: Double

        when {
            rating == ReviewRating.AGAIN && wasReview -> {
                nextStatus = CardStatus.LEARNING
                intervalMillis = RELEARNING_STEP_MILLIS
                scheduledDays = RELEARNING_STEP_DAYS
            }
            rating == ReviewRating.AGAIN -> {
                nextStatus = CardStatus.LEARNING
                intervalMillis = LEARNING_AGAIN_STEP_MILLIS
                scheduledDays = LEARNING_AGAIN_STEP_DAYS
            }
            rating == ReviewRating.HARD &&
                (progress.status == CardStatus.NEW || isLearningRetry) -> {
                nextStatus = CardStatus.LEARNING
                intervalMillis = LEARNING_HARD_STEP_MILLIS
                scheduledDays = LEARNING_HARD_STEP_DAYS
            }
            else -> {
                nextStatus = CardStatus.REVIEW
                val intervalDays = memory.intervalDays
                    .roundToInt()
                    .coerceIn(1, maximumIntervalDays)
                intervalMillis = intervalDays.toLong() * MILLIS_PER_DAY
                scheduledDays = intervalDays.toDouble()
            }
        }

        val dueAt = Math.addExact(nowEpochMillis, intervalMillis)
        return progress.copy(
            status = nextStatus,
            dueAtEpochMillis = dueAt,
            stabilityDays = memory.stability,
            difficulty = memory.difficulty,
            scheduledDays = scheduledDays,
            elapsedDays = elapsedDays,
            reps = progress.reps + 1,
            lapses = progress.lapses + if (wasReview && rating == ReviewRating.AGAIN) 1 else 0,
            reviewCount = progress.reviewCount + 1,
            correctCount = progress.correctCount + if (rating == ReviewRating.AGAIN) 0 else 1,
            lastReviewedAtEpochMillis = nowEpochMillis
        )
    }

    companion object {
        const val DEFAULT_DESIRED_RETENTION = 0.9
        const val DEFAULT_MAXIMUM_INTERVAL_DAYS = 36_500

        private const val MILLIS_PER_DAY = 86_400_000L

        // Application learning/relearning policy.
        private const val LEARNING_AGAIN_STEP_MILLIS = 60_000L
        private const val LEARNING_HARD_STEP_MILLIS = 6 * 60_000L
        private const val RELEARNING_STEP_MILLIS = 10 * 60_000L

        private const val LEARNING_AGAIN_STEP_DAYS = 1.0 / 1_440.0
        private const val LEARNING_HARD_STEP_DAYS = 6.0 / 1_440.0
        private const val RELEARNING_STEP_DAYS = 10.0 / 1_440.0
    }
}
