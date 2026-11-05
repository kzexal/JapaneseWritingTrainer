package com.example.japanesewritingtrainer

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-6 scheduler using the published default 21 parameters.
 *
 * The FSRS memory model calculates stability and difficulty. The app-level
 * learning policy keeps Again/Hard cards in LEARNING for a short retry;
 * Good/Easy graduate them into REVIEW.
 */
class Fsrs6Scheduler(
    private val desiredRetention: Double = DEFAULT_DESIRED_RETENTION,
    private val maximumIntervalDays: Int = DEFAULT_MAXIMUM_INTERVAL_DAYS
) : CardReviewScheduler {

    init {
        require(desiredRetention in 0.01..0.999) {
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
        val elapsedWholeDays = elapsedDays.roundToInt()

        val grade = rating.toGrade()
        val previousStability = progress.stabilityDays
        val previousDifficulty = progress.difficulty

        val memory = if (previousStability == null || previousDifficulty == null) {
            MemoryState(
                stability = initialStability(grade),
                difficulty = initialDifficulty(grade)
            )
        } else {
            val stability = previousStability.coerceIn(MIN_STABILITY, MAX_STABILITY)
            val difficulty = previousDifficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
            MemoryState(
                stability = when {
                    elapsedWholeDays == 0 -> shortTermStability(stability, grade)
                    grade == GRADE_AGAIN -> stabilityAfterFailure(
                        stability,
                        difficulty,
                        retrievability(elapsedWholeDays.toDouble(), stability)
                    )
                    else -> stabilityAfterSuccess(
                        stability,
                        difficulty,
                        retrievability(elapsedWholeDays.toDouble(), stability),
                        grade
                    )
                },
                difficulty = nextDifficulty(difficulty, grade)
            )
        }

        val wasReview = progress.status == CardStatus.REVIEW
        val isLearningRetry = progress.status == CardStatus.LEARNING
        val nextStatus: CardStatus
        val intervalMillis: Long
        val scheduledDays: Double

        when {
            grade == GRADE_AGAIN && wasReview -> {
                nextStatus = CardStatus.LEARNING
                intervalMillis = RELEARNING_STEP_MILLIS
                scheduledDays = RELEARNING_STEP_DAYS
            }
            grade == GRADE_AGAIN -> {
                nextStatus = CardStatus.LEARNING
                intervalMillis = LEARNING_AGAIN_STEP_MILLIS
                scheduledDays = LEARNING_AGAIN_STEP_DAYS
            }
            grade == GRADE_HARD && (progress.status == CardStatus.NEW || isLearningRetry) -> {
                nextStatus = CardStatus.LEARNING
                intervalMillis = LEARNING_HARD_STEP_MILLIS
                scheduledDays = LEARNING_HARD_STEP_DAYS
            }
            else -> {
                nextStatus = CardStatus.REVIEW
                val intervalDays = intervalDays(memory.stability)
                intervalMillis = intervalDays * MILLIS_PER_DAY
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
            lapses = progress.lapses + if (wasReview && grade == GRADE_AGAIN) 1 else 0,
            reviewCount = progress.reviewCount + 1,
            correctCount = progress.correctCount + if (grade == GRADE_AGAIN) 0 else 1,
            lastReviewedAtEpochMillis = nowEpochMillis
        )
    }

    /** Probability of recall at a given elapsed time, from 0.0 to 1.0. */
    fun retrievability(elapsedDays: Double, stabilityDays: Double): Double {
        require(elapsedDays >= 0.0)
        require(stabilityDays > 0.0)
        val factor = forgettingFactor()
        val decay = -WEIGHTS[20]
        return (1.0 + factor * elapsedDays / stabilityDays).pow(decay)
    }

    /** Computes the rounded whole-day interval for a stability value. */
    fun intervalDays(stabilityDays: Double): Long {
        require(stabilityDays > 0.0)
        val decay = -WEIGHTS[20]
        val rawInterval = stabilityDays / forgettingFactor() *
            (desiredRetention.pow(1.0 / decay) - 1.0)
        return rawInterval.roundToInt()
            .coerceIn(1, maximumIntervalDays)
            .toLong()
    }

    private fun initialStability(grade: Int): Double =
        WEIGHTS[grade - 1].coerceIn(MIN_STABILITY, MAX_STABILITY)

    private fun initialDifficulty(grade: Int): Double =
        (WEIGHTS[4] - exp(WEIGHTS[5] * (grade - 1)) + 1.0)
            .coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private fun nextDifficulty(difficulty: Double, grade: Int): Double {
        val delta = -WEIGHTS[6] * (grade - 3)
        val damped = delta * (10.0 - difficulty) / 9.0
        val tentative = difficulty + damped
        val easyInitialDifficulty = initialDifficulty(GRADE_EASY)
        return (WEIGHTS[7] * easyInitialDifficulty +
            (1.0 - WEIGHTS[7]) * tentative)
            .coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    private fun stabilityAfterSuccess(
        stability: Double,
        difficulty: Double,
        recallability: Double,
        grade: Int
    ): Double {
        val hardPenalty = if (grade == GRADE_HARD) WEIGHTS[15] else 1.0
        val easyBonus = if (grade == GRADE_EASY) WEIGHTS[16] else 1.0
        val increase = exp(WEIGHTS[8]) *
            (11.0 - difficulty) *
            stability.pow(-WEIGHTS[9]) *
            (exp((1.0 - recallability) * WEIGHTS[10]) - 1.0) *
            hardPenalty * easyBonus + 1.0
        return (stability * increase).coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    private fun stabilityAfterFailure(
        stability: Double,
        difficulty: Double,
        recallability: Double
    ): Double {
        val candidate = WEIGHTS[11] *
            difficulty.pow(-WEIGHTS[12]) *
            ((stability + 1.0).pow(WEIGHTS[13]) - 1.0) *
            exp((1.0 - recallability) * WEIGHTS[14])
        // FSRS-6 caps post-lapse stability to prevent an excessive increase.
        val cap = stability / exp(WEIGHTS[17] * WEIGHTS[18])
        return minOf(candidate, cap).coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    private fun shortTermStability(stability: Double, grade: Int): Double {
        val increase = exp(WEIGHTS[17] * (grade - 3.0 + WEIGHTS[18])) *
            stability.pow(-WEIGHTS[19])
        // FSRS-6 does not let Hard/Good/Easy reduce stability on same-day reviews.
        val adjustedIncrease = if (grade >= GRADE_HARD) maxOf(increase, 1.0) else increase
        return (stability * adjustedIncrease).coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    private fun forgettingFactor(): Double {
        val decay = -WEIGHTS[20]
        return 0.9.pow(1.0 / decay) - 1.0
    }

    private data class MemoryState(
        val stability: Double,
        val difficulty: Double
    )

    private fun ReviewRating.toGrade(): Int = when (this) {
        ReviewRating.AGAIN -> GRADE_AGAIN
        ReviewRating.HARD -> GRADE_HARD
        ReviewRating.GOOD -> GRADE_GOOD
        ReviewRating.EASY -> GRADE_EASY
    }

    companion object {
        const val DEFAULT_DESIRED_RETENTION = 0.9
        const val DEFAULT_MAXIMUM_INTERVAL_DAYS = 36_500

        private const val MILLIS_PER_DAY = 86_400_000L
        private const val MIN_STABILITY = 0.001
        private const val MAX_STABILITY = 36_500.0
        private const val MIN_DIFFICULTY = 1.0
        private const val MAX_DIFFICULTY = 10.0

        private const val GRADE_AGAIN = 1
        private const val GRADE_HARD = 2
        private const val GRADE_GOOD = 3
        private const val GRADE_EASY = 4

        // App learning policy layered over the FSRS-6 memory model.
        private const val LEARNING_AGAIN_STEP_MILLIS = 60_000L
        private const val LEARNING_HARD_STEP_MILLIS = 6 * 60_000L
        private const val RELEARNING_STEP_MILLIS = 10 * 60_000L
        private const val LEARNING_AGAIN_STEP_DAYS = 1.0 / 1_440.0
        private const val LEARNING_HARD_STEP_DAYS = 6.0 / 1_440.0
        private const val RELEARNING_STEP_DAYS = 10.0 / 1_440.0

        /** Default FSRS-6 weights published by Open Spaced Repetition. */
        val WEIGHTS = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194,
            0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629,
            1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542
        )
    }
}
