package com.bilingual.dictionary

import com.bilingual.dictionary.core.MalayStemmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MalayStemmerTest {

    @Test
    fun testNasalPrefixes() {
        // meny- -> s... (menyapu -> sapu)
        val candSapu = MalayStemmer.stem("menyapu")
        assertTrue("Expected sapu root in candidates", candSapu.any { it.root == "sapu" })

        // mem- -> p... (memilih -> pilih)
        val candPilih = MalayStemmer.stem("memilih")
        assertTrue("Expected pilih root in candidates", candPilih.any { it.root == "pilih" })

        // men- -> t... (menulis -> tulis)
        val candTulis = MalayStemmer.stem("menulis")
        assertTrue("Expected tulis root in candidates", candTulis.any { it.root == "tulis" })

        // meng- -> k... (mengira -> kira) or vowel (mengajar -> ajar)
        val candKira = MalayStemmer.stem("mengira")
        assertTrue("Expected kira root in candidates", candKira.any { it.root == "kira" })

        val candAjar = MalayStemmer.stem("mengajar")
        assertTrue("Expected ajar root in candidates", candAjar.any { it.root == "ajar" })
    }

    @Test
    fun testSuffixAndConfixes() {
        // makanan -> makan + -an
        val candMakan = MalayStemmer.stem("makanan")
        assertTrue("Expected makan root in candidates", candMakan.any { it.root == "makan" })

        // perjalanan -> jalan + per-...-an
        val candJalan = MalayStemmer.stem("perjalanan")
        assertTrue("Expected jalan root in candidates", candJalan.any { it.root == "jalan" })

        // membaca -> baca + mem-
        val candBaca = MalayStemmer.stem("membaca")
        assertTrue("Expected baca root in candidates", candBaca.any { it.root == "baca" })
    }

    @Test
    fun testShortWordsIgnored() {
        val cand = MalayStemmer.stem("air")
        assertTrue("Short words should produce no stem artifacts", cand.isEmpty())
    }
}
