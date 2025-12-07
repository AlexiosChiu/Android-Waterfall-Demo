package com.alexioschiu.waterfall

import android.app.Application
import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Priority
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

class WaterfallApplication : Application()

@GlideModule
class WaterfallGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // 使用 Glide 的内置计算器估算最佳内存缓存与 Bitmap 池大小
        val calculator = MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(2f)
            .build()

        // 依据可用核心数动态计算线程池大小，至少保证 4 条线程
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        // 磁盘线程取一半并保证不少于 2
        val diskThreads = (cores / 2).coerceAtLeast(2)

        builder
            // 配置内存缓存大小
            .setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
            // 配置 Bitmap 池以复用像素缓冲
            .setBitmapPool(LruBitmapPool(calculator.bitmapPoolSize.toLong()))
            // 将磁盘缓存放在内部存储，并限制为 512 MB
            .setDiskCache(InternalCacheDiskCacheFactory(context, "image_cache", 512 * 1024 * 1024))
            // 自定义磁盘任务线程池
            .setDiskCacheExecutor(
                GlideExecutor.newDiskCacheBuilder()
                    .setThreadCount(diskThreads)
                    .build()
            )
            // 自定义网络/解码线程池
            .setSourceExecutor(
                GlideExecutor.newSourceBuilder()
                    .setThreadCount(cores)
                    .build()
            )
            // 设置全局默认请求参数
            .setDefaultRequestOptions(createDefaultOptions())
    }

    // 全局默认的 RequestOptions，统一图片格式、缓存策略与优先级
    private fun createDefaultOptions(): RequestOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .priority(Priority.HIGH)
        .skipMemoryCache(false)
        .disallowHardwareConfig()
        .dontAnimate()

    // 关闭 Manifest 自动解析，避免重复注册
    override fun isManifestParsingEnabled(): Boolean = false
}