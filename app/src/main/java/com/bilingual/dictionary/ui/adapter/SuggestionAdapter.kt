package com.bilingual.dictionary.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bilingual.dictionary.R
import com.bilingual.dictionary.data.model.SuggestionItem
import com.bilingual.dictionary.databinding.ItemSuggestionBinding

class SuggestionAdapter(
    private val onSuggestionClick: (SuggestionItem) -> Unit
) : ListAdapter<SuggestionItem, SuggestionAdapter.SuggestionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SuggestionViewHolder(private val binding: ItemSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SuggestionItem) {
            val context = itemView.context
            if (item.lang.equals("en", ignoreCase = true)) {
                binding.tvSuggestionBadge.text = "EN"
                binding.tvSuggestionBadge.setBackgroundResource(R.drawable.bg_tag_english)
                binding.tvSuggestionBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_english))
            } else {
                binding.tvSuggestionBadge.text = "MS"
                binding.tvSuggestionBadge.setBackgroundResource(R.drawable.bg_tag_malay)
                binding.tvSuggestionBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_malay))
            }

            binding.tvSuggestionWord.text = item.word
            binding.tvSuggestionDef.text = item.definition

            itemView.setOnClickListener { onSuggestionClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SuggestionItem>() {
        override fun areItemsTheSame(oldItem: SuggestionItem, newItem: SuggestionItem): Boolean =
            oldItem.word == newItem.word && oldItem.lang == newItem.lang

        override fun areContentsTheSame(oldItem: SuggestionItem, newItem: SuggestionItem): Boolean =
            oldItem == newItem
    }
}
