package com.example.japanesewritingtrainer

/**
 * Thin interface to FSRS-rs 6.6.2.
 *
 * Native binaries are not wired into Gradle yet; calling this before the
 * native library is packaged intentionally fails with UnsatisfiedLinkError.
 */
internal object UpstreamFsrsNative {
    init {
        System.loadLibrary("japanese_writing_fsrs")
    }

    private external fun nativeReview(inputJson: String): String

    fun review(
        stability: Double?,
        difficulty: Double?,
        elapsedDays: Int,
        desiredRetention: Double = 0.9
    ): String {
        require((stability == null) == (difficulty == null)) {
            "stability and difficulty must both be null or both be provided"
        }
        require(elapsedDays >= 0)
        require(desiredRetention in 0.01..0.999)

        val json = buildString {
            append("{")
            append("\"stability\":").append(stability ?: "null").append(',')
            append("\"difficulty\":").append(difficulty ?: "null").append(',')
            append("\"elapsed_days\":").append(elapsedDays).append(',')
            append("\"desired_retention\":").append(desiredRetention)
            append("}")
        }
        return nativeReview(json)
    }
}
