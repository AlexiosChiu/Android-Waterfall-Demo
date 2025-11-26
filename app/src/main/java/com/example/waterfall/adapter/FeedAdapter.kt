package com.example.waterfall.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.waterfall.R
import com.example.waterfall.data.FeedItem
import com.example.waterfall.holder.ImageTextViewHolder
import com.example.waterfall.holder.ItemViewHolder

class FeedAdapter : RecyclerView.Adapter<ItemViewHolder>() {

    private val items = mutableListOf<FeedItem>()

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FeedItem.ImageTextItem -> 0
            is FeedItem.VideoItem -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            0 -> {
                val view = inflater.inflate(R.layout.image_text_item_layout, parent, false)
                ImageTextViewHolder(view)
            }

            //暂时用图文layout
            1 -> {
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
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

}