package com.example.waterfall.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.waterfall.R
import com.example.waterfall.data.FeedItem
import com.example.waterfall.holder.ImageTextViewHolder
import com.example.waterfall.holder.ItemViewHolder

class FeedAdapter : RecyclerView.Adapter<ItemViewHolder>() {

    private companion object {
        private const val VIEW_TYPE_IMAGE_TEXT = 0
    }

    private val items = mutableListOf<FeedItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_IMAGE_TEXT -> {
                val view = inflater.inflate(R.layout.image_text_item_layout, parent, false)
                ImageTextViewHolder(view)
            }

            else -> throw IllegalArgumentException("未知的视图类型: $viewType")
        }
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<FeedItem>) {
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object :
            androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newItems[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newItems[newItemPosition]
            }
        })
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

}