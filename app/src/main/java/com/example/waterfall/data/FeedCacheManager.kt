package com.example.waterfall.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedCacheManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val cacheType = object : TypeToken<List<FeedItem.ImageTextItem>>() {}.type

    suspend fun saveFeedItems(items: List<FeedItem.ImageTextItem>) {
        withContext(Dispatchers.IO) {
            val json = gson.toJson(items, cacheType)
            prefs.edit().putString(KEY_FEED_CACHE, json).apply()
        }
    }

    suspend fun loadFeedItems(): List<FeedItem.ImageTextItem> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_FEED_CACHE, null) ?: return@withContext emptyList()
        runCatching { gson.fromJson<List<FeedItem.ImageTextItem>>(json, cacheType) }
            .getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "home_feed_cache"
        private const val KEY_FEED_CACHE = "last_feed_items"
    }
}
