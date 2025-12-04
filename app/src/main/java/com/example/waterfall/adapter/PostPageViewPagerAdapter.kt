package com.example.waterfall.adapter

import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.example.waterfall.R

class PostPageViewPagerAdapter(
    private val mediaUrls: List<String>,
    private val onMaxImageSizeMeasured: (maxHeight: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_VIDEO = 1
        private const val TAG = "PostPageAdapter"
    }

    private var maxHeight = 0
    private var loadedCount = 0
    private var fallbackSent = false
    private val videoHolders = mutableSetOf<VideoViewHolder>()

    init {
        ensureFallbackHeight()
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view_pager_item)
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val videoView: VideoView = itemView.findViewById(R.id.video_view_pager_item)
        var isPrepared: Boolean = false
        var pendingPlay: Boolean = false
        var mediaController: MediaController? = null
    }

    override fun getItemViewType(position: Int): Int =
        if (isVideo(mediaUrls[position])) TYPE_VIDEO else TYPE_IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_VIDEO) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_pager_video_item, parent, false)
            VideoViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_pager_image_item, parent, false)
            ImageViewHolder(view)
        }
    }

    override fun getItemCount(): Int = mediaUrls.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val url = mediaUrls[position]
        if (holder is ImageViewHolder) {
            Glide.with(holder.itemView.context)
                .load(url)
                .apply(RequestOptions().centerCrop())
                .listener(glideListener(holder))
                .into(holder.imageView)
        } else if (holder is VideoViewHolder) {
            bindVideo(holder, url)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.reset()
            videoHolders.remove(holder)
        }
    }

    fun release() {
        pauseAll()
        val holders = videoHolders.toList()
        videoHolders.clear()
        holders.forEach { it.reset() }
    }

    fun playAt(position: Int) {
        val holder = findVideoHolder(position) ?: return
        try {
            if (holder.isPrepared) {
                if (!holder.videoView.isPlaying) holder.videoView.start()
                holder.pendingPlay = false
            } else {
                holder.pendingPlay = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "playAt failed for pos $position", e)
            holder.pendingPlay = false
        }
    }

    fun pauseAll() {
        val iterator = videoHolders.iterator()
        while (iterator.hasNext()) {
            val holder = iterator.next()
            if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                iterator.remove()
                continue
            }
            holder.pendingPlay = false
            try {
                if (holder.isPrepared && holder.videoView.isPlaying) holder.videoView.pause()
            } catch (e: Exception) {
                Log.w(TAG, "pauseAll failed", e)
            }
        }
    }

    private fun bindVideo(holder: VideoViewHolder, url: String) {
        videoHolders.add(holder)
        holder.reset()

        holder.mediaController = MediaController(holder.itemView.context).apply {
            setAnchorView(holder.videoView)
        }
        holder.videoView.setMediaController(holder.mediaController)

        holder.videoView.setOnPreparedListener { mp: MediaPlayer ->
            holder.isPrepared = true
            mp.isLooping = true

            val metrics = holder.itemView.context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val aspectRatio =
                if (mp.videoWidth > 0) mp.videoHeight.toFloat() / mp.videoWidth.toFloat() else 1f
            updateMaxHeight((screenWidth * aspectRatio).toInt())

            if (holder.pendingPlay && !holder.videoView.isPlaying) {
                holder.videoView.start()
            }
            holder.pendingPlay = false
            loadedCount++
            checkAllMediaLoaded()
        }

        holder.videoView.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "video error: what=$what extra=$extra url=$url")
            holder.isPrepared = false
            holder.pendingPlay = false
            loadedCount++
            checkAllMediaLoaded()
            true
        }

        try {
            holder.videoView.setVideoURI(Uri.parse(url))
        } catch (e: Exception) {
            Log.w(TAG, "setVideoURI failed for $url", e)
            loadedCount++
            checkAllMediaLoaded()
        }
    }

    private fun VideoViewHolder.reset() {
        pendingPlay = false
        isPrepared = false
        try {
            videoView.stopPlayback()
        } catch (e: Exception) {
            Log.w(TAG, "stopPlayback failed", e)
        }
        mediaController?.let {
            try {
                it.hide()
            } catch (_: Exception) {
            }
            it.setAnchorView(null)
        }
        mediaController = null
        videoView.setMediaController(null)
        videoView.setOnPreparedListener(null)
        videoView.setOnErrorListener(null)
    }

    private fun glideListener(holder: ImageViewHolder) = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>?,
            isFirstResource: Boolean
        ): Boolean {
            loadedCount++
            checkAllMediaLoaded()
            return false
        }

        override fun onResourceReady(
            resource: Drawable?,
            model: Any?,
            target: Target<Drawable>?,
            dataSource: DataSource?,
            isFirstResource: Boolean
        ): Boolean {
            resource?.let { drawable ->
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width > 0 && height > 0) {
                    val displayMetrics = holder.itemView.context.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val aspectRatio = height.toFloat() / width.toFloat()
                    updateMaxHeight((screenWidth * aspectRatio).toInt())
                }
            }
            loadedCount++
            checkAllMediaLoaded()
            return false
        }
    }

    private fun checkAllMediaLoaded() {
        if (loadedCount >= mediaUrls.size) {
            onMaxImageSizeMeasured(maxHeight)
        }
    }

    private fun updateMaxHeight(candidate: Int) {
        if (candidate <= 0) return
        if (candidate > maxHeight) {
            maxHeight = candidate
            onMaxImageSizeMeasured(maxHeight)
        }
    }

    private fun findVideoHolder(position: Int): VideoViewHolder? {
        val iterator = videoHolders.iterator()
        var target: VideoViewHolder? = null
        while (iterator.hasNext()) {
            val holder = iterator.next()
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) {
                iterator.remove()
                continue
            }
            if (adapterPos == position) {
                target = holder
                break
            }
        }
        return target
    }

    private fun ensureFallbackHeight() {
        if (fallbackSent) return
        fallbackSent = true
        onMaxImageSizeMeasured(0)
    }

    private fun isVideo(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8")
    }
}