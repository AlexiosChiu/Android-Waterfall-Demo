package com.example.waterfall.holder

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
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
import androidx.media3.common.util.UnstableApi
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
import java.util.concurrent.atomic.AtomicBoolean

class ImageTextViewHolder(private val view: View, private val adapter: FeedAdapter) :
    ItemViewHolder(view) {
    @Volatile
    private var currentCoverKey: String? = null
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
        private const val MAX_CACHE_SIZE = 128 * 1024 * 1024 // 128MB缓存大小

        // 使用LruCache自动管理内存
        private val videoFrameCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                // 返回bitmap占用的字节数
                return value.byteCount
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: Bitmap,
                newValue: Bitmap?
            ) {
                // 当缓存项被移除时，不需要手动回收，LruCache会自动处理
            }
        }
    }

    override fun bind(item: FeedItem) {
        // 立即取消所有之前的异步任务
        recycle()
        // 重置回收标志
        isRecycled.set(false)

        if (item is FeedItem.ImageTextItem) {
            currentCoverKey = item.coverClip
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
        currentCoverKey = null
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
        currentCoverKey = imageUrl
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
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun extractThumbnailWithExoPlayer(videoUrl: String) {
        val requestKey = currentCoverKey ?: return
        if (isRecycled.get() || videoUrl != requestKey) return

        val cachedBitmap = videoFrameCache.get(videoUrl)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            if (!isRecycled.get() && videoUrl == currentCoverKey) {
                coverImage.setImageBitmap(cachedBitmap)
            }
            return
        } else if (cachedBitmap != null) {
            videoFrameCache.remove(videoUrl)
        }

        currentThumbnailExtractionTask = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isRecycled.get() || currentCoverKey != requestKey) return@launch

                val exoPlayer = ExoPlayer.Builder(view.context).build()
                currentExoPlayer = exoPlayer

                val mediaItem = MediaItem.fromUri(videoUrl)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()

                var retryCount = 0
                while (
                    exoPlayer.playbackState != Player.STATE_READY &&
                    retryCount < 10 &&
                    !isRecycled.get() &&
                    currentCoverKey == requestKey
                ) {
                    kotlinx.coroutines.delay(100)
                    retryCount++
                }

                if (exoPlayer.playbackState != Player.STATE_READY ||
                    isRecycled.get() ||
                    currentCoverKey != requestKey
                ) {
                    exoPlayer.release()
                    if (currentExoPlayer === exoPlayer) currentExoPlayer = null
                    return@launch
                }

                val videoFormat = exoPlayer.videoFormat
                if (videoFormat != null) {
                    extractThumbnailWithSmartRetriever(videoUrl, requestKey)
                } else {
                    fallbackToMediaMetadataRetriever(videoUrl, requestKey)
                }

                exoPlayer.release()
                if (currentExoPlayer === exoPlayer) currentExoPlayer = null

            } catch (e: Exception) {
                e.printStackTrace()
                if (!isRecycled.get() && currentCoverKey == requestKey) {
                    fallbackToMediaMetadataRetriever(videoUrl, requestKey)
                }
            }
        }
    }

    /**
     * 智能的MediaMetadataRetriever方案 - 优化性能
     */
    private fun extractThumbnailWithSmartRetriever(videoUrl: String, requestKey: String?) {
        // 在后台线程使用MediaMetadataRetriever提取视频帧
        if (requestKey == null) return

        val videoTask = Thread {
            var bitmap: Bitmap? = null

            try {
                if (isRecycled.get() || currentCoverKey != requestKey) return@Thread

                val retriever = MediaMetadataRetriever()
                currentMediaRetriever = retriever

                try {
                    retriever.setDataSource(videoUrl, HashMap())
                } catch (_: Exception) {
                    retriever.setDataSource(videoUrl)
                }

                if (isRecycled.get() || currentCoverKey != requestKey) {
                    releaseMediaRetriever()
                    return@Thread
                }

                bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (bitmap != null && !isRecycled.get() && currentCoverKey == requestKey) {
                    val targetWidth = (adapter.getColumnWidth() * 0.5f).coerceAtLeast(50f)
                    val scaleFactor = targetWidth / bitmap.width
                    bitmap = bitmap.scale(
                        targetWidth.toInt(),
                        (bitmap.height * scaleFactor).toInt().coerceAtLeast(30)
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                bitmap = null
            } finally {
                releaseMediaRetriever()
            }

            if (bitmap != null &&
                !bitmap.isRecycled &&
                !isRecycled.get() &&
                currentCoverKey == requestKey
            ) {
                synchronized(videoFrameCache) {
                    videoFrameCache.put(videoUrl, bitmap)
                }

                Handler(Looper.getMainLooper()).post {
                    if (
                        !bitmap.isRecycled && !isRecycled.get() && coverImage.isAttachedToWindow && currentCoverKey == requestKey
                    ) {
                        val fadeIn = AlphaAnimation(0.5f, 1.0f)
                        fadeIn.duration = 300
                        coverImage.startAnimation(fadeIn)
                        coverImage.setImageBitmap(bitmap)
                    }
                }
            }
        }

        currentVideoTask = videoTask
        videoTask.start()
    }

    /**
     * 回退方案：使用MediaMetadataRetriever
     */
    private fun fallbackToMediaMetadataRetriever(imageUrl: String, requestKey: String?) {
        extractThumbnailWithSmartRetriever(imageUrl, requestKey)
    }

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
            item.likes = if (item.liked) item.likes + 1 else (item.likes - 1).coerceAtLeast(0)

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
