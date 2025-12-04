package com.example.waterfall

import android.app.Application
import android.content.Context
import com.bumptech.glide.Glide
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

class WaterfallApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 配置Glide以优化视频帧加载
        setupGlide()
    }

    private fun setupGlide() {
        val context = this

        // 使用MemorySizeCalculator计算合理的缓存大小
        val calculator = MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(2f)  // 内存缓存可以容纳2个屏幕的图片
            .build()

        // 配置GlideBuilder
        val glideBuilder = GlideBuilder()
            // 设置更合理的内存缓存和磁盘缓存
            .setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
            .setBitmapPool(LruBitmapPool(calculator.bitmapPoolSize.toLong()))
            .setDiskCache(
                InternalCacheDiskCacheFactory(
                    this,
                    "image_cache",
                    512 * 1024 * 1024  // 512MB 磁盘缓存
                )
            )
            // 为瀑布流布局配置更合理的线程池
            .setDiskCacheExecutor(
                GlideExecutor.newDiskCacheBuilder()
                    .setThreadCount(4)  // 增加磁盘缓存线程数
                    .build()
            )
            .setSourceExecutor(
                GlideExecutor.newSourceBuilder()
                    .setThreadCount(8)  // 增加网络/资源加载线程数
                    .build()
            )
            .setDefaultRequestOptions(createDefaultOptions())

        Glide.init(this, glideBuilder)
    }

    private fun createDefaultOptions(): RequestOptions {
        return RequestOptions()
            // 使用RGB_565格式以减少内存使用并提高解码速度
            .format(DecodeFormat.PREFER_RGB_565)
            // 只缓存转换后的资源，减少磁盘空间使用
            .diskCacheStrategy(DiskCacheStrategy.ALL)  // 使用ALL策略同时缓存源数据和转换后的数据
            // 优先从缓存加载
            .priority(Priority.HIGH)
            // 允许内存缓存
            .skipMemoryCache(false)
            // 预加载大小适配，提高瀑布流布局性能
            .disallowHardwareConfig()  // 避免硬件加速相关问题
            .dontAnimate()  // 默认禁用动画以提高性能
    }
}

// 为Glide启用注解处理，提供更好的性能
@GlideModule
class WaterfallGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)

        val calculator = MemorySizeCalculator.Builder(context)
            .setMemoryCacheScreens(2f)
            .build()

        builder
            .setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
            .setBitmapPool(LruBitmapPool(calculator.bitmapPoolSize.toLong()))
            .setDiskCacheExecutor(
                GlideExecutor.newDiskCacheBuilder()
                    .setThreadCount(4)
                    .build()
            )
            .setSourceExecutor(
                GlideExecutor.newSourceBuilder()
                    .setThreadCount(8)
                    .build()
            )
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false  // 禁用清单解析，提高启动性能
    }
}