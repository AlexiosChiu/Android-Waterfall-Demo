package com.example.waterfall.holder

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import androidx.media3.common.PlaybackException
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

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

            val targetWidth =
                coverImage.layoutParams?.width?.takeIf { it > 0 } ?: adapter.getColumnWidth()
            val targetHeight = coverImage.layoutParams?.height?.takeIf { it > 0 } ?: targetWidth

            // 快速加载模糊的低分辨率版本
            val lowQualityRequest = Glide.with(coverImage.context)
                .load(imageUrl)
                .override(10) // 超低分辨率预览
                .dontAnimate()

            Glide.with(coverImage.context)
                .load(imageUrl)
                .thumbnail(lowQualityRequest)
                .override(targetWidth, targetHeight)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
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
            if (!isRecycled.get() && videoUrl == currentCoverKey) coverImage.setImageBitmap(
                cachedBitmap
            )
            return
        } else if (cachedBitmap != null) {
            videoFrameCache.remove(videoUrl)
        }

        currentThumbnailExtractionTask = CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isRecycled.get() || currentCoverKey != requestKey) return@launch

                val exoPlayer = ExoPlayer.Builder(view.context).build()
                currentExoPlayer = exoPlayer

                exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
                exoPlayer.prepare()

                val isReady = withTimeoutOrNull(1_500L) { exoPlayer.awaitReadyState() } ?: false
                if (!isReady || isRecycled.get() || currentCoverKey != requestKey) {
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
            } catch (_: Exception) {
                if (!isRecycled.get() && currentCoverKey == requestKey) {
                    fallbackToMediaMetadataRetriever(videoUrl, requestKey)
                }
            }
        }
    }

    private suspend fun ExoPlayer.awaitReadyState(): Boolean =
        suspendCancellableCoroutine { cont ->
            if (playbackState == Player.STATE_READY) {
                cont.resume(true)
                return@suspendCancellableCoroutine
            }

            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && cont.isActive) {
                        removeListener(this)
                        cont.resume(true)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (cont.isActive) {
                        removeListener(this)
                        cont.resume(false)
                    }
                }
            }

            addListener(listener)
            cont.invokeOnCancellation { removeListener(listener) }
        }

    /**
     * 智能的MediaMetadataRetriever方案 - 优化性能
     */
    private val thumbnailScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentRetrieverJob: Job? = null

    private fun extractThumbnailWithSmartRetriever(videoUrl: String, requestKey: String?) {
        if (requestKey == null) return
        currentRetrieverJob?.cancel()
        currentRetrieverJob = thumbnailScope.launch {
            var bitmap: Bitmap? = null
            try {
                if (isRecycled.get() || currentCoverKey != requestKey) return@launch
                val retriever = MediaMetadataRetriever()
                currentMediaRetriever = retriever
                retriever.setDataSource(videoUrl, HashMap())
                if (isRecycled.get() || currentCoverKey != requestKey) return@launch
                bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { original ->
                        val targetWidth = (adapter.getColumnWidth() * 0.5f).coerceAtLeast(50f)
                        val scaleFactor = targetWidth / original.width
                        original.scale(
                            targetWidth.toInt(),
                            (original.height * scaleFactor).toInt().coerceAtLeast(30)
                        )
                    }
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
                withContext(Dispatchers.Main) {
                    if (!bitmap.isRecycled && coverImage.isAttachedToWindow && currentCoverKey == requestKey) {
                        val fadeIn = AlphaAnimation(0.5f, 1.0f).apply { duration = 300 }
                        coverImage.startAnimation(fadeIn)
                        coverImage.setImageBitmap(bitmap)
                    }
                }
            }
        }
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
