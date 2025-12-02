package com.example.waterfall.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class LikePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("likes_prefs", Context.MODE_PRIVATE)

    private fun likedKey(id: String) = "liked_$id"
    private fun countKey(id: String) = "count_$id"

    fun getLiked(id: String, default: Boolean = false): Boolean {
        return prefs.getBoolean(likedKey(id), default)
    }

    fun setLiked(id: String, liked: Boolean) {
        prefs.edit { putBoolean(likedKey(id), liked) }
    }

    fun getLikes(id: String, default: Int = 0): Int {
        return prefs.getInt(countKey(id), default)
    }

    fun setLikes(id: String, count: Int) {
        prefs.edit { putInt(countKey(id), count) }
    }
}
