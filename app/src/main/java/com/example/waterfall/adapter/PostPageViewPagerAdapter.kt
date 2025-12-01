package com.example.waterfall.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.waterfall.R

class PostPageViewPagerAdapter(
    private val images: List<String>,
    private val onMaxImageSizeMeasured: (maxHeight: Int) -> Unit
) :
    RecyclerView.Adapter<PostPageViewPagerAdapter.ImageViewHolder>() {

    private var maxHeight = 0
    private var loadedCount = 0
    private val imageSizes = mutableMapOf<Int, Pair<Int, Int>>()

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view_pager_item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_pager_image_item, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val currentPosition = holder.adapterPosition
        if (currentPosition == RecyclerView.NO_POSITION) return
        
        Glide.with(holder.itemView.context)
            .load(images[currentPosition])
            .apply(RequestOptions().override(1000, 1000)) // 限制最大尺寸
            .listener(object :
                com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    loadedCount++
                    checkAllImagesLoaded()
                    return false
                }

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    val adapterPosition = holder.adapterPosition
                    if (adapterPosition == RecyclerView.NO_POSITION) return false
                    
                    resource?.let { drawable ->
                        val width = drawable.intrinsicWidth
                        val height = drawable.intrinsicHeight
                        if (width > 0 && height > 0) {
                            imageSizes[adapterPosition] = Pair(width, height)
                            
                            // 计算基于屏幕宽度的等比高度
                            val displayMetrics = holder.itemView.context.resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val aspectRatio = height.toFloat() / width.toFloat()
                            val calculatedHeight = (screenWidth * aspectRatio).toInt()
                            
                            // 更新最大高度
                            if (calculatedHeight > maxHeight) {
                                maxHeight = calculatedHeight
                            }
                        }
                    }
                    loadedCount++
                    checkAllImagesLoaded()
                    return false
                }
            })
            .into(holder.imageView)
    }

    private fun checkAllImagesLoaded() {
        if (loadedCount >= images.size) {
            // 所有图片加载完成，通知最大高度
            onMaxImageSizeMeasured(maxHeight)
        }
    }

    override fun getItemCount(): Int = images.size
}