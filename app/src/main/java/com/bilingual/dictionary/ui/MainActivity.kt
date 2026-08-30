package com.bilingual.dictionary.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bilingual.dictionary.DictionaryApplication
import com.bilingual.dictionary.R
import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.data.model.SearchMode
import com.bilingual.dictionary.data.repository.DictionaryRepository
import com.bilingual.dictionary.databinding.ActivityMainBinding
import com.bilingual.dictionary.ui.adapter.FavoriteAdapter
import com.bilingual.dictionary.ui.adapter.HistoryAdapter
import com.bilingual.dictionary.ui.adapter.SuggestionAdapter
import com.bilingual.dictionary.ui.adapter.WordCardAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DictionaryRepository
    private var tts: TextToSpeech? = null

    private lateinit var wordAdapter: WordCardAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var favoriteAdapter: FavoriteAdapter

    private var currentMode = SearchMode.AUTO_DETECT
    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = (application as DictionaryApplication).repository
        tts = TextToSpeech(this, this)

        setupRecyclerViews()
        setupSearchInput()
        setupModeChips()
        setupBottomNavigation()

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (Intent.ACTION_PROCESS_TEXT == intent.action && intent.type == "text/plain") {
            val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!selectedText.isNullOrBlank()) {
                binding.etSearch.setText(selectedText)
                binding.etSearch.setSelection(selectedText.length)
                performSearch(selectedText)
            }
        }
    }

    private fun setupRecyclerViews() {
        // 1. Word Card Adapter
        wordAdapter = WordCardAdapter(
            onSpeakClick = { entry -> speakWord(entry) },
            onFavoriteClick = { entry, pos -> toggleFavorite(entry, pos) },
            onCopyClick = { entry -> copyDefinition(entry) }
        )
        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wordAdapter
        }

        // 2. Suggestion Adapter
        suggestionAdapter = SuggestionAdapter { suggestion ->
            binding.etSearch.setText(suggestion.word)
            binding.etSearch.setSelection(suggestion.word.length)
            hideSuggestions()
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
                performSearch(favItem.word)
            },
            onRemoveClick = { favItem ->
                lifecycleScope.launch {
                    repository.removeFavorite(favItem.word, favItem.lang)
                    loadFavorites()
                    // If currently displaying this word in search, update it
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
                        delay(150)
                        val suggestions = repository.getSuggestions(query)
                        if (suggestions.isNotEmpty() && binding.panelSearch.isVisible) {
                            suggestionAdapter.submitList(suggestions)
                            binding.rvSuggestions.visibility = View.VISIBLE
                        } else {
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
                    hideKeyboard()
                    hideSuggestions()
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text.clear()
            wordAdapter.submitList(emptyList())
            binding.layoutEmptySearch.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = "输入单词即可秒级离线查词"
            hideSuggestions()
        }
    }

    private fun setupModeChips() {
        binding.chipGroupMode.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) {
                binding.chipAuto.isChecked = true
                return@setOnCheckedStateChangeListener
            }
            currentMode = when (checkedIds.first()) {
                R.id.chipZhToEn -> SearchMode.ZH_TO_EN
                R.id.chipZhToMs -> SearchMode.ZH_TO_MS
                else -> SearchMode.AUTO_DETECT
            }

            val query = binding.etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            hideSuggestions()
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

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.layoutEmptySearch.visibility = View.GONE

            val results = repository.lookup(query, currentMode)
            binding.progressBar.visibility = View.GONE

            if (results.isNotEmpty()) {
                wordAdapter.submitList(results)
                binding.layoutEmptySearch.visibility = View.GONE
            } else {
                wordAdapter.submitList(emptyList())
                binding.layoutEmptySearch.visibility = View.VISIBLE
                binding.tvEmptyTitle.text = getString(R.string.no_results)
            }
        }
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            val list = repository.getFavorites()
            favoriteAdapter.submitList(list)
            binding.tvEmptyFavorites.isVisible = list.isEmpty()
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val list = repository.getHistory()
            historyAdapter.submitList(list)
            binding.tvEmptyHistory.isVisible = list.isEmpty()
        }
    }

    private fun toggleFavorite(entry: DictionaryEntry, pos: Int) {
        lifecycleScope.launch {
            val newState = repository.toggleFavorite(entry)
            entry.isFavorite = newState
            wordAdapter.notifyItemChanged(pos)
            val msg = if (newState) getString(R.string.added_to_favorites) else getString(R.string.removed_from_favorites)
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyDefinition(entry: DictionaryEntry) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = "${entry.displayWord}\n${entry.definition}"
        val clip = ClipData.newPlainText("Dictionary Word", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun speakWord(entry: DictionaryEntry) {
        tts?.let { player ->
            val lang = when (entry.lang.lowercase()) {
                "ms" -> Locale("ms", "MY")
                "zh" -> Locale.CHINESE
                else -> Locale.US
            }
            player.language = lang
            player.speak(entry.displayWord, TextToSpeech.QUEUE_FLUSH, null, "tts_${entry.word}")
        }
    }

    private fun hideSuggestions() {
        binding.rvSuggestions.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
