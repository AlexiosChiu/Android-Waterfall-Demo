package com.example.waterfall.holder

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.example.waterfall.R
import com.example.waterfall.data.FeedItem

class ImageTextViewHolder(private val view: View) : ItemViewHolder(view) {
    private val avatar: ImageView = view.findViewById(R.id.avatar)
    private val authorName: TextView = view.findViewById(R.id.author_name)
    private val title: TextView = view.findViewById(R.id.post_title)

    private val likes: TextView = view.findViewById(R.id.likes)
    private val coverImage: ImageView = view.findViewById(R.id.cover_image)

    private val likeButton: ImageButton = view.findViewById(R.id.likeButton)


    override fun bind(item: FeedItem) {
        if (item is FeedItem.ImageTextItem) {
            // 设置基本信息
            authorName.text = item.authorName
            title.text = item.title + "，" + item.content
            likes.text = item.likes.toString()


            // 加载头像
            Glide.with(avatar.context)
                .load(item.avatar)
                .circleCrop()
                .into(avatar)

            // 动态设置图片高度
            val (columnWidth, targetHeight) = calculateOptimalSize(
                item.coverWidth,
                item.coverHeight
            )

            // 设置 ImageView 尺寸
            coverImage.layoutParams = coverImage.layoutParams.apply {
                width = columnWidth
                height = targetHeight
            }

            // 加载图片
            loadImageWithOptimalCrop(item.coverImage, columnWidth, targetHeight)

            // 设置互动按钮
            setupInteractionButtons(item)

            // 设置整个卡片的点击事件
//            setupClickListeners(item)
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

        // 限制宽高比在 0.75 (3:4) 到 1.333 (4:3) 之间
        val constrainedAspectRatio = originalAspectRatio.coerceIn(0.75f, 1.333f)
        val targetHeight = (columnWidth * constrainedAspectRatio).toInt()

        return Pair(columnWidth, targetHeight)
    }

    private fun loadImageWithOptimalCrop(imageUrl: String, width: Int, height: Int) {
        Glide.with(coverImage.context)
            .load(imageUrl)
            .override(width, height)  // 指定目标尺寸
            .transform(CenterCrop())  // 居中裁剪
            .into(coverImage)
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun setupInteractionButtons(item: FeedItem.ImageTextItem) {


        likeButton.setOnClickListener {
            // 处理点赞逻辑
            Toast.makeText(view.context, "点赞了: ${item.title}", Toast.LENGTH_SHORT).show()
        }


    }

//    private fun setupClickListeners(item: FeedItem.ImageTextItem) {
//        // 整个卡片点击跳转到详情页
//        view.setOnClickListener {
//            val intent = Intent(view.context, PostDetailActivity::class.java).apply {
//                putExtra("POST_ID", item.id)
//            }
//            view.context.startActivity(intent)
//        }
//
//        // 封面图片点击也可以跳转
//        coverImage.setOnClickListener {
//            val intent = Intent(view.context, PostDetailActivity::class.java).apply {
//                putExtra("POST_ID", item.id)
//            }
//            view.context.startActivity(intent)
//        }
//    }

}