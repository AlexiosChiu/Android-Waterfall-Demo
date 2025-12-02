package com.example.waterfall.holder

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions
import com.example.waterfall.R
import com.example.waterfall.activity.PostPageActivity
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.LikePreferences

class ImageTextViewHolder(private val view: View) : ItemViewHolder(view) {
    private val avatar: ImageView = view.findViewById(R.id.avatar)
    private val authorName: TextView = view.findViewById(R.id.author_name)
    private val title: TextView = view.findViewById(R.id.post_title)

    private val likes: TextView = view.findViewById(R.id.likes)
    private val coverImage: ImageView = view.findViewById(R.id.cover_image)

    private val likeButton: ImageButton = view.findViewById(R.id.likeButton)
    private lateinit var prefs: LikePreferences

    override fun bind(item: FeedItem) {
        if (item is FeedItem.ImageTextItem) {
            // 初始化 prefs
            prefs = LikePreferences(view.context)

            // 从持久化读取（以 item 中的值为默认）
            val persistedLikes = prefs.getLikes(item.id, item.likes)
            val persistedLiked = prefs.getLiked(item.id, item.liked)

            // 更新数据模型与界面
            item.likes = persistedLikes
            item.liked = persistedLiked

            authorName.text = item.authorName
            title.text = item.title ?: item.content
            likes.text = item.likes.toString()

            likeButton.setImageResource(
                if (item.liked) R.drawable.like_icon_red else R.drawable.like_icon
            )

            // 加载头像
            Glide.with(avatar.context).load(item.avatar).circleCrop().into(avatar)

            // 动态设置图片高度
            val (columnWidth, targetHeight) = calculateOptimalSize(
                item.coverWidth, item.coverHeight
            )

            coverImage.layoutParams = coverImage.layoutParams.apply {
                width = columnWidth
                height = targetHeight
            }

            loadImageWithOptimalCrop(item.coverImage, columnWidth, targetHeight, item)

            setupInteractionButtons(item)
            setupClickListeners(item)
        }
    }

    private fun calculateOptimalSize(originalWidth: Int, originalHeight: Int): Pair<Int, Int> {
        val recyclerView = view.parent as? RecyclerView
        val recyclerWidth =
            recyclerView?.width ?: coverImage.context.resources.displayMetrics.widthPixels

        val margin = 12.dpToPx(coverImage.context)
        val columnWidth = (recyclerWidth - margin) / 2

        val originalAspectRatio = if (originalWidth > 0 && originalHeight > 0) {
            originalHeight.toFloat() / originalWidth.toFloat()
        } else {
            1.0f
        }

        val constrainedAspectRatio = originalAspectRatio.coerceIn(0.75f, 1.333f)
        val targetHeight = (columnWidth * constrainedAspectRatio).toInt()

        return Pair(columnWidth, targetHeight)
    }

    private fun loadImageWithOptimalCrop(imageUrl: String, width: Int, height: Int, item: FeedItem.ImageTextItem) {
        Glide.with(coverImage.context)
            .load(imageUrl)
            .apply(RequestOptions().override(width, height))
            .transform(CenterCrop())
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    // 图片加载完成后，重新设置ImageView的高度并通知RecyclerView更新
                    resource?.let { drawable ->
                        val drawableWidth = drawable.intrinsicWidth
                        val drawableHeight = drawable.intrinsicHeight

                        if (drawableWidth > 0 && drawableHeight > 0) {
                            val aspectRatio = drawableHeight.toFloat() / drawableWidth.toFloat()
                            val newHeight = (width * aspectRatio).toInt()

                            // 更新ImageView的高度
                            coverImage.layoutParams.height = newHeight
                            coverImage.requestLayout()

                            // 通知StaggeredGridLayoutManager重新测量和布局
                            view.post {
                                val recyclerView = view.parent as? RecyclerView
                                recyclerView?.let { rv ->
                                    val position = rv.getChildAdapterPosition(view)
                                    if (position != RecyclerView.NO_POSITION) {
                                        val layoutParams = view.layoutParams as? StaggeredGridLayoutManager.LayoutParams
                                        layoutParams?.isFullSpan = false
                                        view.layoutParams = layoutParams

                                        // 强制刷新该item的布局
                                        rv.adapter?.notifyItemChanged(position)
                                    }
                                }
                            }
                        }
                    }
                    return false
                }
            })
            .into(coverImage)
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun setupInteractionButtons(item: FeedItem.ImageTextItem) {
        likeButton.setOnClickListener {
            // 切换状态
            item.liked = !item.liked

            if (item.liked) {
                item.likes = (item.likes + 1)
            } else {
                item.likes = (item.likes - 1).coerceAtLeast(0)
            }

            // 更新界面
            likes.text = item.likes.toString()
            likeButton.setImageResource(
                if (item.liked) R.drawable.like_icon_red else R.drawable.like_icon
            )

            // 简单点赞动画
            val anim = ScaleAnimation(
                0.8f, 1.0f, 0.8f, 1.0f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            )
            anim.duration = 150
            likeButton.startAnimation(anim)

            // 持久化本地状态
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