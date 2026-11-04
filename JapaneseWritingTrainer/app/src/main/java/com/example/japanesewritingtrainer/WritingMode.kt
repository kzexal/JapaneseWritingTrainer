package com.example.japanesewritingtrainer

/**
 * Các chế độ luyện viết.
 *
 * Trace dùng mẫu trực quan; Guided hiển thị hướng dẫn nét;
 * Recall yêu cầu người học tự nhớ nét trước khi xem đáp án.
 */
enum class WritingMode {
    TRACE,
    GUIDED,
    RECALL;

    /** Trace không bắt buộc thứ tự nét; Guided và Recall thì có. */
    val requiresStrokeValidation: Boolean
        get() = this != TRACE

    /** Chỉ Guided được hiển thị đường nét và hướng dẫn trong lúc viết. */
    val showsStrokeGuidance: Boolean
        get() = this == GUIDED
}
