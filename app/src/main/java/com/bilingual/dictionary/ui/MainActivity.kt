package com.bilingual.dictionary.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bilingual.dictionary.DictionaryApplication
import com.bilingual.dictionary.R
import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.data.model.SearchMode
import com.bilingual.dictionary.data.pref.AppPreferences
import com.bilingual.dictionary.data.repository.DictionaryRepository
import com.bilingual.dictionary.databinding.ActivityMainBinding
import com.bilingual.dictionary.ui.adapter.FavoriteAdapter
import com.bilingual.dictionary.ui.adapter.HistoryAdapter
import com.bilingual.dictionary.ui.adapter.SuggestionAdapter
import com.bilingual.dictionary.ui.adapter.WordCardAdapter
import com.bilingual.dictionary.ui.dialog.SettingsBottomSheet
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DictionaryRepository
    private lateinit var appPrefs: AppPreferences
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private lateinit var wordAdapter: WordCardAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var favoriteAdapter: FavoriteAdapter

    private var currentMode = SearchMode.AUTO_DETECT
    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var lastHandledClip: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            appPrefs = AppPreferences(this)
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            repository = (application as DictionaryApplication).repository
            initTts()

            setupRecyclerViews()
            setupSearchInput()
            setupModeChips()
            setupBottomNavigation()
            setupSettingsButton()
            setupClipboardBanner()

            handleIncomingIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboard()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.w(TAG, "TTS init error: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        try {
            if (Intent.ACTION_PROCESS_TEXT == intent.action && intent.type == "text/plain") {
                val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!selectedText.isNullOrBlank()) {
                    binding.etSearch.setText(selectedText)
                    binding.etSearch.setSelection(selectedText.length)
                    performSearch(selectedText)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling intent", e)
        }
    }

    private fun setupRecyclerViews() {
        // 1. Word Card Adapter with dynamic font size from AppPreferences
        wordAdapter = WordCardAdapter(
            onSpeakClick = { entry -> speakWord(entry) },
            onFavoriteClick = { entry, pos -> toggleFavorite(entry, pos) },
            onCopyClick = { entry -> copyDefinition(entry) },
            fontSizeSp = appPrefs.getDefinitionTextSizeSp()
        )
        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wordAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        hideSuggestions()
                        hideKeyboard()
                    }
                }
            })
        }

        // 2. Suggestion Adapter
        suggestionAdapter = SuggestionAdapter { suggestion ->
            binding.etSearch.setText(suggestion.word)
            binding.etSearch.setSelection(suggestion.word.length)
            hideSuggestions()
            hideKeyboard()
            performSearch(suggestion.word)
        }
        binding.rvSuggestions.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = suggestionAdapter
        }

        // 3. History Adapter
        historyAdapter = HistoryAdapter(
            onItemClick = { historyItem ->
                binding.bottomNav.selectedItemId = R.id.nav_search
                binding.etSearch.setText(historyItem.query)
                binding.etSearch.setSelection(historyItem.query.length)
                hideSuggestions()
                hideKeyboard()
                performSearch(historyItem.query)
            },
            onDeleteClick = { historyItem ->
                lifecycleScope.launch {
                    repository.deleteHistory(historyItem.id)
                    loadHistory()
                }
            }
        )
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }

        // 4. Favorite Adapter
        favoriteAdapter = FavoriteAdapter(
            onItemClick = { favItem ->
                binding.bottomNav.selectedItemId = R.id.nav_search
                binding.etSearch.setText(favItem.word)
                binding.etSearch.setSelection(favItem.word.length)
                hideSuggestions()
                hideKeyboard()
                performSearch(favItem.word)
            },
            onRemoveClick = { favItem ->
                lifecycleScope.launch {
                    repository.removeFavorite(favItem.word, favItem.lang)
                    loadFavorites()
                    wordAdapter.currentList.find { it.word == favItem.word && it.lang == favItem.lang }?.let {
                        it.isFavorite = false
                        wordAdapter.notifyDataSetChanged()
                    }
                }
            }
        )
        binding.rvFavorites.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = favoriteAdapter
        }
    }

    private fun setupSearchInput() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClear.isVisible = query.isNotEmpty()

                if (query.isNotEmpty() && !repository.isChinese(query)) {
                    suggestJob?.cancel()
                    suggestJob = lifecycleScope.launch {
                        delay(120)
                        try {
                            val suggestions = repository.getSuggestions(query)
                            if (suggestions.isNotEmpty() && binding.panelSearch.isVisible && binding.etSearch.hasFocus()) {
                                suggestionAdapter.submitList(suggestions)
                                binding.cardSuggestions.visibility = View.VISIBLE
                            } else {
                                hideSuggestions()
                            }
                        } catch (e: Exception) {
                            hideSuggestions()
                        }
                    }
                } else {
                    hideSuggestions()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideSuggestions()
                    hideKeyboard()
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }

        binding.btnClear.setOnClickListener {
            suggestJob?.cancel()
            binding.etSearch.text.clear()
            wordAdapter.submitList(emptyList())
            binding.cardSpellCheck.visibility = View.GONE
            binding.layoutEmptySearch.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = "输入单词即可秒级离线查词"
            hideSuggestions()
        }
    }

    private fun setupModeChips() {
        binding.chipGroupMode.setOnCheckedStateChangeListener { _, checkedIds ->
            currentMode = when {
                checkedIds.contains(binding.chipZhToEn.id) -> SearchMode.ZH_TO_EN
                checkedIds.contains(binding.chipZhToMs.id) -> SearchMode.ZH_TO_MS
                else -> {
                    if (checkedIds.isEmpty()) binding.chipAuto.isChecked = true
                    SearchMode.AUTO_DETECT
                }
            }

            val query = binding.etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                hideSuggestions()
                hideKeyboard()
                performSearch(query)
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            hideSuggestions()
            hideKeyboard()
            when (item.itemId) {
                R.id.nav_search -> {
                    binding.panelSearch.visibility = View.VISIBLE
                    binding.panelFavorites.visibility = View.GONE
                    binding.panelHistory.visibility = View.GONE
                    binding.headerContainer.visibility = View.VISIBLE
                    true
                }
                R.id.nav_favorites -> {
                    binding.panelSearch.visibility = View.GONE
                    binding.panelFavorites.visibility = View.VISIBLE
                    binding.panelHistory.visibility = View.GONE
                    binding.headerContainer.visibility = View.GONE
                    loadFavorites()
                    true
                }
                R.id.nav_history -> {
                    binding.panelSearch.visibility = View.GONE
                    binding.panelFavorites.visibility = View.GONE
                    binding.panelHistory.visibility = View.VISIBLE
                    binding.headerContainer.visibility = View.GONE
                    loadHistory()
                    true
                }
                else -> false
            }
        }

        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                repository.clearHistory()
                loadHistory()
                Toast.makeText(this@MainActivity, "历史记录已清空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            hideSuggestions()
            hideKeyboard()
            val sheet = SettingsBottomSheet.newInstance {
                // Refresh font size
                wordAdapter.fontSizeSp = appPrefs.getDefinitionTextSizeSp()
                wordAdapter.notifyDataSetChanged()
            }
            sheet.show(supportFragmentManager, SettingsBottomSheet.TAG)
        }
    }

    private fun setupClipboardBanner() {
        binding.btnCloseClipboard.setOnClickListener {
            binding.cardClipboard.visibility = View.GONE
        }
    }

    private fun checkClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (!clipboard.hasPrimaryClip()) return

            val clipItem = clipboard.primaryClip?.getItemAt(0)
            val text = clipItem?.text?.toString()?.trim() ?: ""

            if (text.isNotEmpty() && text.length in 1..45 && text != lastHandledClip && text != binding.etSearch.text.toString().trim()) {
                binding.tvClipboardSnippet.text = "检测到复制: \"$text\""
                binding.cardClipboard.visibility = View.VISIBLE

                binding.btnLookupClipboard.setOnClickListener {
                    lastHandledClip = text
                    binding.cardClipboard.visibility = View.GONE
                    binding.bottomNav.selectedItemId = R.id.nav_search
                    binding.etSearch.setText(text)
                    binding.etSearch.setSelection(text.length)
                    performSearch(text)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkClipboard error: ${e.message}")
        }
    }

    private fun performSearch(query: String) {
        suggestJob?.cancel()
        hideSuggestions()
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.layoutEmptySearch.visibility = View.GONE
                binding.cardSpellCheck.visibility = View.GONE

                val results = repository.lookup(query, currentMode)
                binding.progressBar.visibility = View.GONE

                if (results.isNotEmpty()) {
                    wordAdapter.submitList(results)
                    binding.layoutEmptySearch.visibility = View.GONE
                    binding.cardSpellCheck.visibility = View.GONE
                } else {
                    wordAdapter.submitList(emptyList())
                    binding.layoutEmptySearch.visibility = View.VISIBLE
                    binding.tvEmptyTitle.text = getString(R.string.no_results)

                    // Attempt fuzzy spell check correction
                    val corrections = repository.getSpellCorrection(query)
                    if (corrections.isNotEmpty()) {
                        showSpellCorrections(corrections)
                    } else {
                        binding.cardSpellCheck.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search execution error", e)
                binding.progressBar.visibility = View.GONE
                binding.layoutEmptySearch.visibility = View.VISIBLE
                binding.tvEmptyTitle.text = "查询失败，请重试"
            }
        }
    }

    private fun showSpellCorrections(corrections: List<String>) {
        binding.chipGroupSpell.removeAllViews()
        for (cand in corrections) {
            val chip = Chip(this).apply {
                text = cand
                isCheckable = false
                isClickable = true
                setOnClickListener {
                    binding.etSearch.setText(cand)
                    binding.etSearch.setSelection(cand.length)
                    binding.cardSpellCheck.visibility = View.GONE
                    performSearch(cand)
                }
            }
            binding.chipGroupSpell.addView(chip)
        }
        binding.cardSpellCheck.visibility = View.VISIBLE
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            try {
                val list = repository.getFavorites()
                favoriteAdapter.submitList(list)
                binding.tvEmptyFavorites.isVisible = list.isEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "loadFavorites error", e)
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val list = repository.getHistory()
                historyAdapter.submitList(list)
                binding.tvEmptyHistory.isVisible = list.isEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "loadHistory error", e)
            }
        }
    }

    private fun toggleFavorite(entry: DictionaryEntry, pos: Int) {
        lifecycleScope.launch {
            try {
                val newState = repository.toggleFavorite(entry)
                entry.isFavorite = newState
                wordAdapter.notifyItemChanged(pos)
                val msg = if (newState) getString(R.string.added_to_favorites) else getString(R.string.removed_from_favorites)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "toggleFavorite error", e)
            }
        }
    }

    private fun copyDefinition(entry: DictionaryEntry) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = "${entry.displayWord}\n${entry.definition}"
            val clip = ClipData.newPlainText("Dictionary Word", text)
            clipboard.setPrimaryClip(clip)
            lastHandledClip = text // Avoid self triggering
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "copy error", e)
        }
    }

    private fun speakWord(entry: DictionaryEntry) {
        try {
            if (tts == null || !isTtsInitialized) {
                initTts()
                Toast.makeText(this, "正在准备语音发音...", Toast.LENGTH_SHORT).show()
                return
            }

            tts?.let { player ->
                val targetLocale = when (entry.lang.lowercase()) {
                    "ms" -> Locale("ms", "MY")
                    "zh" -> Locale.CHINESE
                    else -> Locale.US
                }

                val status = player.setLanguage(targetLocale)
                if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) {
                    player.language = Locale.US
                }
                player.setSpeechRate(0.95f)
                player.speak(entry.displayWord, TextToSpeech.QUEUE_FLUSH, null, "tts_${entry.word}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak error", e)
            Toast.makeText(this, "语音发音异常", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideSuggestions() {
        binding.cardSuggestions.visibility = View.GONE
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        } catch (e: Exception) {
            Log.w(TAG, "hideKeyboard error: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        try {
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                tts?.language = Locale.US
            } else {
                isTtsInitialized = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS onInit error: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "TTS destroy error: ${e.message}")
        }
        super.onDestroy()
    }
}
