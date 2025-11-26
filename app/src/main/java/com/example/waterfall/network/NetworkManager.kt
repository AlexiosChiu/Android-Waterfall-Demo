package com.example.waterfall.network

import android.util.Log
import com.example.waterfall.data.ResponseDTO
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class NetworkManager {
    // 单例模式
    companion object {
        @Volatile
        private var instance: NetworkManager? = null

        fun getInstance(): NetworkManager {
            return instance ?: synchronized(this) {
                instance ?: NetworkManager().also { instance = it }
            }
        }
    }

    private val client = OkHttpClient()
    private val gson = Gson()

    // 基础URL - 替换成你的实际URL
    private val baseUrl = "https://college-training-camp.bytedance.com/feed"

    /**
     * 获取帖子列表
     * @param count 请求的作品数量
     * @param accept_video 是否接受视频作品
     * @param callback 回调接口
     */
    fun getPostList(
        count: Int,
        accept_video: Boolean = true,
        callback: ApiCallback<ResponseDTO.ApiResponse>
    ) {
        // 构建URL，添加count参数
        val url = "$baseUrl/?count=$count&accept_video=$accept_video"
        Log.d("NetworkManager", "getPostList: $url")

        // 创建请求
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        // 异步执行请求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 网络请求失败
                callback.onFailure("网络请求失败: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        // 使用Gson解析JSON
                        val apiResponse =
                            gson.fromJson(responseBody, ResponseDTO.ApiResponse::class.java)

                        // 检查状态码
                        if (apiResponse.statusCode == 0) {
                            callback.onSuccess(apiResponse)
                        } else {
                            callback.onFailure("服务器返回错误: ${apiResponse.statusCode}")
                        }
                    } else {
                        callback.onFailure("请求失败，状态码: ${response.code}")
                    }
                } catch (e: Exception) {
                    callback.onFailure("数据解析失败: ${e.message}")
                }
            }
        })
    }


}


// 回调接口
interface ApiCallback<T> {
    fun onSuccess(data: T)
    fun onFailure(error: String)
}