package com.example.waterfall.holder

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
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

            // 加载封面图片
            Glide.with(coverImage.context)
                .load(item.coverImage)
                .into(coverImage)

            // 设置互动按钮
            setupInteractionButtons(item)

            // 设置整个卡片的点击事件
//            setupClickListeners(item)
        }
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