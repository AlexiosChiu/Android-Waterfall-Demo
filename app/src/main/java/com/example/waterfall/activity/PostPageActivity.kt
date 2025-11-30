package com.example.waterfall.activity

import android.os.Bundle
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

    // UI组件
    private lateinit var backButton: ImageView
    private lateinit var avatar: ImageView
    private lateinit var authorName: TextView
    private lateinit var title: TextView
    private lateinit var content: TextView
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
        likes = findViewById(R.id.like_count)
        likeButton = findViewById(R.id.like_icon)

        // 设置点赞按钮点击事件
        likeButton.setOnClickListener {
            viewModel.likePost()
        }

        // 初始化ViewPager
        val viewPager =
            findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.view_pager_post_page)
        val adapter = PostPageViewPagerAdapter(postItem.images)
        viewPager.adapter = adapter
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
        likes.text = postItem.likes.toString()

        // 加载头像
        Glide.with(this).load(postItem.avatar).circleCrop().into(avatar)


    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            finish()
        }
    }
}