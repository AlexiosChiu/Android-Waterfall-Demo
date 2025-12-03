package com.example.waterfall.adapter

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
import com.bumptech.glide.request.RequestOptions
import com.example.waterfall.R

class PostPageViewPagerAdapter(
    private val mediaUrls: List<String>,
    private val onMaxImageSizeMeasured: (maxHeight: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_VIDEO = 1
    }

    private var maxHeight = 0
    private var loadedCount = 0

    // 保存视频 holder 用于控制播放/暂停
    private val videoHolders = mutableMapOf<Int, VideoViewHolder>()

    private val imageSizes = mutableMapOf<Int, Pair<Int, Int>>()

    private fun isVideo(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8")
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view_pager_item)
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val videoView: VideoView = itemView.findViewById(R.id.video_view_pager_item)
        var isPrepared: Boolean = false
        var pendingPlay: Boolean = false
    }

    override fun getItemViewType(position: Int): Int {
        return if (isVideo(mediaUrls[position])) TYPE_VIDEO else TYPE_IMAGE
    }

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
                .apply(RequestOptions().override(1000, 1000))
                .listener(object :
                    com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        loadedCount++
                        checkAllMediaLoaded()
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        resource?.let { drawable ->
                            val width = drawable.intrinsicWidth
                            val height = drawable.intrinsicHeight
                            if (width > 0 && height > 0) {
                                imageSizes[position] = Pair(width, height)
                                val displayMetrics =
                                    holder.itemView.context.resources.displayMetrics
                                val screenWidth = displayMetrics.widthPixels
                                val aspectRatio = height.toFloat() / width.toFloat()
                                val calculatedHeight = (screenWidth * aspectRatio).toInt()
                                if (calculatedHeight > maxHeight) {
                                    maxHeight = calculatedHeight
                                }
                            }
                        }
                        loadedCount++
                        checkAllMediaLoaded()
                        return false
                    }
                }).into(holder.imageView)
        } else if (holder is VideoViewHolder) {
            // 重置状态并保存 holder 引用以便外部控制播放
            holder.isPrepared = false
            holder.pendingPlay = false
            videoHolders[position] = holder

            // 设置MediaController（可选）
            val mc = MediaController(holder.itemView.context)
            mc.setAnchorView(holder.videoView)
            holder.videoView.setMediaController(mc)

            try {
                holder.videoView.setVideoURI(Uri.parse(url))
            } catch (e: Exception) {
                // URI 设置失败也算加载完成
                Log.w("PostPageAdapter", "setVideoURI failed for $url", e)
                loadedCount++
                checkAllMediaLoaded()
                return
            }

            // 准备完成监听：在准备好后计算高度并在需要时播放
            holder.videoView.setOnPreparedListener { mp: MediaPlayer ->
                holder.isPrepared = true
                mp.isLooping = true

                // 获取视频实际尺寸，计算等比高度
                val videoWidth = mp.videoWidth
                val videoHeight = mp.videoHeight
                if (videoWidth > 0 && videoHeight > 0) {
                    val displayMetrics = holder.itemView.context.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val aspectRatio = videoHeight.toFloat() / videoWidth.toFloat()
                    val calculatedHeight = (screenWidth * aspectRatio).toInt()
                    if (calculatedHeight > maxHeight) {
                        maxHeight = calculatedHeight
                    }
                }

                // 如果之前请求了播放，立即开始
                if (holder.pendingPlay) {
                    try {
                        holder.videoView.start()
                    } catch (e: Exception) {
                        Log.w("PostPageAdapter", "start failed after prepared", e)
                    }
                    holder.pendingPlay = false
                }

                loadedCount++
                checkAllMediaLoaded()
            }

            holder.videoView.setOnErrorListener { _, what, extra ->
                // 遇到错误：清理标志并统计为已加载，避免阻塞尺寸计算
                Log.w("PostPageAdapter", "video error: what=$what extra=$extra for $url")
                holder.isPrepared = false
                holder.pendingPlay = false
                loadedCount++
                checkAllMediaLoaded()
                true
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            val position = holder.bindingAdapterPosition
            // 停止播放并移除引用，清理状态
            try {
                holder.pendingPlay = false
                holder.isPrepared = false
                holder.videoView.stopPlayback()
            } catch (e: Exception) {
                Log.w("PostPageAdapter", "error stopping playback on recycle", e)
            }
            videoHolders.remove(position)
        }
    }

    private fun checkAllMediaLoaded() {
        if (loadedCount >= mediaUrls.size) {
            onMaxImageSizeMeasured(maxHeight)
        }
    }

    // 外部调用：播放指定位置（如果是视频）
    fun playAt(position: Int) {
        videoHolders[position]?.let { vh ->
            try {
                if (vh.isPrepared) {
                    if (!vh.videoView.isPlaying) {
                        vh.videoView.start()
                    }
                    vh.pendingPlay = false
                } else {
                    // 标记等待播放，实际在 onPrepared 时会启动
                    vh.pendingPlay = true
                }
            } catch (e: Exception) {
                Log.w("PostPageAdapter", "playAt failed for pos $position", e)
                vh.pendingPlay = false
            }
        }
    }

    // 外部调用：暂停所有视频
    fun pauseAll() {
        videoHolders.values.forEach { vh ->
            try {
                // 清除待播放标志
                vh.pendingPlay = false
                if (vh.isPrepared && vh.videoView.isPlaying) vh.videoView.pause()
            } catch (e: Exception) {
                Log.w("PostPageAdapter", "pauseAll failed", e)
            }
        }
    }
}