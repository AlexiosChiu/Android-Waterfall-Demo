package com.example.waterfall.holder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.waterfall.data.FeedItem

abstract class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: FeedItem)
}