package com.bilingual.dictionary

import android.app.Application
import com.bilingual.dictionary.data.db.DatabaseHelper
import com.bilingual.dictionary.data.db.DictionaryDao
import com.bilingual.dictionary.data.repository.DictionaryRepository

class DictionaryApplication : Application() {

    lateinit var repository: DictionaryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val dbHelper = DatabaseHelper.getInstance(this)
        val dao = DictionaryDao(dbHelper)
        repository = DictionaryRepository(dao)
    }
}
