package com.example.waterfall.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

class HomeFragment : Fragment() {
    
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FeedAdapter

    // 使用 viewModels 委托初始化 ViewModel
    private val viewModel: HomePageViewModel by viewModels()

    // 加载更多相关变量
    private var isLoading = false
    private var isLastPage = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.home_page_layout, container, false)
        setupViews(view)
        setupObservers()
        loadData()
        return view
    }

    private fun setupViews(view: View) {
        setupSwipeRefresh(view)
        setupRecyclerView(view)
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        adapter = FeedAdapter()

        recyclerView.apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            adapter = this@HomeFragment.adapter

            // 添加滚动监听器
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPositions =
                        layoutManager.findFirstVisibleItemPositions(null)

                    if (firstVisibleItemPositions.isNotEmpty()) {
                        val firstVisibleItemPosition = firstVisibleItemPositions.minOrNull() ?: 0

                        // 检查是否滑动到底部
                        if (!isLoading && !isLastPage) {
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                                && firstVisibleItemPosition >= 0
                                && totalItemCount >= 4
                            ) { // 滑过4个item就加载更多
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
                        isLoading = true
                    }

                    is HomeUiState.Success -> {
                        isLoading = false
                        isLastPage = !viewModel.hasMore // 根据ViewModel的hasMore状态判断是否还有更多数据
                        updateAdapterData(state.posts)
                    }

                    is HomeUiState.Error -> {
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
                        coverHeight = firstClip.height, // 封面高度
                        coverWidth = firstClip.width,  // 封面宽度
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
                        coverHeight = firstClip.height,
                        coverWidth = firstClip.width,
                        likes = 0,
                        comments = 0
                    )
                }
            }
        }
        adapter.submitList(feedItems)
    }

    // 刷新功能
    private fun setupSwipeRefresh(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshPosts(10)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun showError(message: String) {
        // Fragment中使用requireContext()获取Context
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}