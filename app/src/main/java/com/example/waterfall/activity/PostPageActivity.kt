        package com.example.waterfall.activity

        import android.os.Bundle
        import android.util.DisplayMetrics
        import android.view.animation.ScaleAnimation
        import android.widget.ImageView
        import android.widget.TextView
        import androidx.activity.viewModels
        import androidx.appcompat.app.AppCompatActivity
        import androidx.lifecycle.lifecycleScope
        import com.bumptech.glide.Glide
        import com.example.waterfall.R
        import com.example.waterfall.adapter.PostPageViewPagerAdapter
        import com.example.waterfall.data.FeedItem
        import com.example.waterfall.view_model.PostPageUiState
        import com.example.waterfall.view_model.PostPageViewModel
        import kotlinx.coroutines.launch

        class PostPageActivity : AppCompatActivity() {

            private val viewModel: PostPageViewModel by viewModels()

            private lateinit var postItem: FeedItem.ImageTextItem
            private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2

            // UI组件
            private lateinit var backButton: ImageView
            private lateinit var avatar: ImageView
            private lateinit var authorName: TextView
            private lateinit var title: TextView
            private lateinit var content: TextView
            private lateinit var postTime: TextView
            private lateinit var likes: TextView
            private lateinit var likeButton: ImageView

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.post_page_layout)
                postItem = intent.getParcelableExtra("POST_ITEM") ?: return
                initViews()
                setupObservers()
                setupBackButton()

                viewModel.setPostData(postItem)
            }

            private fun initViews() {
                backButton = findViewById(R.id.return_icon)
                avatar = findViewById(R.id.avatar_post_page)
                authorName = findViewById(R.id.author_name_post_page)
                title = findViewById(R.id.title_post_page)
                content = findViewById(R.id.content_post_page)
                postTime = findViewById(R.id.post_time_post_page)
                likes = findViewById(R.id.like_count)
                likeButton = findViewById(R.id.like_icon)

                // 设置点赞按钮点击事件：触发 ViewModel 切换并播放简单缩放动画作为反馈
                likeButton.setOnClickListener {
                    // 先触发 ViewModel 逻辑（会更新持久化与当前数据）
                    viewModel.likePost()

                    // 播放简单的缩放动画以给用户即时反馈
                    val anim = ScaleAnimation(
                        0.8f, 1.0f, 0.8f, 1.0f,
                        ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                        ScaleAnimation.RELATIVE_TO_SELF, 0.5f
                    )
                    anim.duration = 150
                    likeButton.startAnimation(anim)
                }

                // 初始化ViewPager - 根据最长图片动态调整高度
                viewPager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.view_pager_post_page)
                val adapter = PostPageViewPagerAdapter(postItem.images) { maxHeight ->
                    adjustViewPagerHeight(maxHeight)
                }
                viewPager.adapter = adapter
            }

            private fun adjustViewPagerHeight(maxHeight: Int) {
                if (maxHeight <= 0) return

                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)

                // 限制最大高度（例如屏幕高度的80%）
                val maxAllowedHeight = (displayMetrics.heightPixels * 0.8).toInt()
                val finalHeight = minOf(maxHeight, maxAllowedHeight)

                // 设置最小高度（例如200dp）
                val minHeight = (200 * displayMetrics.density).toInt()
                val adjustedHeight = maxOf(finalHeight, minHeight)

                // 更新ViewPager的高度
                val layoutParams = viewPager.layoutParams
                layoutParams.height = adjustedHeight
                viewPager.layoutParams = layoutParams
            }

            private fun setupObservers() {
                lifecycleScope.launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is PostPageUiState.Loading -> {
                                // 显示加载状态
                            }

                            is PostPageUiState.Success -> {
                                updateUI(state.post)
                            }

                            is PostPageUiState.Error -> {
                                // 显示错误状态
                            }
                        }
                    }
                }

                // 监听当前帖子数据变化
                lifecycleScope.launch {
                    viewModel.currentPost.collect { post ->
                        post?.let { updateUI(it) }
                    }
                }
            }

            private fun updateUI(postItem: FeedItem.ImageTextItem) {
                // 设置作者信息
                authorName.text = postItem.authorName
                title.text = postItem.title
                content.text = postItem.content
                postTime.text = getPostTimeText(postItem.createTime)
                likes.text = postItem.likes.toString()

                // 加载头像
                Glide.with(this).load(postItem.avatar).circleCrop().into(avatar)

                // 根据 liked 状态切换图标（不在此处播放动画，点击时已播放）
                likeButton.setImageResource(
                    if (postItem.liked) R.drawable.like_icon_big_red else R.drawable.like_icon_big
                )
            }

            private fun getPostTimeText(createTime: Long): String {
                val time = createTime / 1000
                val currentTime = System.currentTimeMillis() / 1000
                val diff = currentTime - time
                val diffDays = diff / 86400
                val nowCal = java.util.Calendar.getInstance()
                val postCal = java.util.Calendar.getInstance().apply { timeInMillis = createTime }

                when {
                    // 同一天：显示 HH:mm
                    nowCal.get(java.util.Calendar.YEAR) == postCal.get(java.util.Calendar.YEAR) && nowCal.get(
                        java.util.Calendar.DAY_OF_YEAR
                    ) == postCal.get(java.util.Calendar.DAY_OF_YEAR) -> {
                        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        return fmt.format(java.util.Date(createTime))
                    }
                    // 昨天：显示 "昨天 HH:mm"
                    diffDays == 1L -> {
                        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        return "昨天 ${fmt.format(java.util.Date(createTime))}"
                    }
                    // 七天内：显示 x 天前
                    diffDays in 2..6 -> return "${diffDays}天前"
                    // 其余：显示 MM-dd
                    else -> {
                        val fmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                        return fmt.format(java.util.Date(createTime))
                    }
                }
            }

            private fun setupBackButton() {
                backButton.setOnClickListener {
                    finish()
                }
            }
        }