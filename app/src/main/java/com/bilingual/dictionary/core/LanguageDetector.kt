package com.bilingual.dictionary.core

import java.util.regex.Pattern

/**
 * Intelligent Language Detector for distinguishing between:
 * - Chinese (zh)
 * - Bahasa Melayu (ms)
 * - English (en)
 */
object LanguageDetector {

    private val CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]")

    // Common Malay function words, pronouns, particles and frequent vocabulary
    private val MALAY_KEYWORDS = setOf(
        "dan", "yang", "di", "ke", "dari", "daripada", "ini", "itu", "untuk", "pada", "kepada",
        "adalah", "ialah", "dengan", "tidak", "tak", "bukan", "ada", "tiada", "saya", "aku",
        "kamu", "awak", "kau", "dia", "beliau", "mereka", "kita", "kami", "ia", "akan",
        "telah", "sudah", "pernah", "belum", "sedang", "masih", "boleh", "bisa", "dapat",
        "harus", "mesti", "perlu", "patut", "tetapi", "tapi", "namun", "juga", "pun", "atau",
        "jika", "kalau", "jikalau", "sekiranya", "kerana", "sebab", "oleh", "supaya", "agar",
        "seperti", "bagai", "bak", "umpama", "dalam", "atas", "bawah", "depan", "hadapan",
        "belakang", "luar", "antara", "sini", "situ", "sana", "apa", "siapa", "bila", "mana",
        "bagaimana", "mengapa", "kenapa", "berapa", "terima", "kasih", "selamat", "pagi",
        "malam", "petang", "tengah", "hari", "makan", "minum", "tidur", "pergi", "balik",
        "jalan", "lari", "datang", "lihat", "tengok", "dengar", "tahu", "rasa", "fikir",
        "buat", "kerja", "buku", "rumah", "orang", "kawan", "rakan", "sekolah", "guru",
        "cikgu", "murid", "pelajar", "cantik", "besar", "kecil", "panjang", "pendek",
        "tinggi", "rendah", "baik", "jahat", "baru", "lama", "banyak", "sedikit", "sikit",
        "sangat", "amat", "paling", "sungguh", "satu", "dua", "tiga", "empat", "lima",
        "enam", "tujuh", "lapan", "sembilan", "sepuluh", "pertama", "kedua", "ketiga",
        "kelas", "bilik", "pintu", "tingkap", "meja", "kerusi", "papan", "tulis", "baca",
        "ajar", "belajar", "soalan", "jawapan", "soal", "jawab", "tolong", "bantu",
        "maaf", "sila", "jemput", "tanya", "cakap", "kata", "ucap", "cerita"
    )

    // Common English words, pronouns, auxiliary verbs and frequent vocabulary
    private val ENGLISH_KEYWORDS = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not",
        "on", "with", "he", "as", "you", "do", "at", "this", "but", "his", "by", "from",
        "they", "we", "say", "her", "she", "or", "an", "will", "my", "one", "all", "would",
        "there", "their", "what", "so", "up", "out", "if", "about", "who", "get", "which",
        "go", "me", "when", "make", "can", "like", "time", "no", "just", "him", "know",
        "take", "people", "into", "year", "your", "good", "some", "could", "them", "see",
        "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
        "also", "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "is", "are", "was", "were", "been", "has", "had", "does", "did", "am", "tomorrow",
        "yesterday", "today", "morning", "evening", "afternoon", "night", "school", "teacher",
        "student", "quiet", "please", "listen", "write", "read", "book", "pencil", "room",
        "door", "window", "table", "chair", "blackboard", "three", "four", "five", "six"
    )

    private val MALAY_PREFIXES = listOf("ber", "ter", "per", "mem", "men", "meng", "meny", "me", "pem", "pen", "peng", "peny", "pe", "se", "di", "ke")
    private val MALAY_SUFFIXES = listOf("kan", "an", "nya", "lah", "kah", "ku", "mu")

    private val ENGLISH_SUFFIXES = listOf("tion", "sion", "ment", "ness", "able", "ible", "ful", "less", "ing", "ed", "ly", "ize", "ise", "ism", "ist", "ous", "ious", "est", "ive")

    /**
     * Returns true if string contains Chinese characters.
     */
    fun isChinese(text: String): Boolean {
        return CHINESE_PATTERN.matcher(text).find()
    }

    /**
     * Determines whether a Latin-based query is Malay ("ms") or English ("en").
     */
    fun detectLatinLanguage(text: String): String {
        val clean = text.lowercase().trim()
        if (clean.isEmpty()) return "en"

        val words = clean.split(Regex("[^a-zA-Z\\-]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "en"

        var malayScore = 0
        var englishScore = 0

        for (w in words) {
            // 1. Direct dictionary / keyword lookup
            if (MALAY_KEYWORDS.contains(w)) {
                malayScore += 4
            }
            if (ENGLISH_KEYWORDS.contains(w)) {
                englishScore += 4
            }

            // 2. Reduplication check (e.g. kanak-kanak, tiba-tiba, kata-kata)
            if (w.contains("-")) {
                val parts = w.split("-")
                if (parts.size == 2 && parts[0] == parts[1] && parts[0].length >= 3) {
                    malayScore += 5
                }
            }

            // 3. Malay morphological affixes
            for (pre in MALAY_PREFIXES) {
                if (w.startsWith(pre) && w.length >= pre.length + 3) {
                    malayScore += 2
                    break
                }
            }
            for (suf in MALAY_SUFFIXES) {
                if (w.endsWith(suf) && w.length >= suf.length + 3) {
                    malayScore += 2
                    break
                }
            }

            // 4. English morphological suffixes
            for (suf in ENGLISH_SUFFIXES) {
                if (w.endsWith(suf) && w.length >= suf.length + 3) {
                    englishScore += 2
                    break
                }
            }

            // 5. Letter cluster heuristics
            // Malay frequent clusters: ng, ny, sy, kh
            if (w.contains("ny") || w.contains("sy") || w.contains("kh")) {
                malayScore += 2
            }
            // English frequent clusters: th, wh, ck, ea, igh, ght, ph, qu
            if (w.contains("th") || w.contains("wh") || w.contains("ck") || w.contains("igh") || w.contains("ght") || w.contains("ph")) {
                englishScore += 2
            }

            // 6. MalayStemmer check
            if (MalayStemmer.stem(w).isNotEmpty()) {
                malayScore += 1
            }
        }

        return if (malayScore > englishScore) "ms" else "en"
    }
}
