package com.example.waterfall.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.example.waterfall.R
import com.example.waterfall.data.FeedItem
import com.example.waterfall.holder.ImageTextViewHolder
import com.example.waterfall.holder.ItemViewHolder
import kotlin.math.roundToInt

class FeedAdapter : ListAdapter<FeedItem, ItemViewHolder>(FeedDiffCallback()) {

    private val heightCache = mutableMapOf<String, Int>()
    private var columnWidth: Int = 0

    fun setColumnWidth(width: Int) {
        if (width > 0 && width != columnWidth) {
            columnWidth = width
        }
    }

    fun getColumnWidth(): Int = columnWidth

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.image_text_item_layout, parent, false)
        return ImageTextViewHolder(view, this)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun submitList(list: List<FeedItem>?) {
        heightCache.clear()
        if (list != null && columnWidth > 0) {
            list.filterIsInstance<FeedItem.ImageTextItem>().forEach { item ->
                val height = calculateTargetHeight(item.coverWidth, item.coverHeight, columnWidth)
                heightCache[item.id] = height
            }
        }
        super.submitList(list)
    }

    fun getCachedHeight(itemId: String): Int? = heightCache[itemId]

    private fun calculateTargetHeight(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int
    ): Int {
        if (originalWidth <= 0 || originalHeight <= 0) return targetWidth

        val ratio = (originalHeight.toFloat() / originalWidth).coerceIn(
            0.75f,
            1.333f
        ) // 限制高度比例在 0.75 到 1.333 之间
        return (targetWidth * ratio).roundToInt()
    }


}

class FeedDiffCallback : DiffUtil.ItemCallback<FeedItem>() {
    override fun areItemsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
        return (oldItem as? FeedItem.ImageTextItem)?.id == (newItem as? FeedItem.ImageTextItem)?.id
    }

    override fun areContentsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
        return oldItem == newItem
    }
}