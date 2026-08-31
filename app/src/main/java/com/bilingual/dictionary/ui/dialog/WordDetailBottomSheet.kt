package com.bilingual.dictionary.ui.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.bilingual.dictionary.R
import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.databinding.DialogWordDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class WordDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogWordDetailBinding? = null
    private val binding get() = _binding!!

    private var entry: DictionaryEntry? = null
    private var tts: TextToSpeech? = null
    private var onFavoriteToggled: ((DictionaryEntry) -> Unit)? = null
    private var onOpenInMain: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogWordDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentEntry = entry
        if (currentEntry == null) {
            // Entry lost after config change — dismiss gracefully
            dismissAllowingStateLoss()
            return
        }

        val context = requireContext()

        // Lang Badge
        when (currentEntry.lang.lowercase()) {
            "en" -> {
                binding.tvDetailLangBadge.text = "英语"
                binding.tvDetailLangBadge.setBackgroundResource(R.drawable.bg_tag_english)
                binding.tvDetailLangBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_english))
            }
            "ms" -> {
                binding.tvDetailLangBadge.text = "马来语"
                binding.tvDetailLangBadge.setBackgroundResource(R.drawable.bg_tag_malay)
                binding.tvDetailLangBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_malay))
            }
            else -> {
                binding.tvDetailLangBadge.text = "释义"
                binding.tvDetailLangBadge.setBackgroundResource(R.drawable.bg_tag_english)
                binding.tvDetailLangBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_english))
            }
        }

        binding.tvDetailWord.text = currentEntry.displayWord

        // Phonetic & POS
        val hasPhonetic = !currentEntry.phonetic.isNullOrEmpty()
        val hasPos = !currentEntry.pos.isNullOrEmpty()
        if (hasPhonetic || hasPos) {
            binding.layoutDetailPhonetic.visibility = View.VISIBLE
            binding.tvDetailPhonetic.text = currentEntry.phonetic ?: ""
            binding.tvDetailPhonetic.visibility = if (hasPhonetic) View.VISIBLE else View.GONE
            binding.tvDetailPos.text = currentEntry.pos ?: ""
            binding.tvDetailPos.visibility = if (hasPos) View.VISIBLE else View.GONE
        } else {
            binding.layoutDetailPhonetic.visibility = View.GONE
        }

        binding.tvDetailDefinition.text = currentEntry.definition

        if (!currentEntry.example.isNullOrEmpty()) {
            binding.tvDetailExample.visibility = View.VISIBLE
            binding.tvDetailExample.text = "例句: ${currentEntry.example}"
        } else {
            binding.tvDetailExample.visibility = View.GONE
        }

        binding.btnDetailFavorite.setImageResource(
            if (currentEntry.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
        )

        binding.btnDetailSpeak.setOnClickListener {
            speakWord(currentEntry)
        }

        binding.btnDetailFavorite.setOnClickListener {
            onFavoriteToggled?.invoke(currentEntry)
            currentEntry.isFavorite = !currentEntry.isFavorite
            binding.btnDetailFavorite.setImageResource(
                if (currentEntry.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
            )
            val msg = if (currentEntry.isFavorite) "已添加到生词本" else "已从生词本移除"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnDetailCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = "${currentEntry.displayWord}\n${currentEntry.definition}"
            clipboard.setPrimaryClip(ClipData.newPlainText("Dictionary Word", text))
            Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        binding.btnViewInDictionary.setOnClickListener {
            dismiss()
            onOpenInMain?.invoke(currentEntry.displayWord)
        }
    }

    private fun speakWord(entry: DictionaryEntry) {
        try {
            val player = tts
            if (player == null) {
                Toast.makeText(requireContext(), "语音引擎未就绪，请稍后重试", Toast.LENGTH_SHORT).show()
                return
            }

            val targetLocale = when (entry.lang.lowercase()) {
                "ms" -> Locale("ms", "MY")
                "zh" -> Locale.CHINESE
                else -> Locale.US
            }

            val status = player.setLanguage(targetLocale)
            if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English
                val fallback = player.setLanguage(Locale.US)
                if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(requireContext(), "设备不支持该语言发音", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            player.setSpeechRate(0.95f)
            player.speak(entry.displayWord, TextToSpeech.QUEUE_FLUSH, null, "tts_detail_${entry.word}")
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "发音失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun setTts(ttsInstance: TextToSpeech?) {
        this.tts = ttsInstance
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "WordDetailBottomSheet"

        fun newInstance(
            entry: DictionaryEntry,
            tts: TextToSpeech?,
            onFavoriteToggled: ((DictionaryEntry) -> Unit)? = null,
            onOpenInMain: ((String) -> Unit)? = null
        ): WordDetailBottomSheet {
            return WordDetailBottomSheet().apply {
                this.entry = entry
                this.tts = tts
                this.onFavoriteToggled = onFavoriteToggled
                this.onOpenInMain = onOpenInMain
            }
        }
    }
}
