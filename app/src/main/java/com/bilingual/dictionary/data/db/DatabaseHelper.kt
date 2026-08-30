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

    private fun ensureDatabaseExists() {
        val file = dbFile
        if (!file.exists() || file.length() == 0L) {
            file.parentFile?.mkdirs()
            unpackAssetDatabase()
        }
    }

    private fun unpackAssetDatabase() {
        Log.i(TAG, "Unpacking pre-bundled database from assets ($DB_ASSET_GZ)...")
        try {
            context.assets.open(DB_ASSET_GZ).use { assetIn ->
                GZIPInputStream(assetIn).use { gzipIn ->
                    FileOutputStream(dbFile).use { fileOut ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (gzipIn.read(buffer).also { bytesRead = it } != -1) {
                            fileOut.write(buffer, 0, bytesRead)
                        }
                        fileOut.flush()
                    }
                }
            }
            Log.i(TAG, "Database unpacked successfully: ${dbFile.absolutePath} (${dbFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unpack database", e)
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Tables already created in pre-packaged db
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.i(TAG, "Upgrading database from version $oldVersion to $newVersion")
        unpackAssetDatabase()
    }
}
