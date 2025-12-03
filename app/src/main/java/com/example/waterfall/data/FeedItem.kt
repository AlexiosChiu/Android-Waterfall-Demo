package com.example.waterfall.data

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.Gson
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler


sealed class FeedItem {

    object HashTagParceler : Parceler<ResponseDTO.HashTag> {
        private val gson = Gson()
        override fun create(parcel: Parcel): ResponseDTO.HashTag {
            val json = parcel.readString() ?: "{}"
            return gson.fromJson(json, ResponseDTO.HashTag::class.java)
        }

        override fun ResponseDTO.HashTag.write(parcel: Parcel, flags: Int) {
            parcel.writeString(gson.toJson(this))
        }
    }

    @Parcelize
    @TypeParceler<ResponseDTO.HashTag, HashTagParceler>()
    data class ImageTextItem(
        val id: String,
        val avatar: String,      // 作者头像
        val authorName: String,  // 作者名
        val title: String?,       // 标题
        val content: String,     // 内容
        val clips: List<String>, // clip列表
        val coverClip: String, // 封面clip地址
        val coverHeight: Int,    // 封面高度
        val coverWidth: Int,     // 封面宽度
        var likes: Int,          // 点赞数
        var liked: Boolean,      // 是否点赞
        val createTime: Long,     // 创建时间
        val hashTags: List<ResponseDTO.HashTag>?    // 话题标签
    ) : FeedItem(), Parcelable

}