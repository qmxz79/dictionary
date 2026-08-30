package com.bilingual.dictionary.core

/**
 * High performance Morphological Stemmer for Bahasa Melayu.
 * Strips affixes (imbuhan) and resolves phonetic root mutations.
 */
object MalayStemmer {

    data class StemCandidate(
        val root: String,
        val prefix: String? = null,
        val suffix: String? = null,
        val explanation: String
    )

    private val INFLECTIONAL_SUFFIXES = listOf("lah", "kah", "nya", "ku", "mu")
    private val DERIVATIONAL_SUFFIXES = listOf("kan", "an", "i")

    /**
     * Generates a ranked list of candidate root words for a given Malay word.
     */
    fun stem(rawWord: String): List<StemCandidate> {
        val word = rawWord.lowercase().trim()
        if (word.length <= 3) return emptyList()

        val results = mutableListOf<StemCandidate>()
        val seenRoots = mutableSetOf<String>()

        fun addCandidate(root: String, prefix: String?, suffix: String?) {
            if (root.length >= 3 && root != word && !seenRoots.contains(root)) {
                seenRoots.add(root)
                val parts = mutableListOf<String>()
                if (!prefix.isNullOrEmpty()) parts.add("前缀: $prefix")
                if (!suffix.isNullOrEmpty()) parts.add("后缀: -$suffix")
                val desc = if (parts.isNotEmpty()) "💡 词根: $root (${parts.joinToString(", ")})" else "💡 词根: $root"
                results.add(StemCandidate(root, prefix, suffix, desc))
            }
        }

        // 1. Check direct suffix stripping
        for (suf in INFLECTIONAL_SUFFIXES + DERIVATIONAL_SUFFIXES) {
            if (word.endsWith(suf) && word.length > suf.length + 2) {
                val base = word.substring(0, word.length - suf.length)
                addCandidate(base, null, suf)
                // Check if base has prefix
                stripPrefixes(base).forEach {
                    addCandidate(it.root, it.prefix, suf)
                }
            }
        }

        // 2. Check direct prefix stripping
        stripPrefixes(word).forEach {
            addCandidate(it.root, it.prefix, null)
            // Check if root has suffix
            for (suf in DERIVATIONAL_SUFFIXES) {
                if (it.root.endsWith(suf) && it.root.length > suf.length + 2) {
                    val subRoot = it.root.substring(0, it.root.length - suf.length)
                    addCandidate(subRoot, it.prefix, suf)
                }
            }
        }

        return results
    }

    private fun stripPrefixes(word: String): List<StemCandidate> {
        val list = mutableListOf<StemCandidate>()

        // Rule: be- / ber- / bel-
        if (word.startsWith("ber") && word.length > 5) {
            list.add(StemCandidate(word.substring(3), "ber", null, ""))
        } else if (word.startsWith("belajar")) {
            list.add(StemCandidate("ajar", "bel", null, ""))
        } else if (word.startsWith("be") && word.length > 4) {
            list.add(StemCandidate(word.substring(2), "be", null, ""))
        }

        // Rule: ter-
        if (word.startsWith("ter") && word.length > 5) {
            list.add(StemCandidate(word.substring(3), "ter", null, ""))
        }

        // Rule: di- / ke- / se-
        for (p in listOf("di", "ke", "se")) {
            if (word.startsWith(p) && word.length > p.length + 2) {
                list.add(StemCandidate(word.substring(p.length), p, null, ""))
            }
        }

        // Rule: me- and pe- series (with nasal substitutions)
        list.addAll(stripNasalPrefix(word, "me", "pe"))

        return list
    }

    private fun stripNasalPrefix(word: String, verbPrefix: String, nounPrefix: String): List<StemCandidate> {
        val list = mutableListOf<StemCandidate>()

        for (prefixBase in listOf(verbPrefix, nounPrefix)) {
            // meny- / peny- -> s... (menyapu -> sapu) or vowel
            val ny = "${prefixBase}ny"
            if (word.startsWith(ny) && word.length > ny.length + 2) {
                val rest = word.substring(ny.length)
                list.add(StemCandidate("s$rest", ny, null, ""))
                list.add(StemCandidate(rest, ny, null, ""))
            }

            // meng- / peng- -> k... (mengira -> kira) or g... / vowel (mengajar -> ajar)
            val ng = "${prefixBase}ng"
            if (word.startsWith(ng) && word.length > ng.length + 2) {
                val rest = word.substring(ng.length)
                list.add(StemCandidate("k$rest", ng, null, ""))
                list.add(StemCandidate(rest, ng, null, ""))
            }

            // mem- / pem- -> p... (memilih -> pilih) or b... (membeli -> beli)
            val m = "${prefixBase}m"
            if (word.startsWith(m) && word.length > m.length + 2) {
                val rest = word.substring(m.length)
                list.add(StemCandidate("p$rest", m, null, ""))
                list.add(StemCandidate(rest, m, null, ""))
            }

            // men- / pen- -> t... (menulis -> tulis) or d... (mendengar -> dengar)
            val n = "${prefixBase}n"
            if (word.startsWith(n) && word.length > n.length + 2) {
                val rest = word.substring(n.length)
                list.add(StemCandidate("t$rest", n, null, ""))
                list.add(StemCandidate(rest, n, null, ""))
            }

            // Simple me- / pe- / per-
            val r = "${prefixBase}r"
            if (word.startsWith(r) && word.length > r.length + 2) {
                list.add(StemCandidate(word.substring(r.length), r, null, ""))
            }
            if (word.startsWith(prefixBase) && word.length > prefixBase.length + 2) {
                list.add(StemCandidate(word.substring(prefixBase.length), prefixBase, null, ""))
            }
        }

        return list
    }
}
