package com.bilingual.dictionary.data.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class AppPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "bilingual_dict_prefs"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_FONT_SIZE_SCALE = "key_font_size_scale"

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val FONT_SMALL = 0
        const val FONT_NORMAL = 1
        const val FONT_LARGE = 2
        const val FONT_XLARGE = 3
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    var fontSizeScale: Int
        get() = prefs.getInt(KEY_FONT_SIZE_SCALE, FONT_NORMAL)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE_SCALE, value).apply()

    fun applyTheme() {
        val mode = when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getDefinitionTextSizeSp(): Float {
        return when (fontSizeScale) {
            FONT_SMALL -> 14f
            FONT_NORMAL -> 16f
            FONT_LARGE -> 18f
            FONT_XLARGE -> 20f
            else -> 16f
        }
    }
}
