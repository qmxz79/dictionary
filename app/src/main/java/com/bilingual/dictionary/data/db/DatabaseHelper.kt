package com.bilingual.dictionary.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

class DatabaseHelper private constructor(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        const val DB_NAME = "dictionary.db"
        const val DB_ASSET_GZ = "dictionary.db.gz"
        private const val DB_VERSION = 1

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private val dbFile: File
        get() = context.getDatabasePath(DB_NAME)

    init {
        ensureDatabaseExists()
    }

    @Synchronized
    fun ensureDatabaseExists() {
        val file = dbFile
        if (!file.exists() || file.length() < 1000L || !isDatabaseValid(file)) {
            file.parentFile?.mkdirs()
            unpackAssetDatabase()
        }
    }

    private fun isDatabaseValid(file: File): Boolean {
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='words'", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getInt(0) > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Database validation check failed: ${e.message}")
            false
        }
    }

    private fun unpackAssetDatabase() {
        Log.i(TAG, "Unpacking pre-bundled database from assets ($DB_ASSET_GZ)...")
        val tempFile = File(dbFile.parentFile, "$DB_NAME.tmp")
        try {
            if (tempFile.exists()) tempFile.delete()

            context.assets.open(DB_ASSET_GZ).use { assetIn ->
                GZIPInputStream(assetIn).use { gzipIn ->
                    FileOutputStream(tempFile).use { fileOut ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (gzipIn.read(buffer).also { bytesRead = it } != -1) {
                            fileOut.write(buffer, 0, bytesRead)
                        }
                        fileOut.flush()
                        fileOut.fd.sync()
                    }
                }
            }

            if (dbFile.exists()) dbFile.delete()
            val success = tempFile.renameTo(dbFile)
            Log.i(TAG, "Database unpacked successfully (renamed: $success): ${dbFile.absolutePath} (${dbFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unpack database from asset", e)
            if (tempFile.exists()) tempFile.delete()
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Create basic schema in case asset unpacking had issues
        db?.executescriptSafely("""
            CREATE TABLE IF NOT EXISTS words (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                display_word TEXT NOT NULL,
                lang TEXT NOT NULL,
                phonetic TEXT,
                pos TEXT,
                definition TEXT NOT NULL,
                example TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_words_word ON words(word);
            CREATE TABLE IF NOT EXISTS reverse_index (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                zh_keyword TEXT NOT NULL,
                target_word TEXT NOT NULL,
                target_lang TEXT NOT NULL,
                definition TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS user_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL,
                lang TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS user_favorites (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                lang TEXT NOT NULL,
                phonetic TEXT,
                pos TEXT,
                definition TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS online_cache (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL,
                source_lang TEXT NOT NULL,
                target_lang TEXT NOT NULL,
                result_text TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            );
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Upgrade handler
    }

    private fun SQLiteDatabase.executescriptSafely(sql: String) {
        val statements = sql.split(";")
        for (st in statements) {
            val trimmed = st.trim()
            if (trimmed.isNotEmpty()) {
                try {
                    execSQL(trimmed)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing SQL fallback: $trimmed", e)
                }
            }
        }
    }
}
