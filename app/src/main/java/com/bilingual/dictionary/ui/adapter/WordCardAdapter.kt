package com.bilingual.dictionary.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bilingual.dictionary.R
import com.bilingual.dictionary.data.model.DictionaryEntry
import com.bilingual.dictionary.databinding.ItemWordCardBinding

class WordCardAdapter(
    private val onSpeakClick: (DictionaryEntry) -> Unit,
    private val onFavoriteClick: (DictionaryEntry, Int) -> Unit,
    private val onCopyClick: (DictionaryEntry) -> Unit,
    var fontSizeSp: Float = 16f
) : ListAdapter<DictionaryEntry, WordCardAdapter.WordViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val binding = ItemWordCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WordViewHolder(private val binding: ItemWordCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: DictionaryEntry) {
            val context = itemView.context

            // Badge setup
            when (entry.lang.lowercase()) {
                "en" -> {
                    binding.tvLangBadge.text = "英语"
                    binding.tvLangBadge.setBackgroundResource(R.drawable.bg_tag_english)
                    binding.tvLangBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_english))
                }
                "ms" -> {
                    binding.tvLangBadge.text = "马来语"
                    binding.tvLangBadge.setBackgroundResource(R.drawable.bg_tag_malay)
                    binding.tvLangBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_malay))
                }
                "online" -> {
                    binding.tvLangBadge.text = "网络翻译"
                    binding.tvLangBadge.setBackgroundResource(R.drawable.bg_tag_online)
                    binding.tvLangBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_online))
                }
                else -> {
                    binding.tvLangBadge.text = "释义"
                    binding.tvLangBadge.setBackgroundResource(R.drawable.bg_tag_english)
                    binding.tvLangBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_english))
                }
            }

            // Word display
            binding.tvWord.text = entry.displayWord

            // Phonetic & POS
            val hasPhonetic = !entry.phonetic.isNullOrEmpty()
            val hasPos = !entry.pos.isNullOrEmpty()
            if (hasPhonetic || hasPos) {
                binding.layoutPhonetic.visibility = View.VISIBLE
                binding.tvPhonetic.text = entry.phonetic ?: ""
                binding.tvPhonetic.visibility = if (hasPhonetic) View.VISIBLE else View.GONE
                binding.tvPos.text = entry.pos ?: ""
                binding.tvPos.visibility = if (hasPos) View.VISIBLE else View.GONE
            } else {
                binding.layoutPhonetic.visibility = View.GONE
            }

            // Stemming annotation
            if (!entry.stemNote.isNullOrEmpty()) {
                binding.tvStemInfo.visibility = View.VISIBLE
                binding.tvStemInfo.text = entry.stemNote
            } else {
                binding.tvStemInfo.visibility = View.GONE
            }

            // Definition
            binding.tvDefinition.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            binding.tvDefinition.text = entry.definition

            // Example
            if (!entry.example.isNullOrEmpty()) {
                binding.tvExample.visibility = View.VISIBLE
                binding.tvExample.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, (fontSizeSp - 2f).coerceAtLeast(12f))
                binding.tvExample.text = "例句: ${entry.example}"
            } else {
                binding.tvExample.visibility = View.GONE
            }

            // Favorite icon
            binding.btnFavorite.setImageResource(
                if (entry.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border
            )

            // Click listeners
            binding.btnSpeak.setOnClickListener { onSpeakClick(entry) }
            binding.btnFavorite.setOnClickListener { 
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onFavoriteClick(entry, pos)
                }
            }
            binding.btnCopy.setOnClickListener { onCopyClick(entry) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DictionaryEntry>() {
        override fun areItemsTheSame(oldItem: DictionaryEntry, newItem: DictionaryEntry): Boolean {
            return oldItem.word == newItem.word && oldItem.lang == newItem.lang
        }

        override fun areContentsTheSame(oldItem: DictionaryEntry, newItem: DictionaryEntry): Boolean {
            return oldItem == newItem
        }
    }
}
