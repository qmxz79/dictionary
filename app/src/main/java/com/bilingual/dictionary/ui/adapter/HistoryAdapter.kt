package com.bilingual.dictionary.ui.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bilingual.dictionary.data.model.HistoryItem
import com.bilingual.dictionary.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            binding.tvHistoryQuery.text = item.query
            binding.tvHistoryTime.text = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            itemView.setOnClickListener { onItemClick(item) }
            binding.btnDeleteHistory.setOnClickListener { onDeleteClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
            oldItem == newItem
    }
}
