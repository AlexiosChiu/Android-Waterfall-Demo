package com.example.waterfall.holder

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.scale
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.waterfall.R
import com.example.waterfall.activity.PostPageActivity
import com.example.waterfall.adapter.FeedAdapter
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.LikePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ImageTextViewHolder(private val view: View, private val adapter: FeedAdapter) :
    ItemViewHolder(view) {
    private val avatar: ImageView = view.findViewById(R.id.avatar)
    private val authorName: TextView = view.findViewById(R.id.author_name)
    private val title: TextView = view.findViewById(R.id.post_title)
    private val likes: TextView = view.findViewById(R.id.likes)
    private val coverImage: ImageView = view.findViewById(R.id.cover_image)
    private val likeButton: ImageButton = view.findViewById(R.id.likeButton)
    private lateinit var prefs: LikePreferences

    // 添加用于取消操作的标志和任务引用
    private val isRecycled = AtomicBoolean(false)
    private var currentVideoTask: Thread? = null
    private var currentMediaRetriever: MediaMetadataRetriever? = null
    private var currentThumbnailExtractionTask: kotlinx.coroutines.Job? = null
    private var currentExoPlayer: ExoPlayer? = null

    // 视频帧缓存
    companion object {
        private val videoFrameCache = ConcurrentHashMap<String, Bitmap>()
        private const val MAX_CACHE_SIZE = 32 // 限制缓存大小
    }

    override fun bind(item: FeedItem) {
        // 立即取消所有之前的异步任务
        recycle()
        // 重置回收标志
        isRecycled.set(false)

        if (item is FeedItem.ImageTextItem) {
            prefs = LikePreferences(view.context)

            val targetWidth = adapter.getColumnWidth()
            if (targetWidth > 0) {
                val targetHeight = adapter.getCachedHeight(item.id) ?: targetWidth
                val params =
                    coverImage.layoutParams ?: ViewGroup.LayoutParams(targetWidth, targetHeight)
                params.width = targetWidth
                params.height = targetHeight
                coverImage.layoutParams = params
            }

            val persistedLikes = prefs.getLikes(item.id, item.likes)
            val persistedLiked = prefs.getLiked(item.id, item.liked)

            item.likes = persistedLikes
            item.liked = persistedLiked

            authorName.text = item.authorName
            title.text = item.title ?: item.content
            likes.text = item.likes.toString()

            likeButton.setImageResource(
                if (item.liked) R.drawable.like_icon_red else R.drawable.like_icon
            )

            Glide.with(avatar.context).load(item.avatar).circleCrop().into(avatar)

            loadImage(item.coverClip)

            setupInteractionButtons(item)
            setupClickListeners(item)
        }
    }

    // 添加recycle方法，在ViewHolder被回收时调用
    fun recycle() {
        // 标记为已回收
        isRecycled.set(true)

        // 立即清除图片显示
        coverImage.setImageDrawable(null)

        // 取消当前正在执行的视频帧提取任务
        currentVideoTask?.let { task ->
            if (task.isAlive) {
                try {
                    task.interrupt() // 强制中断线程
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            currentVideoTask = null
        }

        // 取消协程任务
        currentThumbnailExtractionTask?.cancel()
        currentThumbnailExtractionTask = null

        // 释放ExoPlayer资源
        currentExoPlayer?.release()
        currentExoPlayer = null

        // 取消Glide加载
        Glide.with(coverImage).clear(coverImage)

        // 释放MediaMetadataRetriever
        releaseMediaRetriever()
    }

    private fun loadImage(imageUrl: String) {
        // 立即清除当前图片，避免显示旧数据
        coverImage.setImageDrawable(null)

        // 检查是否为视频URL
        fun isVideo(url: String): Boolean {
            val lower = url.lowercase()
            return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8")
        }

        // 对于图片URL，使用Glide加载较低分辨率的版本
        if (!isVideo(imageUrl)) {
            // 立即取消所有之前的Glide加载
            Glide.with(coverImage).clear(coverImage)

            // 快速加载模糊的低分辨率版本
            val lowQualityRequest = Glide.with(coverImage.context)
                .load(imageUrl)
                .override(10) // 超低分辨率预览
                .centerCrop()
                .dontAnimate()

            // 然后加载优化后的中等分辨率版本
            val targetWidth = adapter.getColumnWidth() * 0.5f
            val targetHeight = targetWidth * 0.75f // 保持合适的宽高比

            Glide.with(coverImage.context)
                .load(imageUrl)
                .centerCrop()
                .thumbnail(lowQualityRequest)
                .override(targetWidth.toInt(), targetHeight.toInt()) // 限制最大尺寸
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(coverImage)

            return
        }

        // 对于视频URL，优先使用ExoPlayer的缩略图提取
        extractThumbnailWithExoPlayer(imageUrl)
    }

    /**
     * 使用ExoPlayer提取视频缩略图
     */
    private fun extractThumbnailWithExoPlayer(videoUrl: String) {
        // 1. 首先检查缓存中是否已有该视频的帧
        if (videoFrameCache.containsKey(videoUrl)) {
            val cachedBitmap = videoFrameCache[videoUrl]
            if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                coverImage.setImageBitmap(cachedBitmap)
                return // 使用缓存的帧，不再进行加载
            } else {
                // 缓存中的bitmap已被回收，移除缓存
                videoFrameCache.remove(videoUrl)
            }
        }

        // 2. 使用协程在后台执行缩略图提取
        currentThumbnailExtractionTask = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 检查是否已被回收
                if (isRecycled.get()) return@launch

                // 创建ExoPlayer实例
                val exoPlayer = ExoPlayer.Builder(view.context).build()
                currentExoPlayer = exoPlayer

                // 创建MediaItem
                val mediaItem = MediaItem.fromUri(videoUrl)

                // 设置媒体项并准备播放器
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()

                // 等待播放器准备完成
                var retryCount = 0
                while (exoPlayer.playbackState != Player.STATE_READY && retryCount < 10 && !isRecycled.get()) {
                    kotlinx.coroutines.delay(100)
                    retryCount++
                }

                if (exoPlayer.playbackState != Player.STATE_READY || isRecycled.get()) {
                    exoPlayer.release()
                    return@launch
                }

                // 获取视频信息
                val videoFormat = exoPlayer.videoFormat
                if (videoFormat != null) {
                    // 使用MediaMetadataRetriever作为备选方案
                    extractThumbnailWithSmartRetriever(videoUrl)
                } else {
                    // 如果无法获取视频格式，使用传统方法
                    fallbackToMediaMetadataRetriever(videoUrl)
                }

                // 释放ExoPlayer资源
                exoPlayer.release()
                currentExoPlayer = null

            } catch (e: Exception) {
                e.printStackTrace()
                // 如果ExoPlayer方法失败，回退到MediaMetadataRetriever
                if (!isRecycled.get()) {
                    fallbackToMediaMetadataRetriever(videoUrl)
                }
            }
        }
    }

    /**
     * 智能的MediaMetadataRetriever方案 - 优化性能
     */
    private fun extractThumbnailWithSmartRetriever(videoUrl: String) {
        // 在后台线程使用MediaMetadataRetriever提取视频帧
        val videoTask = Thread {
            var bitmap: Bitmap?

            try {
                // 创建新的MediaMetadataRetriever
                val retriever = MediaMetadataRetriever()
                currentMediaRetriever = retriever

                // 检查是否已被回收，如果是则退出
                if (isRecycled.get()) {
                    releaseMediaRetriever()
                    return@Thread
                }

                // 设置数据源，尝试处理网络视频URL
                try {
                    retriever.setDataSource(videoUrl, HashMap<String, String>())
                } catch (_: Exception) {
                    // 如果带headers的方法失败，尝试简单设置
                    retriever.setDataSource(videoUrl)
                }

                // 再次检查是否已被回收
                if (isRecycled.get()) {
                    releaseMediaRetriever()
                    return@Thread
                }

                // 获取第0毫秒的帧 - 使用更小的分辨率选项
                bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                // 对获取到的bitmap进行缩放处理 - 使用更小的目标尺寸
                if (bitmap != null && !isRecycled.get()) {
                    // 计算目标尺寸（使用30%的列宽，实现低分辨率）
                    val targetWidth = (adapter.getColumnWidth() * 0.3f).coerceAtLeast(50f)

                    // 计算缩放比例，确保图片按比例缩放
                    val scaleFactor = targetWidth / bitmap.width

                    // 创建缩放后的bitmap
                    bitmap = bitmap.scale(
                        targetWidth.toInt(),
                        (bitmap.height * scaleFactor).toInt().coerceAtLeast(30)
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                bitmap = null
            } finally {
                // 释放MediaMetadataRetriever
                releaseMediaRetriever()
            }

            // 只在成功获取到有效bitmap且ViewHolder未被回收时才更新UI
            if (bitmap != null && !bitmap.isRecycled && !isRecycled.get()) {
                // 缓存获取到的帧
                synchronized(videoFrameCache) {
                    // 限制缓存大小，移除最旧的项
                    if (videoFrameCache.size >= MAX_CACHE_SIZE) {
                        val oldestKey = videoFrameCache.keys.firstOrNull()
                        oldestKey?.let {
                            val oldBitmap = videoFrameCache[it]
                            if (oldBitmap != null && !oldBitmap.isRecycled) {
                                // 安全地回收旧bitmap以释放内存
                                oldBitmap.recycle()
                            }
                            videoFrameCache.remove(it)
                        }
                    }
                    // 添加新缓存
                    videoFrameCache[videoUrl] = bitmap
                }

                // 在主线程平滑过渡到视频帧
                Handler(Looper.getMainLooper()).post {
                    try {
                        // 再次检查bitmap是否有效且ImageView是否存在且ViewHolder未被回收
                        if (bitmap != null && !bitmap.isRecycled && !isRecycled.get() && coverImage.isAttachedToWindow) {
                            // 添加淡入动画使过渡更平滑
                            val fadeIn = AlphaAnimation(0.5f, 1.0f)
                            fadeIn.duration = 300
                            coverImage.startAnimation(fadeIn)
                            coverImage.setImageBitmap(bitmap)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 保存任务引用并启动
        currentVideoTask = videoTask
        videoTask.start()
    }

    /**
     * 回退方案：使用MediaMetadataRetriever
     */
    private fun fallbackToMediaMetadataRetriever(imageUrl: String) {
        extractThumbnailWithSmartRetriever(imageUrl)
    }

    // 安全释放MediaMetadataRetriever资源
    private fun releaseMediaRetriever() {
        try {
            currentMediaRetriever?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentMediaRetriever = null
        }
    }

    private fun setupInteractionButtons(item: FeedItem.ImageTextItem) {
        likeButton.setOnClickListener {
            item.liked = !item.liked

            if (item.liked) {
                item.likes += 1
            } else {
                item.likes = (item.likes - 1).coerceAtLeast(0)
            }

            likes.text = item.likes.toString()
            likeButton.setImageResource(
                if (item.liked) R.drawable.like_icon_red else R.drawable.like_icon
            )

            val anim = ScaleAnimation(
                0.8f, 1.0f, 0.8f, 1.0f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            )
            anim.duration = 150
            likeButton.startAnimation(anim)

            prefs.setLiked(item.id, item.liked)
            prefs.setLikes(item.id, item.likes)
        }
    }

    private fun setupClickListeners(item: FeedItem.ImageTextItem) {
        view.setOnClickListener {
            val intent = Intent(view.context, PostPageActivity::class.java).apply {
                putExtra("POST_ITEM", item)
            }
            view.context.startActivity(intent)
        }
    }
}
