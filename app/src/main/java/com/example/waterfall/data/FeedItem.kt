package com.example.waterfall.data

sealed class FeedItem {

    data class ImageTextItem(
        val id: String,
        val avatar: String,      // 作者头像
        val authorName: String,  // 作者名
        val title: String,       // 标题
        val content: String,     // 内容
        val coverImage: String, // 图片地址
        val likes: Int,          // 点赞数
        val comments: Int        // 评论数
    ) : FeedItem()

    data class VideoItem(
        val id: String,
        val avatar: String,
        val authorName: String,
        val title: String,
        val content: String,
        val videoUrl: String,    // 视频地址
        val coverImage: String,  // 视频封面
        val likes: Int,
        val comments: Int,
        val duration: String     // 视频时长
    ) : FeedItem()

}