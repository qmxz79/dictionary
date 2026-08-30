package com.bilingual.dictionary.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

class DatabaseHelper private constructor(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        const val DB_NAME = "dictionary.db"
        private const val DB_VERSION = 1

        private val ASSET_CANDIDATES = listOf(
            "dictionary.bin",
            "dictionary.db.gz",
            "dictionary.db"
        )

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
        if (!file.exists() || file.length() < 1000L) return false
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
        Log.i(TAG, "Unpacking pre-bundled database from assets...")
        val tempFile = File(dbFile.parentFile, "$DB_NAME.tmp")
        var copied = false

        for (assetName in ASSET_CANDIDATES) {
            try {
                if (tempFile.exists()) tempFile.delete()

                context.assets.open(assetName).use { rawIn ->
                    val pushbackIn = PushbackInputStream(rawIn, 2)
                    val header = ByteArray(2)
                    val bytesRead = pushbackIn.read(header)
                    if (bytesRead == 2) {
                        pushbackIn.unread(header)
                    }

                    // Check if file is GZIP compressed (magic number 0x1f8b)
                    val isGzip = (bytesRead == 2 &&
                            header[0] == 0x1f.toByte() &&
                            header[1] == 0x8b.toByte())

                    val inStream: InputStream = if (isGzip) {
                        GZIPInputStream(pushbackIn)
                    } else {
                        BufferedInputStream(pushbackIn)
                    }

                    FileOutputStream(tempFile).use { fileOut ->
                        val buffer = ByteArray(65536)
                        var readLen: Int
                        while (inStream.read(buffer).also { readLen = it } != -1) {
                            fileOut.write(buffer, 0, readLen)
                        }
                        fileOut.flush()
                        fileOut.fd.sync()
                    }
                }

                if (tempFile.exists() && tempFile.length() > 1000L) {
                    if (dbFile.exists()) dbFile.delete()
                    val renamed = tempFile.renameTo(dbFile)
                    Log.i(TAG, "Successfully extracted $assetName -> ${dbFile.absolutePath} (${dbFile.length()} bytes, renamed: $renamed)")
                    copied = true
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load asset candidate $assetName: ${e.message}")
                if (tempFile.exists()) tempFile.delete()
            }
        }

        if (!copied) {
            Log.e(TAG, "CRITICAL: None of the asset database candidates could be unpacked!")
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Fallback schema in case asset unpacking fails
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
