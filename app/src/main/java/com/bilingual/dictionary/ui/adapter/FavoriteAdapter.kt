package com.bilingual.dictionary.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bilingual.dictionary.R
import com.bilingual.dictionary.data.model.FavoriteItem
import com.bilingual.dictionary.databinding.ItemFavoriteBinding

class FavoriteAdapter(
    private val onItemClick: (FavoriteItem) -> Unit,
    private val onRemoveClick: (FavoriteItem) -> Unit
) : ListAdapter<FavoriteItem, FavoriteAdapter.FavoriteViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FavoriteViewHolder(private val binding: ItemFavoriteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FavoriteItem) {
            val context = itemView.context
            if (item.lang.equals("en", ignoreCase = true)) {
                binding.tvFavBadge.text = "EN"
                binding.tvFavBadge.setBackgroundResource(R.drawable.bg_tag_english)
                binding.tvFavBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_english))
            } else {
                binding.tvFavBadge.text = "MS"
                binding.tvFavBadge.setBackgroundResource(R.drawable.bg_tag_malay)
                binding.tvFavBadge.setTextColor(ContextCompat.getColor(context, R.color.tag_malay))
            }

            binding.tvFavWord.text = item.word
            binding.tvFavDef.text = item.definition

            itemView.setOnClickListener { onItemClick(item) }
            binding.btnFavDelete.setOnClickListener { onRemoveClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FavoriteItem>() {
        override fun areItemsTheSame(oldItem: FavoriteItem, newItem: FavoriteItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FavoriteItem, newItem: FavoriteItem): Boolean =
            oldItem == newItem
    }
}
