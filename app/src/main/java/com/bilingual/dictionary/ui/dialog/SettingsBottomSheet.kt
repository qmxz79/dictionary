package com.bilingual.dictionary.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bilingual.dictionary.R
import com.bilingual.dictionary.data.pref.AppPreferences
import com.bilingual.dictionary.databinding.DialogSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsBottomSheet(
    private val onSettingsChanged: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var appPrefs: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSettingsBinding.inflate(inflater, container, false)
        appPrefs = AppPreferences(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Theme selection
        when (appPrefs.themeMode) {
            AppPreferences.THEME_LIGHT -> binding.chipThemeLight.isChecked = true
            AppPreferences.THEME_DARK -> binding.chipThemeDark.isChecked = true
            else -> binding.chipThemeSystem.isChecked = true
        }

        binding.chipGroupTheme.setOnCheckedStateChangeListener { _, checkedIds ->
            val newTheme = when {
                checkedIds.contains(binding.chipThemeLight.id) -> AppPreferences.THEME_LIGHT
                checkedIds.contains(binding.chipThemeDark.id) -> AppPreferences.THEME_DARK
                else -> AppPreferences.THEME_SYSTEM
            }
            if (newTheme != appPrefs.themeMode) {
                appPrefs.themeMode = newTheme
                appPrefs.applyTheme()
                onSettingsChanged()
            }
        }

        // Setup Font Size selection
        when (appPrefs.fontSizeScale) {
            AppPreferences.FONT_SMALL -> binding.chipFontSmall.isChecked = true
            AppPreferences.FONT_LARGE -> binding.chipFontLarge.isChecked = true
            AppPreferences.FONT_XLARGE -> binding.chipFontXLarge.isChecked = true
            else -> binding.chipFontNormal.isChecked = true
        }

        binding.chipGroupFontSize.setOnCheckedStateChangeListener { _, checkedIds ->
            val newScale = when {
                checkedIds.contains(binding.chipFontSmall.id) -> AppPreferences.FONT_SMALL
                checkedIds.contains(binding.chipFontLarge.id) -> AppPreferences.FONT_LARGE
                checkedIds.contains(binding.chipFontXLarge.id) -> AppPreferences.FONT_XLARGE
                else -> AppPreferences.FONT_NORMAL
            }
            if (newScale != appPrefs.fontSizeScale) {
                appPrefs.fontSizeScale = newScale
                onSettingsChanged()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsBottomSheet"
        fun newInstance(onSettingsChanged: () -> Unit) = SettingsBottomSheet(onSettingsChanged)
    }
}
