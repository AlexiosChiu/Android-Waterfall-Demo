package com.example.waterfall.holder

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.waterfall.R
import com.example.waterfall.data.FeedItem

class VideoViewHolder(private val view: View) : ItemViewHolder(view) {

    private val avatar: ImageView = view.findViewById(R.id.avatar)
    private val authorName: TextView = view.findViewById(R.id.author_name)
    private val title: TextView = view.findViewById(R.id.post_title)
    private val videoCover: ImageView = view.findViewById(R.id.cover_image)
//    private val duration: TextView = view.findViewById(R.id.tv_duration)
//    private val playButton: ImageButton = view.findViewById(R.id.btn_play)

    override fun bind(item: FeedItem) {
        if (item is FeedItem.VideoItem) {
            authorName.text = item.authorName
            title.text = item.content
//            duration.text = item.duration

            // 加载头像和视频封面
            Glide.with(avatar.context)
                .load(item.avatar)
                .circleCrop()
                .into(avatar)

            Glide.with(videoCover.context)
                .load(item.coverImage)
                .into(videoCover)


        }
    }


}