package com.bilingual.dictionary.core

import kotlin.math.min

object SpellCorrector {

    /**
     * Calculates the standard Levenshtein distance between two strings.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1].lowercaseChar() == s2[j - 1].lowercaseChar()) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Computes fuzzy spelling candidates for a misspelled query from a candidate list.
     * @param query Misspelled word entered by the user
     * @param candidates Pre-filtered word list (e.g., matching initial letter or similar length)
     * @param maxResults Number of top suggestions to return (default 3)
     * @param maxDistance Maximum allowed edit distance (default 2 for short/medium words)
     */
    fun findSuggestions(
        query: String,
        candidates: List<String>,
        maxResults: Int = 3,
        maxDistance: Int = 2
    ): List<String> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()

        val allowedDist = if (q.length <= 3) 1 else maxDistance

        return candidates
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() != q }
            .distinctBy { it.lowercase() }
            .map { candidate ->
                val dist = levenshteinDistance(q, candidate.lowercase())
                val prefixBonus = if (candidate.lowercase().startsWith(q.take(2))) -0.5 else 0.0
                Pair(candidate, dist.toDouble() + prefixBonus)
            }
            .filter { it.second <= allowedDist }
            .sortedBy { it.second }
            .take(maxResults)
            .map { it.first }
            .toList()
    }
}
