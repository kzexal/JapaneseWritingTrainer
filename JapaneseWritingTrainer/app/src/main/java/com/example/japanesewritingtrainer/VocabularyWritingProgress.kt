package com.example.japanesewritingtrainer

/**
 * Tracks which glyph in a Vocabulary writing sequence has been completed.
 *
 * A Vocabulary card is reviewed only after this sequence is complete. Each
 * glyph still uses the existing WritingCanvas and its own stroke geometry.
 */
data class VocabularyWritingProgress(
    val sequence: VocabularyWritingSequence,
    val completedCharacterCount: Int = 0
) {
    init {
        require(sequence.isWritable) {
            "Only fully supported Vocabulary sequences can be practiced"
        }
        require(completedCharacterCount in 0..sequence.characters.size) {
            "completedCharacterCount is outside the sequence"
        }
    }

    val currentCharacter: CharacterData?
        get() = sequence.characters.getOrNull(completedCharacterCount)

    val isComplete: Boolean
        get() = completedCharacterCount == sequence.characters.size

    fun completeCurrentCharacter(): VocabularyWritingProgress =
        if (isComplete) this else copy(
            completedCharacterCount = completedCharacterCount + 1
        )

    fun reset(): VocabularyWritingProgress =
        if (completedCharacterCount == 0) this else copy(completedCharacterCount = 0)
}
