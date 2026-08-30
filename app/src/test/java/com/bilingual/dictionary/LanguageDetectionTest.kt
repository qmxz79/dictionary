package com.bilingual.dictionary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class LanguageDetectionTest {

    private val chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]")

    private fun isChinese(text: String): Boolean {
        return chinesePattern.matcher(text).find()
    }

    @Test
    fun testChineseDetection() {
        assertTrue(isChinese("你好"))
        assertTrue(isChinese("水"))
        assertTrue(isChinese("早安，马来西亚"))
        assertTrue(isChinese("hello你好"))
    }

    @Test
    fun testLatinDetection() {
        assertFalse(isChinese("hello"))
        assertFalse(isChinese("selamat pagi"))
        assertFalse(isChinese("air"))
        assertFalse(isChinese("12345"))
    }
}
