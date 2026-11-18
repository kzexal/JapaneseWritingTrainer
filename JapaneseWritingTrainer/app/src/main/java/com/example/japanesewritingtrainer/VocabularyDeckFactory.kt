package com.example.japanesewritingtrainer

/**
 * A Vocabulary entry that cannot be used for writing with the selected target.
 * It remains in the source library; this result does not delete or mutate content.
 */
data class UnsupportedVocabularyWriting(
    val vocabulary: VocabularyData,
    val target: VocabularyWritingTarget,
    val unsupportedSymbols: List<String>
)

/**
 * Vocabulary-specific view of the existing learning deck model.
 *
 * Only entries whose complete target text has valid stroke geometry become
 * writing cards. Unsupported entries are returned separately for an explicit
 * non-writing learning path; they are never silently transliterated.
 */
data class VocabularyDeckCatalog(
    val deck: WritingDeck,
    val cards: List<WritingCard>,
    val sequencesByCardId: Map<String, VocabularyWritingSequence>,
    val unsupportedEntries: List<UnsupportedVocabularyWriting>
)

object VocabularyDeckFactory {

    fun fromVocabulary(
        vocabulary: List<VocabularyData>,
        availableCharacters: List<CharacterData>,
        target: VocabularyWritingTarget
    ): VocabularyDeckCatalog {
        require(vocabulary.map { it.id }.distinct().size == vocabulary.size) {
            "Vocabulary IDs must be unique"
        }

        val targetName = target.name.lowercase()
        val deckId = "vocabulary_$targetName"
        val cards = mutableListOf<WritingCard>()
        val sequences = linkedMapOf<String, VocabularyWritingSequence>()
        val unsupported = mutableListOf<UnsupportedVocabularyWriting>()

        vocabulary.forEachIndexed { index, item ->
            val sequence = VocabularyWritingMapper.resolve(
                vocabulary = item,
                target = target,
                availableCharacters = availableCharacters
            )

            if (!sequence.isWritable) {
                unsupported += UnsupportedVocabularyWriting(
                    vocabulary = item,
                    target = target,
                    unsupportedSymbols = sequence.unsupportedSymbols
                )
                return@forEachIndexed
            }

            // Keep Japanese and Reading progress independent even for the same word.
            val cardId = "vocab_${targetName}_${item.id.removePrefix("vocab_")}"
            cards += WritingCard(
                id = cardId,
                deckId = deckId,
                // For this deck, characterId references VocabularyData.id;
                // the word's glyph sequence is held in sequencesByCardId.
                characterId = item.id,
                character = sequence.sourceText,
                reading = item.reading,
                group = item.group.orEmpty(),
                strokeCount = sequence.characters.sumOf { it.strokeCount },
                order = index
            )
            sequences[cardId] = sequence
        }

        val deck = WritingDeck(
            id = deckId,
            name = if (target == VocabularyWritingTarget.JAPANESE) {
                "Vocabulary · Japanese"
            } else {
                "Vocabulary · Reading"
            },
            description = if (target == VocabularyWritingTarget.JAPANESE) {
                "Luyện viết từ vựng bằng chính tả tiếng Nhật."
            } else {
                "Luyện viết cách đọc của từ vựng."
            },
            characterType = deckId,
            cardIds = cards.map { it.id }
        )

        return VocabularyDeckCatalog(
            deck = deck,
            cards = cards,
            sequencesByCardId = sequences,
            unsupportedEntries = unsupported
        )
    }
}
