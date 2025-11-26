package com.example.waterfall.activity

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.waterfall.R
import com.example.waterfall.adapter.FeedAdapter
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.ResponseDTO
import com.example.waterfall.view_model.HomePageViewModel
import com.example.waterfall.view_model.HomeUiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class HomePageActivity : AppCompatActivity() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FeedAdapter

    // 使用 viewModels 委托初始化 ViewModel
    private val viewModel: HomePageViewModel by viewModels()

    // 加载更多相关变量
    private var isLoading = false
    private var isLastPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_page_layout)
        setupSwipeRefresh()
        setupRecyclerView()
        setupObservers()
        loadData()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        adapter = FeedAdapter()

        recyclerView.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            adapter = this@HomePageActivity.adapter
            
            // 添加滚动监听器
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPositions = layoutManager.findFirstVisibleItemPositions(null)
                    
                    if (firstVisibleItemPositions.isNotEmpty()) {
                        val firstVisibleItemPosition = firstVisibleItemPositions.minOrNull() ?: 0
                        
                        // 检查是否滑动到底部
                        if (!isLoading && !isLastPage) {
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount 
                                && firstVisibleItemPosition >= 0
                                && totalItemCount >= 10) { // 至少有10个item才触发加载更多
                                loadMoreData()
                            }
                        }
                    }
                }
            })
        }
    }

    private fun setupObservers() {
        // 观察 UI 状态变化
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is HomeUiState.Loading -> {
                        showLoading(true)
                        isLoading = true
                    }

                    is HomeUiState.Success -> {
                        showLoading(false)
                        isLoading = false
                        isLastPage = !viewModel.hasMore // 根据ViewModel的hasMore状态判断是否还有更多数据
                        updateAdapterData(state.posts)
                    }

                    is HomeUiState.Error -> {
                        showLoading(false)
                        isLoading = false
                        showError(state.message)
                    }
                }
            }
        }
    }

    private fun loadData() {
        // 使用 ViewModel 加载数据
        viewModel.loadPosts(10)
    }

    private fun loadMoreData() {
        // 加载更多数据
        viewModel.loadMorePosts(10)
    }

    private fun updateAdapterData(posts: List<ResponseDTO.Post>) {
        // 将 Post 数据转换为 FeedItem
        val feedItems = posts.mapNotNull { post ->
            // 安全检查 clips 是否为 null 或空
            if (post.clips.isNullOrEmpty()) {
                return@mapNotNull null // 跳过没有媒体内容的帖子
            }

            val firstClip = post.clips.first()

            when (firstClip.type) {
                0 -> { // 图片帖子
                    FeedItem.ImageTextItem(
                        id = post.postId,
                        avatar = post.author.avatar,
                        authorName = post.author.nickname,
                        title = post.title,
                        content = post.content,
                        coverImage = firstClip.url, // 使用第一张图片作为封面
                        likes = 0,
                        comments = 0
                    )
                }

                1 -> { // 视频帖子
                    FeedItem.VideoItem(
                        id = post.postId,
                        avatar = post.author.avatar,
                        authorName = post.author.nickname,
                        title = post.title,
                        content = post.content,
                        videoUrl = firstClip.url,    // 视频地址
                        coverImage = firstClip.url,  // 视频封面（使用视频地址作为封面）
                        likes = 0,
                        comments = 0,
                        duration = "00:00" // 默认时长，可以根据实际数据调整
                    )
                }

                else -> {
                    // 未知类型，创建默认的图文帖子
                    FeedItem.ImageTextItem(
                        id = post.postId,
                        avatar = post.author.avatar,
                        authorName = post.author.nickname,
                        title = post.title,
                        content = post.content,
                        coverImage = firstClip.url,
                        likes = 0,
                        comments = 0
                    )
                }
            }
        }
        adapter.submitList(feedItems)
    }

    // 刷新功能
    private fun setupSwipeRefresh() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshPosts(10)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun showLoading(isLoading: Boolean) {
        // 加载状态处理
        if (isLoading) {
            // 可以显示加载动画
        } else {
            // 隐藏加载动画
        }
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}