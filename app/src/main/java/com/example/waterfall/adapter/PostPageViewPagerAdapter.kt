package com.example.waterfall.adapter

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    private val onMaxImageSizeMeasured: (maxHeight: Int) -> Unit,
    private val firstClipWidth: Int?,
    private val firstClipHeight: Int?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_VIDEO = 1
        private const val TAG = "PostPageAdapter"
        private const val MIN_PORTRAIT_RATIO = 0.75f    // 3:4
        private const val MAX_LANDSCAPE_RATIO = 1.778f  // 16:9
    }

    private var maxHeight = 0
    private var loadedCount = 0
    private val videoHolders = mutableSetOf<VideoViewHolder>()
    private var firstMediaAspectRatio: Float? = null

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view_pager_item)
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playerView: PlayerView = itemView.findViewById(R.id.player_view)
        var exoPlayer: ExoPlayer? = null
        var isPrepared: Boolean = false
        var pendingPlay: Boolean = false
    }

    override fun getItemViewType(position: Int): Int =
        if (isVideo(mediaUrls[position])) TYPE_VIDEO else TYPE_IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_VIDEO) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_pager_video_item, parent, false)
            VideoViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_pager_image_item, parent, false)
            ImageViewHolder(view)
        }

    override fun getItemCount(): Int = mediaUrls.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val url = mediaUrls[position]
        if (position == 0) {
            tryApplyPreMeasuredAspectRatio(holder.itemView)
        }
        if (holder is ImageViewHolder) {
            Glide.with(holder.itemView.context).load(url).apply(RequestOptions())
                .listener(glideListener()).into(holder.imageView)
        } else if (holder is VideoViewHolder) {
            bindVideo(holder, url, position)
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
                holder.exoPlayer?.play()
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
                if (holder.isPrepared) holder.exoPlayer?.pause()
            } catch (e: Exception) {
                Log.w(TAG, "pauseAll failed", e)
            }
        }
    }

    private fun bindVideo(holder: VideoViewHolder, url: String, position: Int) {
        videoHolders.add(holder)
        holder.reset()

        holder.exoPlayer = ExoPlayer.Builder(holder.itemView.context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ALL
            trackSelectionParameters =
                trackSelectionParameters.buildUpon().setMaxVideoSizeSd().build()

            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (position != 0 || firstMediaAspectRatio != null) return
                    val width = videoSize.width
                    val height = videoSize.height
                    if (width > 0 && height > 0) {
                        val ratio = clampAspectRatio(width.toFloat() / height.toFloat())
                        val screenWidth =
                            holder.itemView.context.resources.displayMetrics.widthPixels
                        applyFirstMediaAspectRatio(ratio, screenWidth)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            holder.isPrepared = true
                            if (holder.pendingPlay) {
                                play()
                                holder.pendingPlay = false
                            }
                            loadedCount++
                            checkAllMediaLoaded()
                        }

                        Player.STATE_ENDED -> {
                            seekTo(0)
                            play()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "ExoPlayer error for $url: ${error.message}", error)
                    holder.isPrepared = false
                    holder.pendingPlay = false
                    loadedCount++
                    checkAllMediaLoaded()
                }
            })

            prepare()
        }

        holder.playerView.player = holder.exoPlayer
        holder.playerView.useController = true
    }

    private fun glideListener() = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean
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
            loadedCount++
            checkAllMediaLoaded()
            return false
        }
    }

    private fun tryApplyPreMeasuredAspectRatio(view: View) {
        if (firstMediaAspectRatio != null) return
        val width = firstClipWidth
        val height = firstClipHeight
        if (width == null || height == null || width <= 0 || height <= 0) return
        val ratio = clampAspectRatio(width.toFloat() / height.toFloat())
        val screenWidth = view.context.resources.displayMetrics.widthPixels
        applyFirstMediaAspectRatio(ratio, screenWidth)
    }

    private fun clampAspectRatio(rawRatio: Float): Float = when {
        rawRatio < MIN_PORTRAIT_RATIO -> MIN_PORTRAIT_RATIO
        rawRatio > MAX_LANDSCAPE_RATIO -> MAX_LANDSCAPE_RATIO
        else -> rawRatio
    }

    private fun applyFirstMediaAspectRatio(ratio: Float, screenWidth: Int) {
        if (firstMediaAspectRatio != null) return
        firstMediaAspectRatio = ratio
        val targetHeight = (screenWidth / ratio).toInt()
        updateMaxHeight(targetHeight)
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

    private fun VideoViewHolder.reset() {
        pendingPlay = false
        isPrepared = false
        exoPlayer?.let { player ->
            player.stop()
            player.release()
        }
        exoPlayer = null
        playerView.player = null
    }

    private fun isVideo(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8")
    }
}