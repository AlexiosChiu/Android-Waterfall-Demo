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
    private val onMaxImageSizeMeasured: (maxHeight: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_VIDEO = 1
        private const val TAG = "PostPageAdapter"
        private const val MIN_ASPECT_RATIO = 0.75f  // 3:4 = 0.75
        private const val MAX_ASPECT_RATIO = 0.5625f // 16:9 = 0.5625
    }

    private var maxHeight = 0
    private var loadedCount = 0
    private var fallbackSent = false
    private val videoHolders = mutableSetOf<VideoViewHolder>()
    private var firstImageAspectRatio: Float? = null  // 首图宽高比
    private var firstImageHeight = 0  // 首图高度

    init {
        ensureFallbackHeight()
    }

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
            // 如果是首图，计算宽高比并限制在3:4到16:9之间
            val isFirstImage = position == 0 && firstImageAspectRatio == null

            Glide.with(holder.itemView.context)
                .load(url)
                .apply(RequestOptions().centerCrop())
                .listener(glideListener(holder, isFirstImage))
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
                if (holder.isPrepared) {
                    holder.exoPlayer?.pause()
                }
            } catch (e: Exception) {
                Log.w(TAG, "pauseAll failed", e)
            }
        }
    }

    private fun bindVideo(holder: VideoViewHolder, url: String) {
        videoHolders.add(holder)
        holder.reset()

        // 创建ExoPlayer实例
        holder.exoPlayer = ExoPlayer.Builder(holder.itemView.context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ALL

            // 设置自适应流媒体参数，优先选择中等分辨率
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setMaxVideoSizeSd()
                .build()

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            holder.isPrepared = true
                            updateMaxHeightBasedOnVideo(holder)

                            if (holder.pendingPlay) {
                                play()
                                holder.pendingPlay = false
                            }
                            loadedCount++
                            checkAllMediaLoaded()
                        }

                        Player.STATE_ENDED -> {
                            // 视频播放完成后重新开始
                            seekTo(0)
                            play()
                        }
                    }
                }

                // 修复错误处理方法
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

    private fun updateMaxHeightBasedOnVideo(holder: VideoViewHolder) {
        val metrics = holder.itemView.context.resources.displayMetrics
        val screenWidth = metrics.widthPixels

        // 如果有首图比例，使用首图比例；否则使用默认的0.75
        val targetAspectRatio = firstImageAspectRatio ?: 0.75f
        val targetHeight = (screenWidth * targetAspectRatio).toInt()
        updateMaxHeight(targetHeight)
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

    private fun glideListener(holder: ImageViewHolder, isFirstImage: Boolean) =
        object : RequestListener<Drawable> {
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

                    // 计算原始宽高比
                    val originalAspectRatio = height.toFloat() / width.toFloat()

                    // 限制宽高比在3:4到16:9之间
                    val clampedAspectRatio = when {
                        originalAspectRatio > MIN_ASPECT_RATIO -> MIN_ASPECT_RATIO
                        originalAspectRatio < MAX_ASPECT_RATIO -> MAX_ASPECT_RATIO
                        else -> originalAspectRatio
                    }

                    // 如果是首图，记录宽高比和高度
                    if (isFirstImage) {
                        firstImageAspectRatio = clampedAspectRatio
                        firstImageHeight = (screenWidth * clampedAspectRatio).toInt()
                        updateMaxHeight(firstImageHeight)
                    } else {
                        // 非首图使用首图的比例
                        val targetAspectRatio = firstImageAspectRatio ?: clampedAspectRatio
                        updateMaxHeight((screenWidth * targetAspectRatio).toInt())
                    }
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
