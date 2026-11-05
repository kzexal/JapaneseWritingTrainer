package com.example.japanesewritingtrainer

data class DeckCatalog(
    val decks: List<WritingDeck>,
    val cards: List<WritingCard>
) {
    fun cardsForDeck(deckId: String): List<WritingCard> {
        val cardIds = decks.firstOrNull { it.id == deckId }?.cardIds?.toSet()
            ?: return emptyList()
        return cards.filter { it.id in cardIds }.sortedBy { it.order }
    }
}

object DeckFactory {

    /**
     * Tạo một Deck cho mỗi loại ký tự (ví dụ Hiragana).
     * Các group như a, ka, sa được giữ làm metadata của Card,
     * không tách thành các Deck riêng.
     */
    fun fromCharacters(characters: List<CharacterData>): DeckCatalog {
        require(characters.map { it.id }.distinct().size == characters.size) {
            "Character IDs must be unique"
        }

        val cards = mutableListOf<WritingCard>()
        val decks = characters
            .groupBy { it.type.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
            .toSortedMap()
            .map { (type, items) ->
                val deckId = "japanese_$type"
                val orderedItems = items

                val deckCards = orderedItems.mapIndexed { index, character ->
                    WritingCard(
                        id = character.id,
                        deckId = deckId,
                        characterId = character.id,
                        character = character.character,
                        reading = character.reading,
                        group = character.group,
                        strokeCount = character.strokeCount,
                        order = index
                    )
                }
                cards += deckCards

                WritingDeck(
                    id = deckId,
                    name = type.toDeckName(),
                    description = "Luyện viết " + type.toDeckName(),
                    characterType = type,
                    cardIds = deckCards.map { it.id }
                )
            }

        return DeckCatalog(decks = decks, cards = cards)
    }

    private fun String.toDeckName(): String =
        when (this) {
            "hiragana" -> "Hiragana"
            "katakana" -> "Katakana"
            "kanji" -> "Kanji"
            else -> replaceFirstChar { it.uppercase() }
        }
}
