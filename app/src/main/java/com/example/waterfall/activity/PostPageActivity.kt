package com.example.waterfall.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.View
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.waterfall.R
import com.example.waterfall.adapter.PostPageViewPagerAdapter
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.HashtagClickableSpan
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
    private lateinit var subscribeButton: ImageView
    private lateinit var progressBar: ProgressBar

    private val prefsName = "post_prefs"

    // 保留 adapter 引用以控制视频播放
    private lateinit var mediaAdapter: PostPageViewPagerAdapter

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
        subscribeButton = findViewById(R.id.subscribe_icon)
        progressBar = findViewById(R.id.viewpager_progress)
        progressBar.bringToFront()

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val subKey = "subscribed_${postItem.createTime}"
        val isSubscribed = prefs.getBoolean(subKey, false)
        updateSubscribeUi(isSubscribed)

        subscribeButton.setOnClickListener {
            val current = prefs.getBoolean(subKey, false)
            val newState = !current
            prefs.edit().putBoolean(subKey, newState).apply()
            updateSubscribeUi(newState)
        }

        likeButton.setOnClickListener {
            viewModel.likePost()
            val anim = ScaleAnimation(
                0.8f, 1.0f, 0.8f, 1.0f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            )
            anim.duration = 150
            likeButton.startAnimation(anim)
        }

        // 初始化ViewPager - 支持图片和视频
        viewPager = findViewById(R.id.view_pager_post_page)
        mediaAdapter = PostPageViewPagerAdapter(postItem.clips) { maxHeight ->
            adjustViewPagerHeight(maxHeight)
        }
        viewPager.adapter = mediaAdapter

        if (postItem.clips.size > 1) {
            progressBar.max = postItem.clips.size
            progressBar.progress = 0

            viewPager.registerOnPageChangeCallback(object :
                androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    progressBar.progress = position + 1
                    // 切换页面时暂停其他视频并尝试播放当前（如果是视频）
                    mediaAdapter.pauseAll()
                    mediaAdapter.playAt(position)
                }

                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int
                ) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                }
            })
        } else {
            progressBar.visibility = View.GONE
        }

        // 初始尝试播放当前页面（若为视频）
        viewPager.post {
            mediaAdapter.playAt(viewPager.currentItem)
        }
    }

    override fun onPause() {
        super.onPause()
        // 暂停所有视频以避免后台播放
        if (::mediaAdapter.isInitialized) mediaAdapter.pauseAll()
    }

    private fun updateSubscribeUi(subscribed: Boolean) {
        subscribeButton.setImageResource(
            if (subscribed) R.drawable.already_subscribe_icon else R.drawable.subscribe_icon
        )
    }

    private fun adjustViewPagerHeight(maxHeight: Int) {
        if (maxHeight <= 0) return

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val maxAllowedHeight = (displayMetrics.heightPixels * 0.8).toInt()
        val finalHeight = minOf(maxHeight, maxAllowedHeight)
        val minHeight = (200 * displayMetrics.density).toInt()
        val adjustedHeight = maxOf(finalHeight, minHeight)

        val layoutParams = viewPager.layoutParams
        layoutParams.height = adjustedHeight
        viewPager.layoutParams = layoutParams
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is PostPageUiState.Loading -> {}
                    is PostPageUiState.Success -> {
                        updateUI(state.post)
                    }

                    is PostPageUiState.Error -> {}
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentPost.collect { post ->
                post?.let { updateUI(it) }
            }
        }
    }

    private fun updateUI(postItem: FeedItem.ImageTextItem) {
        authorName.text = postItem.authorName
        title.text = postItem.title
        setupContentWithHashTags()
        postTime.text = getPostTimeText(postItem.createTime)
        likes.text = postItem.likes.toString()

        Glide.with(this).load(postItem.avatar).circleCrop().into(avatar)

        likeButton.setImageResource(
            if (postItem.liked) R.drawable.like_icon_big_red else R.drawable.like_icon_big
        )

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val subKey = "subscribed_${postItem.createTime}"
        val isSubscribed = prefs.getBoolean(subKey, false)
        updateSubscribeUi(isSubscribed)
    }

    private fun getPostTimeText(createTime: Long): String {
        val time = createTime / 1000
        val currentTime = System.currentTimeMillis() / 1000
        val diff = currentTime - time
        val diffDays = diff / 86400
        val nowCal = java.util.Calendar.getInstance()
        val postCal = java.util.Calendar.getInstance().apply { timeInMillis = createTime }

        when {
            nowCal.get(java.util.Calendar.YEAR) == postCal.get(java.util.Calendar.YEAR) && nowCal.get(
                java.util.Calendar.DAY_OF_YEAR
            ) == postCal.get(java.util.Calendar.DAY_OF_YEAR) -> {
                val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                return fmt.format(java.util.Date(createTime))
            }

            diffDays == 1L -> {
                val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                return "昨天 ${fmt.format(java.util.Date(createTime))}"
            }

            diffDays in 2..6 -> return "${diffDays}天前"
            else -> {
                val fmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                return fmt.format(java.util.Date(createTime))
            }
        }
    }

    private fun setupBackButton() {
        backButton.setOnClickListener { finish() }
    }

    private fun setupContentWithHashTags() {
        val textStr = postItem.content
        val spannable = SpannableString(textStr)
        val hashtagColor = ContextCompat.getColor(this, R.color.hashtag_color)

        postItem.hashTags?.forEach { hashTag ->
            val start = hashTag.start.coerceIn(0, textStr.length)
            val end = hashTag.end.coerceIn(0, textStr.length)
            if (start >= end) return@forEach

            val tagText = textStr.substring(start, end)
            val span = HashtagClickableSpan(
                hashTag = tagText,
                color = hashtagColor,
                onClick = { tag ->
                    val intent = Intent(this, HashTagPageActivity::class.java)
                    intent.putExtra("hashTag", tag)
                    startActivity(intent)
                }
            )
            spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        content.text = spannable
        content.movementMethod = LinkMovementMethod.getInstance()
        content.highlightColor = Color.TRANSPARENT
    }
}
