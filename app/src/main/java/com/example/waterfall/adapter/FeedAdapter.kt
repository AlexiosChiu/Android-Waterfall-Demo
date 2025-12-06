package com.example.waterfall.adapter

import android.util.Log
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

    data class PreviewMeta(
        val url: String?, val isVideo: Boolean, val originalWidth: Int, val originalHeight: Int
    )

    private val heightCache = mutableMapOf<String, Int>()
    private val previewMetaCache = mutableMapOf<String, PreviewMeta>()
    private var columnWidth: Int = 0

    fun setColumnWidth(width: Int) {
        if (width > 0 && width != columnWidth) columnWidth = width
    }

    fun getColumnWidth(): Int = columnWidth

    fun getCachedHeight(itemId: String): Int? = heightCache[itemId]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.image_text_item_layout, parent, false)
        return ImageTextViewHolder(view, this)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ItemViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ImageTextViewHolder) {
            holder.recycle()
        }
    }

    override fun submitList(list: List<FeedItem>?, commitCallback: Runnable?) {
        heightCache.clear()
        previewMetaCache.clear()

        if (list != null && columnWidth > 0) {
            list.filterIsInstance<FeedItem.ImageTextItem>().forEach { item ->
                val previewUrl = when {
                    item.coverClip.isNotBlank() -> item.coverClip
                    item.clips.isNotEmpty() -> item.clips.firstOrNull { it.isNotBlank() }
                    else -> null
                }

                val sourceWidth = item.coverWidth.takeIf { it > 0 } ?: columnWidth
                val sourceHeight = item.coverHeight.takeIf { it > 0 } ?: columnWidth
                val height = calculateTargetHeight(sourceWidth, sourceHeight, columnWidth)

                heightCache[item.id] = height
                previewMetaCache[item.id] = PreviewMeta(
                    url = previewUrl,
                    isVideo = previewUrl?.let(::isVideoUrl) ?: false,
                    originalWidth = sourceWidth,
                    originalHeight = sourceHeight
                )
            }
        }

        super.submitList(list, commitCallback)
    }

    private fun calculateTargetHeight(
        originalWidth: Int, originalHeight: Int, targetWidth: Int
    ): Int {
        Log.i(
            "FeedAdapter",
            "calculateTargetHeight: originalWidth=$originalWidth, originalHeight=$originalHeight, targetWidth=$targetWidth"
        )
        if (originalWidth <= 0 || originalHeight <= 0) return targetWidth
        val ratio = (originalWidth.toFloat() / originalHeight.toFloat()).coerceIn(0.75f, 1.333f)
        Log.i(
            "FeedAdapter",
            "calculateTargetHeight: ratio=$ratio, targetHeight=${(targetWidth / ratio).roundToInt()}"
        )
        return (targetWidth / ratio).roundToInt()
    }

    private fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8")
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