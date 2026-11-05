package com.example.japanesewritingtrainer

import org.json.JSONObject

/** Typed adapter for the upstream FSRS-rs 6.6.2 JNI bridge. */
internal object UpstreamFsrsNative {
    init { System.loadLibrary("japanese_writing_fsrs") }

    private external fun nativeReview(inputJson: String): String

    fun review(
        stability: Double?,
        difficulty: Double?,
        elapsedDays: Int,
        desiredRetention: Double = 0.9
    ): FsrsReviewResult {
        require((stability == null) == (difficulty == null)) {
            "stability and difficulty must both be null or both be provided"
        }
        require(stability == null || stability.isFinite() && stability > 0.0)
        require(difficulty == null || difficulty.isFinite() && difficulty in 1.0..10.0)
        require(elapsedDays >= 0)
        require(desiredRetention.isFinite() && desiredRetention in 0.01..0.999)

        val json = JSONObject()
            .put("stability", stability ?: JSONObject.NULL)
            .put("difficulty", difficulty ?: JSONObject.NULL)
            .put("elapsed_days", elapsedDays)
            .put("desired_retention", desiredRetention)
        val response = JSONObject(nativeReview(json.toString()))
        if (response.has("error")) {
            error("Upstream FSRS failed: " + response.getString("error"))
        }
        return FsrsReviewResult(
            again = response.getJSONObject("again").toState(),
            hard = response.getJSONObject("hard").toState(),
            good = response.getJSONObject("good").toState(),
            easy = response.getJSONObject("easy").toState()
        )
    }

    private fun JSONObject.toState() = FsrsReviewState(
        stability = getDouble("stability"),
        difficulty = getDouble("difficulty"),
        intervalDays = getDouble("interval_days")
    )
}

internal data class FsrsReviewState(
    val stability: Double, val difficulty: Double, val intervalDays: Double
)
internal data class FsrsReviewResult(
    val again: FsrsReviewState, val hard: FsrsReviewState,
    val good: FsrsReviewState, val easy: FsrsReviewState
)
