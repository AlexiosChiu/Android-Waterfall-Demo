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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.waterfall.R
import com.example.waterfall.activity.PostPageActivity
import com.example.waterfall.adapter.FeedAdapter
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.LikePreferences
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

    // 视频帧缓存
    companion object {
        // 使用LRU缓存更好，但为简化使用ConcurrentHashMap
        private val videoFrameCache = ConcurrentHashMap<String, Bitmap>()
        private const val MAX_CACHE_SIZE = 32 // 限制缓存大小
    }

    override fun bind(item: FeedItem) {
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

        // 取消当前正在执行的视频帧提取任务
        if (currentVideoTask != null && currentVideoTask!!.isAlive) {
            try {
                // 通过标志让线程自行退出
                releaseMediaRetriever()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 取消Glide加载
        Glide.with(coverImage).clear(coverImage)

        // 释放其他资源
        currentVideoTask = null
    }

    private fun loadImage(imageUrl: String) {
        // 检查是否为视频URL
        fun isVideo(url: String): Boolean {
            val lower = url.lowercase()
            return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8")
        }

        // 对于图片URL，使用Glide加载较低分辨率的版本（之前已经修改过）
        if (!isVideo(imageUrl)) {
            // 快速加载模糊的低分辨率版本
            val lowQualityRequest = Glide.with(coverImage.context)
                .load(imageUrl)
                .override(20) // 超低分辨率预览
                .centerCrop()
                .dontAnimate()

            // 然后加载优化后的中等分辨率版本
            val targetWidth = adapter.getColumnWidth() * 0.8f
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

        // 对于视频URL：
        // 1. 首先检查缓存中是否已有该视频的帧
        if (videoFrameCache.containsKey(imageUrl)) {
            val cachedBitmap = videoFrameCache[imageUrl]
            if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                coverImage.setImageBitmap(cachedBitmap)
                return // 使用缓存的帧，不再进行加载
            } else {
                // 缓存中的bitmap已被回收，移除缓存
                videoFrameCache.remove(imageUrl)
            }
        }


        // 2. 在后台线程使用MediaMetadataRetriever提取视频帧
        val videoTask = Thread {
            var bitmap: Bitmap? = null

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
                    retriever.setDataSource(imageUrl, HashMap<String, String>())
                } catch (e: Exception) {
                    // 如果带headers的方法失败，尝试简单设置
                    retriever.setDataSource(imageUrl)
                }

                // 再次检查是否已被回收
                if (isRecycled.get()) {
                    releaseMediaRetriever()
                    return@Thread
                }

                // 获取第0毫秒的帧
                bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                // 如果第一帧获取失败，尝试其他时间点
                if (bitmap == null && !isRecycled.get()) {
                    bitmap = retriever.getFrameAtTime(1000) // 尝试第一秒
                }

                // 对获取到的bitmap进行缩放处理
                if (bitmap != null && !isRecycled.get()) {
                    // 计算目标尺寸（使用80%的列宽）
                    val targetWidth = adapter.getColumnWidth() * 0.8f

                    // 计算缩放比例，确保图片按比例缩放
                    val scaleFactor = targetWidth / bitmap.width

                    // 创建缩放后的bitmap
                    bitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        targetWidth.toInt(),
                        (bitmap.height * scaleFactor).toInt(),
                        true // 使用双线性过滤使图片更平滑
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
                    videoFrameCache[imageUrl] = bitmap
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
                        // 失败时保持占位图不变
                    }
                }
            }
        }

        // 保存任务引用并启动
        currentVideoTask = videoTask
        videoTask.start()
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
