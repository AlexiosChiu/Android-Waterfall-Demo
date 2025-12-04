package com.example.waterfall.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.example.waterfall.R
import com.example.waterfall.adapter.FeedAdapter
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.ResponseDTO
import com.example.waterfall.view_model.HomePageViewModel
import com.example.waterfall.view_model.HomeUiState
import com.example.waterfall.view_model.RefreshEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FeedAdapter

    private val viewModel: HomePageViewModel by viewModels()

    private var isLoading = false
    private var isLastPage = false
    private var shouldScrollToTopAfterRefresh = false
    private var isColumnWidthReady = false
    private var pendingFeedItems: List<FeedItem>? = null

    // 预加载相关常量
    private val PRELOAD_AHEAD_DISTANCE = 5 // 预加载可见项前面的数量
    private val PRELOAD_BEHIND_DISTANCE = 5 // 预加载可见项后面的数量
    private lateinit var glideRequestManager: RequestManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.home_page_layout, container, false)
        // 初始化Glide请求管理器
        glideRequestManager = Glide.with(this)
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
            layoutManager =
                StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                    gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
                }
            setHasFixedSize(true)
            recycledViewPool.setMaxRecycledViews(0, 20)
            adapter = this@HomeFragment.adapter
            itemAnimator = null
            (parent as? ViewGroup)?.layoutTransition = null

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    // 检测可见项目并预加载视频封面
                    preloadVideoCovers()
                    if (!isLoading && !isLastPage) {
                        val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val firstVisibleItemPositions =
                            layoutManager.findFirstVisibleItemPositions(null)

                        if (firstVisibleItemPositions.isNotEmpty()) {
                            val firstVisibleItemPosition =
                                firstVisibleItemPositions.minOrNull() ?: 0
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount &&
                                firstVisibleItemPosition >= 0 &&
                                totalItemCount >= 4
                            ) {
                                loadMoreData()
                            }
                        }
                    }
                }
            })
        }

        recyclerView.doOnLayout {
            val layoutManager =
                recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return@doOnLayout
            val spanCount = layoutManager.spanCount
            if (spanCount <= 0 || recyclerView.width <= 0) return@doOnLayout
            val columnWidth = recyclerView.width / spanCount
            adapter.setColumnWidth(columnWidth)
            isColumnWidthReady = true
            pendingFeedItems?.let { items ->
                pendingFeedItems = null
                submitFeedItems(items)
            }
        }
    }

    // 添加预加载视频封面的方法
    private fun preloadVideoCovers() {
        val layoutManager = recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return
        val adapter = recyclerView.adapter as? FeedAdapter ?: return
        val feedItems = adapter.currentList

        if (feedItems.isEmpty()) return

        // 获取可见项的位置
        val firstVisibleItems = IntArray(layoutManager.spanCount) { 0 }
        val lastVisibleItems = IntArray(layoutManager.spanCount) { 0 }

        layoutManager.findFirstVisibleItemPositions(firstVisibleItems)
        layoutManager.findLastVisibleItemPositions(lastVisibleItems)

        val firstVisiblePosition = firstVisibleItems.minOrNull() ?: 0
        val lastVisiblePosition = lastVisibleItems.maxOrNull() ?: 0

        // 计算预加载范围
        val startPreloadPosition = (firstVisiblePosition - PRELOAD_BEHIND_DISTANCE).coerceAtLeast(0)
        val endPreloadPosition =
            (lastVisiblePosition + PRELOAD_AHEAD_DISTANCE).coerceAtMost(feedItems.size - 1)

        // 对预加载范围内的视频项进行预加载
        for (position in startPreloadPosition..endPreloadPosition) {
            val item = feedItems[position]
            if (item is FeedItem.ImageTextItem) {
                // 检查是否为视频URL
                if (isVideoUrl(item.coverClip)) {
                    preloadVideoFrame(item.coverClip)
                }
            }
        }
    }

    // 检查URL是否为视频格式
    private fun isVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".mp4") ||
                lowerUrl.endsWith(".webm") ||
                lowerUrl.endsWith(".m3u8") ||
                lowerUrl.endsWith(".avi") ||
                lowerUrl.endsWith(".mov")
    }

    // 预加载视频第一帧
    private fun preloadVideoFrame(videoUrl: String) {
        // 使用低质量快速预加载视频第一帧
        glideRequestManager
            .asBitmap()
            .load(videoUrl)
            .apply(
                RequestOptions()
                    .frame(0) // 第一帧
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // 全部缓存
                    .signature(ObjectKey("${videoUrl}_frame_preload")) // 添加签名确保正确缓存
            )
            .preload() // 预加载而不显示
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state !is HomeUiState.Loading) {
                    swipeRefreshLayout.isRefreshing = false
                } else {
                    swipeRefreshLayout.isRefreshing = true
                }

                isLoading = state is HomeUiState.Loading

                when (state) {
                    is HomeUiState.Success -> {
                        isLastPage = !viewModel.hasMore
                        updateAdapterData(state.posts)
                    }

                    is HomeUiState.Error -> {
                        shouldScrollToTopAfterRefresh = false
                        showError(state.message)
                    }

                    is HomeUiState.Loading -> Unit
                }
            }
        }

        lifecycleScope.launch {
            viewModel.refreshEvent.collect { event ->
                if (event is RefreshEvent.ScrollToTopAfterRefresh) {
                    shouldScrollToTopAfterRefresh = true
                }
            }
        }
    }

    private fun loadData() {
        viewModel.loadPosts(10)
    }

    private fun loadMoreData() {
        viewModel.loadMorePosts(10)
    }

    private fun updateAdapterData(posts: List<ResponseDTO.Post>) {
        val feedItems = posts
            .asSequence()
            .filter { !it.clips.isNullOrEmpty() }
            .map { post ->
                val firstClip = post.clips!!.first()
                FeedItem.ImageTextItem(
                    id = post.postId,
                    avatar = post.author.avatar,
                    authorName = post.author.nickname,
                    title = post.title,
                    content = post.content,
                    clips = post.clips.map { it.url },
                    coverClip = firstClip.url,
                    coverHeight = firstClip.height,
                    coverWidth = firstClip.width,
                    likes = 0,
                    liked = false,
                    createTime = post.createTime,
                    hashTags = post.hashtag ?: emptyList()
                )
            }
            .toList()

        submitFeedItems(feedItems)
    }

    private fun submitFeedItems(items: List<FeedItem>) {
        if (isColumnWidthReady) {
            adapter.submitList(items) {
                if (shouldScrollToTopAfterRefresh) {
                    scrollToTop()
                }
            }
        } else {
            pendingFeedItems = items
        }
    }

    private fun setupSwipeRefresh(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright)

        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            recyclerView.canScrollVertically(-1)
        }

        swipeRefreshLayout.setOnRefreshListener {
            recyclerView.stopScroll()
            shouldScrollToTopAfterRefresh = true
            viewModel.refreshPosts(10)
        }
    }

    fun scrollToTop() {
        recyclerView.post {
            recyclerView.stopScroll()
            (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.apply {
                invalidateSpanAssignments()
                scrollToPositionWithOffset(0, 0)
            }
            shouldScrollToTopAfterRefresh = false
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}