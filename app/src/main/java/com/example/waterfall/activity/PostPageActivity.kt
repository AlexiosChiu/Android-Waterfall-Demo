package com.example.waterfall.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.example.waterfall.R
import com.example.waterfall.adapter.PostPageViewPagerAdapter
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.HashtagClickableSpan
import com.example.waterfall.data.PlaybackSettings
import com.example.waterfall.view_model.PostPageUiState
import com.example.waterfall.view_model.PostPageViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private lateinit var shareButton: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var muteButton: ImageView
    private var isMuted: Boolean = PlaybackSettings.isMuted
    private var musicBaseVolume: Float = 1f

    private val prefsName = "post_prefs"

    private lateinit var mediaAdapter: PostPageViewPagerAdapter

    private var musicPlayer: ExoPlayer? = null
    private var musicStartPositionMs: Long = 0L
    private var currentMusicUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.post_page_layout)
        postItem = intent.getParcelableExtra("POST_ITEM") ?: return
        initViews()
        prepareMusicPlayback(postItem)
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
        shareButton = findViewById(R.id.share_icon)
        progressBar = findViewById(R.id.viewpager_progress)
        progressBar.bringToFront()
        muteButton = findViewById(R.id.sound_icon)
        muteButton.setOnClickListener { toggleMute() }
        updateMuteIcon()

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val subKey = "subscribed_${postItem.createTime}"
        val isSubscribed = prefs.getBoolean(subKey, false)
        updateSubscribeUi(isSubscribed)

        subscribeButton.setOnClickListener {
            val current = prefs.getBoolean(subKey, false)
            val newState = !current
            prefs.edit { putBoolean(subKey, newState) }
            updateSubscribeUi(newState)
        }

        likeButton.setOnClickListener {
            viewModel.likePost()
            val anim = ScaleAnimation(
                0.8f,
                1.0f,
                0.8f,
                1.0f,
                ScaleAnimation.RELATIVE_TO_SELF,
                0.5f,
                ScaleAnimation.RELATIVE_TO_SELF,
                0.5f
            )
            anim.duration = 150
            likeButton.startAnimation(anim)
        }

        shareButton.setOnClickListener {
            showShareBottomSheet()
        }

        // 初始化ViewPager - 支持图片和视频
        viewPager = findViewById(R.id.view_pager_post_page)
        adjustViewPagerHeight(0) // 先填充一个默认高度，确保首个item能够绑定
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

            })
        } else {
            progressBar.visibility = View.GONE
        }

        // 初始尝试播放当前页面（若为视频）
        viewPager.post {
            mediaAdapter.playAt(viewPager.currentItem)
        }
    }

    private fun showShareBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.share_bottom_sheet_layout, null)
        bottomSheetDialog.setContentView(view)

        // 设置弹窗样式
        bottomSheetDialog.behavior.isDraggable = true
        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setCanceledOnTouchOutside(true)

        // 设置圆角背景
        val bottomSheet =
            bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            val radius = 16 * resources.displayMetrics.density
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@PostPageActivity, android.R.color.white))
                cornerRadius = radius
            }
            sheet.background = drawable
        }

        // 设置分享选项点击事件
        setupShareOptions(view, bottomSheetDialog)

        bottomSheetDialog.show()
    }

    private fun setupShareOptions(
        view: View, bottomSheetDialog: BottomSheetDialog
    ) {
        val shareOptions: List<View> = listOf(
            view.findViewById(R.id.share_wechat),
            view.findViewById(R.id.share_friend_circle),
            view.findViewById(R.id.share_qq),
            view.findViewById(R.id.share_weibo),
            view.findViewById(R.id.share_copy_link)
        )

        shareOptions.forEach { option ->
            option.setOnClickListener {
                // 处理分享选项点击事件(用弹出Toast提示用户分享)
                val shareText = when (option.id) {
                    R.id.share_wechat -> "分享到微信"
                    R.id.share_friend_circle -> "分享到朋友圈"
                    R.id.share_qq -> "分享到QQ"
                    R.id.share_weibo -> "分享到微博"
                    R.id.share_copy_link -> "复制链接"
                    else -> "未知分享选项"
                }
                Toast.makeText(this@PostPageActivity, shareText, Toast.LENGTH_SHORT).show()
                bottomSheetDialog.dismiss()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mediaAdapter.isInitialized) {
            mediaAdapter.playAt(viewPager.currentItem)
        }
        resumeMusic()
    }

    override fun onPause() {
        super.onPause()
        // 暂停所有视频以避免后台播放
        if (::mediaAdapter.isInitialized) mediaAdapter.pauseAll()
        pauseMusic()
    }

    override fun onDestroy() {
        if (::mediaAdapter.isInitialized) {
            mediaAdapter.release()
            viewPager.adapter = null
        }
        releaseMusicPlayer()
        super.onDestroy()
    }

    private fun updateSubscribeUi(subscribed: Boolean) {
        subscribeButton.setImageResource(
            if (subscribed) R.drawable.already_subscribe_icon else R.drawable.subscribe_icon
        )
    }

    private fun adjustViewPagerHeight(height: Int) {
        val layoutParams = viewPager.layoutParams
        if (height > 0) {
            layoutParams.height = height
        } else {
            // 当初始高度为0时，设置一个临时的默认高度（4:3），以确保容器在加载前可见。
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels
            // 使用 4:3 的比例作为默认值
            layoutParams.height = (screenWidth * 3 / 4)
        }
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

        prepareMusicPlayback(postItem)
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
                hashTag = tagText, color = hashtagColor, onClick = { tag ->
                    val intent = Intent(this, HashTagPageActivity::class.java)
                    intent.putExtra("hashTag", tag)
                    startActivity(intent)
                })
            spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        content.text = spannable
        content.movementMethod = LinkMovementMethod.getInstance()
        content.highlightColor = Color.TRANSPARENT
    }

    private fun prepareMusicPlayback(item: FeedItem.ImageTextItem) {
        val musicUrl = item.musicUrl
        if (musicUrl.isNullOrBlank()) {
            releaseMusicPlayer()
            currentMusicUrl = null
            return
        }
        if (currentMusicUrl == musicUrl && musicPlayer != null) {
            musicStartPositionMs = (item.musicSeekTime ?: 0) * 1000L
            musicBaseVolume = ((item.musicVolume ?: 100).coerceIn(0, 100)) / 100f
            applyMuteStateToMusic()
            return
        }
        releaseMusicPlayer()
        musicPlayer = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(musicUrl))
            prepare()
        }
        currentMusicUrl = musicUrl
        musicBaseVolume = ((item.musicVolume ?: 100).coerceIn(0, 100)) / 100f
        musicStartPositionMs = (item.musicSeekTime ?: 0) * 1000L
        applyMuteStateToMusic()
    }

    private fun resumeMusic() {
        val player = musicPlayer ?: return
        val minStart = (postItem.musicSeekTime ?: 0) * 1000L
        val resumePosition = musicStartPositionMs.coerceAtLeast(minStart.toLong())
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.seekTo(resumePosition)
        applyMuteStateToMusic()
        player.playWhenReady = true
        player.play()
    }

    private fun pauseMusic() {
        musicPlayer?.let {
            musicStartPositionMs = it.currentPosition
            it.pause()
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        PlaybackSettings.isMuted = isMuted
        applyMuteStateToMusic()
        updateMuteIcon()
    }

    private fun applyMuteStateToMusic() {
        musicPlayer?.volume = if (isMuted) 0f else musicBaseVolume
    }

    private fun updateMuteIcon() {
        if (!::muteButton.isInitialized) return
        val iconRes = if (isMuted) R.drawable.sound_mute_icon else R.drawable.sound_play_icon
        muteButton.setImageResource(iconRes)
    }

    private fun releaseMusicPlayer() {
        musicPlayer?.release()
        musicPlayer = null
    }
}
