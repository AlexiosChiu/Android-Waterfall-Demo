package com.example.waterfall.holder

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.waterfall.R
import com.example.waterfall.activity.PostPageActivity
import com.example.waterfall.adapter.FeedAdapter
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.LikePreferences

class ImageTextViewHolder(private val view: View, private val adapter: FeedAdapter) :
    ItemViewHolder(view) {
    private val avatar: ImageView = view.findViewById(R.id.avatar)
    private val authorName: TextView = view.findViewById(R.id.author_name)
    private val title: TextView = view.findViewById(R.id.post_title)
    private val likes: TextView = view.findViewById(R.id.likes)
    private val coverImage: ImageView = view.findViewById(R.id.cover_image)
    private val likeButton: ImageButton = view.findViewById(R.id.likeButton)
    private lateinit var prefs: LikePreferences

    override fun bind(item: FeedItem) {
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

    private fun loadImage(imageUrl: String) {
        if (imageUrl.isBlank()) {
            coverImage.setImageDrawable(null)
            coverImage.setBackgroundColor(
                ContextCompat.getColor(coverImage.context, android.R.color.white)
            )
            return
        }

        val placeholderColor = ContextCompat.getColor(coverImage.context, android.R.color.white)
        val placeholder = placeholderColor.toDrawable()

        val requestOptions = RequestOptions()
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(placeholder)
            .error(placeholder)

        Glide.with(coverImage.context)
            .load(imageUrl)
            .apply(requestOptions)
            .into(coverImage)
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
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