package com.bilingual.dictionary.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.data.model.FavoriteItem
import com.bilingual.dictionary.data.model.HistoryItem
import com.bilingual.dictionary.data.model.SuggestionItem

class DictionaryDao(private val dbHelper: DatabaseHelper) {

    private val readableDb: SQLiteDatabase
        get() = dbHelper.readableDatabase

    private val writableDb: SQLiteDatabase
        get() = dbHelper.writableDatabase

    /**
     * Search words by exact word (or specific language if given).
     */
    fun searchExact(rawWord: String, lang: String? = null): List<DictionaryEntry> {
        val word = rawWord.lowercase().trim()
        val results = mutableListOf<DictionaryEntry>()

        val query = if (lang != null) {
            "SELECT id, word, display_word, lang, phonetic, pos, definition, example FROM words WHERE word = ? AND lang = ?"
        } else {
            "SELECT id, word, display_word, lang, phonetic, pos, definition, example FROM words WHERE word = ?"
        }
        val args = if (lang != null) arrayOf(word, lang) else arrayOf(word)

        val favoriteWords = getFavoriteKeys()

        readableDb.rawQuery(query, args).use { cursor ->
            while (cursor.moveToNext()) {
                val w = cursor.getString(1)
                val l = cursor.getString(3)
                results.add(
                    DictionaryEntry(
                        id = cursor.getLong(0),
                        word = w,
                        displayWord = cursor.getString(2) ?: w,
                        lang = l,
                        phonetic = cursor.getString(4),
                        pos = cursor.getString(5),
                        definition = cursor.getString(6),
                        example = cursor.getString(7),
                        isFavorite = favoriteWords.contains("$w|$l")
                    )
                )
            }
        }
        return results
    }

    /**
     * Autocomplete suggestions (prefix search).
     */
    fun searchSuggestions(rawPrefix: String, limit: Int = 10): List<SuggestionItem> {
        val prefix = rawPrefix.lowercase().trim()
        if (prefix.isEmpty()) return emptyList()

        val results = mutableListOf<SuggestionItem>()
        val sql = "SELECT word, lang, definition FROM words WHERE word LIKE ? ORDER BY LENGTH(word) ASC LIMIT ?"
        val args = arrayOf("$prefix%", limit.toString())

        readableDb.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val def = cursor.getString(2) ?: ""
                val cleanDef = def.replace("\n", " ").trim()
                results.add(
                    SuggestionItem(
                        word = cursor.getString(0),
                        lang = cursor.getString(1),
                        definition = cleanDef
                    )
                )
            }
        }
        return results
    }

    /**
     * Reverse search from Chinese keyword to target language (EN or MS).
     */
    fun searchReverseChinese(keyword: String, targetLang: String? = null): List<DictionaryEntry> {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty()) return emptyList()

        val results = mutableListOf<DictionaryEntry>()
        val favoriteWords = getFavoriteKeys()

        val sql = if (targetLang != null) {
            """
            SELECT DISTINCT w.id, w.word, w.display_word, w.lang, w.phonetic, w.pos, w.definition, w.example
            FROM reverse_index r
            JOIN words w ON r.target_word = w.display_word AND r.target_lang = w.lang
            WHERE (r.zh_keyword = ? OR r.definition LIKE ?) AND r.target_lang = ?
            LIMIT 25
            """.trimIndent()
        } else {
            """
            SELECT DISTINCT w.id, w.word, w.display_word, w.lang, w.phonetic, w.pos, w.definition, w.example
            FROM reverse_index r
            JOIN words w ON r.target_word = w.display_word AND r.target_lang = w.lang
            WHERE r.zh_keyword = ? OR r.definition LIKE ?
            LIMIT 25
            """.trimIndent()
        }

        val likeArg = "%$cleanKeyword%"
        val args = if (targetLang != null) arrayOf(cleanKeyword, likeArg, targetLang) else arrayOf(cleanKeyword, likeArg)

        readableDb.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val w = cursor.getString(1)
                val l = cursor.getString(3)
                results.add(
                    DictionaryEntry(
                        id = cursor.getLong(0),
                        word = w,
                        displayWord = cursor.getString(2) ?: w,
                        lang = l,
                        phonetic = cursor.getString(4),
                        pos = cursor.getString(5),
                        definition = cursor.getString(6),
                        example = cursor.getString(7),
                        isFavorite = favoriteWords.contains("$w|$l")
                    )
                )
            }
        }
        return results
    }

    // ================= FAVORITES =================
    private fun getFavoriteKeys(): Set<String> {
        val set = mutableSetOf<String>()
        readableDb.rawQuery("SELECT word, lang FROM user_favorites", null).use { cursor ->
            while (cursor.moveToNext()) {
                set.add("${cursor.getString(0)}|${cursor.getString(1)}")
            }
        }
        return set
    }

    fun getAllFavorites(): List<FavoriteItem> {
        val list = mutableListOf<FavoriteItem>()
        readableDb.rawQuery(
            "SELECT id, word, lang, phonetic, pos, definition, timestamp FROM user_favorites ORDER BY timestamp DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    FavoriteItem(
                        id = cursor.getLong(0),
                        word = cursor.getString(1),
                        lang = cursor.getString(2),
                        phonetic = cursor.getString(3),
                        pos = cursor.getString(4),
                        definition = cursor.getString(5),
                        timestamp = cursor.getLong(6)
                    )
                )
            }
        }
        return list
    }

    fun addFavorite(entry: DictionaryEntry) {
        val values = ContentValues().apply {
            put("word", entry.word)
            put("lang", entry.lang)
            put("phonetic", entry.phonetic)
            put("pos", entry.pos)
            put("definition", entry.definition)
            put("timestamp", System.currentTimeMillis())
        }
        writableDb.insertWithOnConflict("user_favorites", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeFavorite(word: String, lang: String) {
        writableDb.delete("user_favorites", "word = ? AND lang = ?", arrayOf(word, lang))
    }

    fun isFavorite(word: String, lang: String): Boolean {
        readableDb.rawQuery(
            "SELECT 1 FROM user_favorites WHERE word = ? AND lang = ? LIMIT 1",
            arrayOf(word, lang)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    // ================= HISTORY =================
    fun addHistory(query: String, lang: String) {
        if (query.isBlank()) return
        val cleanQuery = query.trim()
        writableDb.delete("user_history", "query = ?", arrayOf(cleanQuery))
        val values = ContentValues().apply {
            put("query", cleanQuery)
            put("lang", lang)
            put("timestamp", System.currentTimeMillis())
        }
        writableDb.insert("user_history", null, values)
    }

    fun getHistory(limit: Int = 50): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        readableDb.rawQuery(
            "SELECT id, query, lang, timestamp FROM user_history ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    HistoryItem(
                        id = cursor.getLong(0),
                        query = cursor.getString(1),
                        lang = cursor.getString(2),
                        timestamp = cursor.getLong(3)
                    )
                )
            }
        }
        return list
    }

    fun deleteHistoryItem(id: Long) {
        writableDb.delete("user_history", "id = ?", arrayOf(id.toString()))
    }

    fun clearAllHistory() {
        writableDb.delete("user_history", null, null)
    }

    // ================= ONLINE CACHE =================
    fun getCachedOnlineResult(query: String, sourceLang: String, targetLang: String): String? {
        readableDb.rawQuery(
            "SELECT result_text FROM online_cache WHERE query = ? AND source_lang = ? AND target_lang = ? LIMIT 1",
            arrayOf(query.lowercase().trim(), sourceLang, targetLang)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    fun saveCachedOnlineResult(query: String, sourceLang: String, targetLang: String, resultText: String) {
        val values = ContentValues().apply {
            put("query", query.lowercase().trim())
            put("source_lang", sourceLang)
            put("target_lang", targetLang)
            put("result_text", resultText)
            put("timestamp", System.currentTimeMillis())
        }
        writableDb.insertWithOnConflict("online_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
