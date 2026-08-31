package com.bilingual.dictionary

import android.app.Application
import android.util.Log
import com.bilingual.dictionary.data.db.DatabaseHelper
import com.bilingual.dictionary.data.db.DictionaryDao
import com.bilingual.dictionary.data.repository.DictionaryRepository

class DictionaryApplication : Application() {

    companion object {
        private const val TAG = "DictionaryApplication"
    }

    lateinit var repository: DictionaryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
        }

        try {
            com.bilingual.dictionary.data.pref.AppPreferences(this).applyTheme()
        } catch (e: Exception) {
            Log.w(TAG, "Error applying theme: ${e.message}")
        }

        try {
            val dbHelper = DatabaseHelper.getInstance(this)
            val dao = DictionaryDao(dbHelper)
            repository = DictionaryRepository(dao)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing repository in application", e)
        }
    }
}
