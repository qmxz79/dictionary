package com.bilingual.dictionary

import com.bilingual.dictionary.core.SpellCorrector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellCorrectorTest {

    @Test
    fun testLevenshteinDistance() {
        assertEquals(0, SpellCorrector.levenshteinDistance("butter", "butter"))
        assertEquals(1, SpellCorrector.levenshteinDistance("buter", "butter"))
        assertEquals(1, SpellCorrector.levenshteinDistance("definiton", "definition"))
        assertEquals(2, SpellCorrector.levenshteinDistance("wrold", "world"))
        assertEquals(1, SpellCorrector.levenshteinDistance("selmat", "selamat"))
    }

    @Test
    fun testFindSuggestionsEnglish() {
        val candidates = listOf("butter", "batter", "better", "water", "apple", "bottle", "bitter")
        val suggestions = SpellCorrector.findSuggestions("buter", candidates)

        assertTrue(suggestions.isNotEmpty())
        assertEquals("butter", suggestions[0])
    }

    @Test
    fun testFindSuggestionsMalay() {
        val candidates = listOf("selamat", "selatan", "selada", "makan", "minum", "belajar")
        val suggestions = SpellCorrector.findSuggestions("selmat", candidates)

        assertTrue(suggestions.isNotEmpty())
        assertEquals("selamat", suggestions[0])
    }
}
