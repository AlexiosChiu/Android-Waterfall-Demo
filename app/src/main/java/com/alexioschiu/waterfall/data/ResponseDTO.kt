package com.alexioschiu.waterfall.data

import com.google.gson.annotations.SerializedName

sealed class ResponseDTO {
    // 响应信息
    data class ApiResponse(
        @SerializedName("status_code") val statusCode: Int,
        @SerializedName("has_more") val hasMore: Int,
        @SerializedName("post_list") val postList: List<Post>
    )

    // 帖子数据
    data class Post(
        @SerializedName("post_id") val postId: String,
        @SerializedName("title") val title: String,
        @SerializedName("content") val content: String,
        @SerializedName("hashtag") val hashtag: List<HashTag>?,
        @SerializedName("create_time") val createTime: Long,
        @SerializedName("author") val author: Author,
        @SerializedName("clips") val clips: List<Clip>?,
        @SerializedName("music") val music: Music
    )

    // 话题标签
    data class HashTag(
        @SerializedName("start") val start: Int,
        @SerializedName("end") val end: Int
    )

    // 作者信息
    data class Author(
        @SerializedName("user_id") val userId: String,
        @SerializedName("nickname") val nickname: String,
        @SerializedName("avatar") val avatar: String
    )

    // 媒体（图片/视频）
    data class Clip(
        @SerializedName("type") val type: Int, // 0:图片, 1:视频
        @SerializedName("width") val width: Int,
        @SerializedName("height") val height: Int,
        @SerializedName("url") val url: String
    )

    // 音乐
    data class Music(
        @SerializedName("volume") val volume: Int,
        @SerializedName("seek_time") val seekTime: Int,
        @SerializedName("url") val url: String
    )

}