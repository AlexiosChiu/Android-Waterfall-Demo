package com.alexioschiu.waterfall.holder

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.alexioschiu.waterfall.R
import com.alexioschiu.waterfall.activity.PostPageActivity
import com.alexioschiu.waterfall.adapter.FeedAdapter
import com.alexioschiu.waterfall.data.FeedItem
import com.alexioschiu.waterfall.data.LikePreferences
import com.alexioschiu.waterfall.databinding.ImageTextItemLayoutBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class ImageTextViewHolder(
    private val binding: ImageTextItemLayoutBinding, private val adapter: FeedAdapter
) : ItemViewHolder(binding.root) {

    private val avatar = binding.avatar
    private val authorName = binding.authorName
    private val title = binding.postTitle
    private val likes = binding.likes
    private val coverImage = binding.coverImage
    private val likeButton = binding.likeButton
    private lateinit var prefs: LikePreferences

    private val isRecycled = AtomicBoolean(false)
    private var currentThumbnailExtractionTask: Job? = null
    private var currentExoPlayer: ExoPlayer? = null

    // 统一追踪缩略图任务，便于在 recycle 中一次性取消
    private val thumbnailSupervisor = SupervisorJob()
    private val thumbnailScope = CoroutineScope(thumbnailSupervisor + Dispatchers.IO)
    private var currentRetrieverJob: Job? = null

    @Volatile
    private var currentCoverKey: String? = null

    companion object {
        private const val MAX_CACHE_SIZE = 128 * 1024 * 1024
        private val videoFrameCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun bind(item: FeedItem) {
        recycle()
        isRecycled.set(false)

        if (item is FeedItem.ImageTextItem) {
            currentCoverKey = item.coverClip
            prefs = LikePreferences(binding.root.context)
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

    fun recycle() {
        currentCoverKey = null
        isRecycled.set(true)
        coverImage.setImageDrawable(null)

        // 取消协程、防止泄漏
        currentThumbnailExtractionTask?.cancel()
        currentThumbnailExtractionTask = null
        thumbnailSupervisor.cancelChildren()
        currentRetrieverJob?.cancel()
        currentRetrieverJob = null

        currentExoPlayer?.release()
        currentExoPlayer = null
        Glide.with(coverImage).clear(coverImage)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadImage(imageUrl: String) {
        currentCoverKey = imageUrl
        coverImage.setImageDrawable(null)

        fun isVideo(url: String): Boolean {
            val lower = url.lowercase()
            return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8")
        }

        if (!isVideo(imageUrl)) {
            Glide.with(coverImage).clear(coverImage)
            val targetWidth =
                coverImage.layoutParams?.width?.takeIf { it > 0 } ?: adapter.getColumnWidth()
            val targetHeight = coverImage.layoutParams?.height?.takeIf { it > 0 } ?: targetWidth
            val lowQualityRequest =
                Glide.with(coverImage.context).load(imageUrl).override(10).dontAnimate()

            Glide.with(coverImage.context).load(imageUrl).thumbnail(lowQualityRequest)
                .override(targetWidth, targetHeight).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate().into(coverImage)
            return
        }

        extractThumbnailWithExoPlayer(imageUrl)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
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

        currentThumbnailExtractionTask = CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isRecycled.get() || currentCoverKey != requestKey) return@launch

                val exoPlayer = ExoPlayer.Builder(binding.root.context).build()
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

    private suspend fun ExoPlayer.awaitReadyState(): Boolean = suspendCancellableCoroutine { cont ->
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun extractThumbnailWithSmartRetriever(videoUrl: String, requestKey: String?) {
        if (requestKey == null) return
        currentRetrieverJob?.cancel()
        currentRetrieverJob = thumbnailScope.launch {
            val bitmap = runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(videoUrl, emptyMap())
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { original ->
                            val targetWidth = (adapter.getColumnWidth() * 0.5f).coerceAtLeast(50f)
                            val scaleFactor = targetWidth / original.width
                            original.scale(
                                targetWidth.toInt(),
                                (original.height * scaleFactor).toInt().coerceAtLeast(30)
                            )
                        }
                }
            }.getOrNull()

            if (bitmap != null && !bitmap.isRecycled && !isRecycled.get() && currentCoverKey == requestKey) {
                synchronized(videoFrameCache) { videoFrameCache.put(videoUrl, bitmap) }
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun fallbackToMediaMetadataRetriever(imageUrl: String, requestKey: String?) {
        extractThumbnailWithSmartRetriever(imageUrl, requestKey)
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
                0.8f,
                1.0f,
                0.8f,
                1.0f,
                ScaleAnimation.RELATIVE_TO_SELF,
                0.5f,
                ScaleAnimation.RELATIVE_TO_SELF,
                0.5f
            )
            anim.duration = 150
            likeButton.startAnimation(anim)
            prefs.setLiked(item.id, item.liked)
            prefs.setLikes(item.id, item.likes)
        }
    }

    private fun setupClickListeners(item: FeedItem.ImageTextItem) {
        binding.root.setOnClickListener {
            val intent = Intent(binding.root.context, PostPageActivity::class.java).apply {
                putExtra("POST_ITEM", item)
            }
            binding.root.context.startActivity(intent)
        }
    }
}