package com.bilingual.dictionary

import com.bilingual.dictionary.core.LanguageDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDetectorTest {

    @Test
    fun testChineseDetection() {
        assertTrue(LanguageDetector.isChinese("你好"))
        assertTrue(LanguageDetector.isChinese("早安 (Good morning)"))
        assertTrue(LanguageDetector.isChinese("课堂口令"))
        assertFalse(LanguageDetector.isChinese("Good morning"))
        assertFalse(LanguageDetector.isChinese("Selamat pagi"))
    }

    @Test
    fun testEnglishDetection() {
        assertEquals("en", LanguageDetector.detectLatinLanguage("tomorrow"))
        assertEquals("en", LanguageDetector.detectLatinLanguage("One Two Three"))
        assertEquals("en", LanguageDetector.detectLatinLanguage("Be quiet please"))
        assertEquals("en", LanguageDetector.detectLatinLanguage("Look at me"))
        assertEquals("en", LanguageDetector.detectLatinLanguage("internationalization"))
    }

    @Test
    fun testMalayDetection() {
        assertEquals("ms", LanguageDetector.detectLatinLanguage("selamat pagi"))
        assertEquals("ms", LanguageDetector.detectLatinLanguage("terima kasih"))
        assertEquals("ms", LanguageDetector.detectLatinLanguage("kanak-kanak"))
        assertEquals("ms", LanguageDetector.detectLatinLanguage("makanan ini sangat sedap"))
        assertEquals("ms", LanguageDetector.detectLatinLanguage("bagaimana keadaan kamu"))
        assertEquals("ms", LanguageDetector.detectLatinLanguage("perjalanan ke sekolah"))
    }
}
