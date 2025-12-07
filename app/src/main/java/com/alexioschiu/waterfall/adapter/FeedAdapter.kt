package com.alexioschiu.waterfall.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alexioschiu.waterfall.data.FeedItem
import com.alexioschiu.waterfall.databinding.ImageTextItemLayoutBinding
import com.alexioschiu.waterfall.holder.ImageTextViewHolder
import kotlin.math.roundToInt

class FeedAdapter : ListAdapter<FeedItem, ImageTextViewHolder>(FeedDiffCallback()) {

    data class PreviewMeta(
        val url: String?, val isVideo: Boolean, val originalWidth: Int, val originalHeight: Int
    )

    private val heightCache = mutableMapOf<String, Int>()
    private val previewMetaCache = mutableMapOf<String, PreviewMeta>()
    private var columnWidth: Int = 0

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return (getItem(position) as? FeedItem.ImageTextItem)?.id?.hashCode()?.toLong()
            ?: RecyclerView.NO_ID
    }

    fun setColumnWidth(width: Int) {
        if (width > 0 && width != columnWidth) columnWidth = width
    }

    fun getColumnWidth(): Int = columnWidth

    fun getCachedHeight(itemId: String): Int? = heightCache[itemId]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageTextViewHolder {
        val binding = ImageTextItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ImageTextViewHolder(binding, this)
    }

    override fun onBindViewHolder(holder: ImageTextViewHolder, position: Int) {
        (getItem(position) as? FeedItem.ImageTextItem)?.let(holder::bind)
    }

    override fun onViewRecycled(holder: ImageTextViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
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
        if (originalWidth <= 0 || originalHeight <= 0) return targetWidth
        val ratio = (originalWidth.toFloat() / originalHeight.toFloat()).coerceIn(0.75f, 1.333f)
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