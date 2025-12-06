package com.example.waterfall.fragment

import android.content.Context
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
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.example.waterfall.R
import com.example.waterfall.adapter.FeedAdapter
import com.example.waterfall.data.FeedCacheManager
import com.example.waterfall.data.FeedItem
import com.example.waterfall.data.ResponseDTO
import com.example.waterfall.view_model.HomePageViewModel
import com.example.waterfall.view_model.HomeUiState
import com.example.waterfall.view_model.RefreshEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.abs

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
    private var columnWidth: Int = 0
    private val videoCoverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val videoPreloadSemaphore = Semaphore(permits = 1)

    // 预加载相关常量
    private val PRELOAD_AHEAD_DISTANCE = 3 // 预加载可见项前面的数量
    private val PRELOAD_BEHIND_DISTANCE = 3 // 预加载可见项后面的数量

    // 预加载视频封面
    private val preloadedUrls = mutableSetOf<String>()
    private var lastPreloadRange: IntRange? = null
    private lateinit var glideRequestManager: RequestManager

    // 缓存历史推文用于首刷
    private lateinit var feedCacheManager: FeedCacheManager
    private var hasRestoredCache = false

    // 图片预取 scope & 控制
    private val imagePrefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imagePreloadSemaphore = Semaphore(permits = 3)
    private val preloadedImageUrls = mutableSetOf<String>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        feedCacheManager = FeedCacheManager(context.applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.home_page_layout, container, false)
        // 初始化Glide请求管理器
        glideRequestManager = Glide.with(this)
        setupViews(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        restoreCachedFeed()
        loadData()
        scrollToTop()
    }

    private fun setupViews(view: View) {
        setupSwipeRefresh(view)
        setupRecyclerView(view)
    }

    private fun setupRecyclerView(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        adapter = FeedAdapter().apply {
            setHasStableIds(true)
        }

        recyclerView.apply {
            layoutManager =
                StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                    gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
                }
            setHasFixedSize(true)
            setItemViewCacheSize(12)
            recycledViewPool.setMaxRecycledViews(0, 24)
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
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0 && totalItemCount >= 4) {
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
            val calculatedColumnWidth = recyclerView.width / spanCount
            columnWidth = calculatedColumnWidth
            adapter.setColumnWidth(calculatedColumnWidth)
            isColumnWidthReady = true
            pendingFeedItems?.let { items ->
                pendingFeedItems = null
                submitFeedItems(items)
            }
        }
    }

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

        // 防抖：只有当可见项位置变化较大时才重新预加载
        val startPreloadPosition = (firstVisiblePosition - PRELOAD_BEHIND_DISTANCE).coerceAtLeast(0)
        val endPreloadPosition =
            (lastVisiblePosition + PRELOAD_AHEAD_DISTANCE).coerceAtMost(feedItems.size - 1)
        val preloadRange = startPreloadPosition..endPreloadPosition
        val previousRange = lastPreloadRange
        if (previousRange != null && abs(preloadRange.first - previousRange.first) < 2 && abs(
                preloadRange.last - previousRange.last
            ) < 2
        ) {
            return
        }
        lastPreloadRange = preloadRange

        for (position in preloadRange) {
            val item = feedItems[position]
            if (item is FeedItem.ImageTextItem) {
                if (isVideoUrl(item.coverClip) && !preloadedUrls.contains(item.coverClip)) {
                    preloadVideoFrame(item.coverClip)
                }
            }
        }
    }

    private fun isVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") || lowerUrl.endsWith(".m3u8")
    }

    private fun preloadVideoFrame(videoUrl: String) {
        if (!preloadedUrls.add(videoUrl)) return
        videoCoverScope.launch {
            videoPreloadSemaphore.withPermit {
                val requestOptions =
                    RequestOptions().frame(750_000).format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .override(columnWidth, Target.SIZE_ORIGINAL)
                        .downsample(DownsampleStrategy.AT_MOST)

                val target = glideRequestManager.asBitmap().load(videoUrl).apply(requestOptions)
                    .thumbnail(0.25f).submit()

                try {
                    target.get()
                } finally {
                    glideRequestManager.clear(target)
                }
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                swipeRefreshLayout.isRefreshing = state is HomeUiState.Loading
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

        viewLifecycleOwner.lifecycleScope.launch {
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
        val feedItems = posts.asSequence().filter { !it.clips.isNullOrEmpty() }.map { post ->
            val clips = post.clips!!
            val coverClip = selectCoverClip(clips)
            FeedItem.ImageTextItem(
                id = post.postId,
                avatar = post.author.avatar,
                authorName = post.author.nickname,
                title = post.title,
                content = post.content,
                clips = post.clips.map { it.url },
                coverClip = coverClip.url,
                coverHeight = coverClip.height,
                coverWidth = coverClip.width,
                likes = 0,
                liked = false,
                createTime = post.createTime,
                hashTags = post.hashtag ?: emptyList(),
                musicUrl = post.music.url,
                musicVolume = post.music.volume,
                musicSeekTime = post.music.seekTime
            )
        }.toList()

        submitFeedItems(feedItems)
        persistFeedItems(feedItems)
    }

    private fun selectCoverClip(clips: List<ResponseDTO.Clip>): ResponseDTO.Clip {
        val firstClip = clips.first()
        if (!isVideoUrl(firstClip.url)) return firstClip
        return clips.firstOrNull { !isVideoUrl(it.url) } ?: firstClip
    }

    private fun restoreCachedFeed() {
        if (hasRestoredCache) return
        hasRestoredCache = true
        viewLifecycleOwner.lifecycleScope.launch {
            val cachedItems = feedCacheManager.loadFeedItems()
            if (cachedItems.isNotEmpty() && adapter.currentList.isEmpty()) {
                submitFeedItems(cachedItems)
                // 触发图片预取（将封面/clips/头像下载到 Glide 磁盘缓存）
                prefetchImagesForItems(cachedItems)
            }
        }
    }

    private fun persistFeedItems(items: List<FeedItem>) {
        val imageItems = items.filterIsInstance<FeedItem.ImageTextItem>()
        if (imageItems.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            feedCacheManager.saveFeedItems(imageItems)
            // 持久化后异步预取图片到 Glide 缓存
            prefetchImagesForItems(imageItems)
        }
    }

    private fun prefetchImagesForItems(items: List<FeedItem>) {
        val urls = mutableSetOf<String>()
        for (item in items.filterIsInstance<FeedItem.ImageTextItem>()) {
            if (item.coverClip.isNotBlank()) urls.add(item.coverClip)
            item.clips.filter { it.isNotBlank() }.forEach { urls.add(it) }
            if (item.avatar.isNotBlank()) urls.add(item.avatar)
        }

        imagePrefetchScope.launch {
            for (url in urls) {
                if (!preloadedImageUrls.add(url)) continue
                imagePreloadSemaphore.withPermit {
                    try {
                        val future = glideRequestManager.downloadOnly().load(url).submit()
                        future.get()
                    } catch (_: Exception) {
                        // 忽略单个资源下载失败
                    }
                }
            }
        }
    }

    private fun submitFeedItems(items: List<FeedItem>) {
        resetPreloadState()
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

    private fun resetPreloadState() {
        preloadedUrls.clear()
        lastPreloadRange = null
        preloadedImageUrls.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        videoCoverScope.cancel()
        imagePrefetchScope.cancel()
        resetPreloadState()
        hasRestoredCache = false
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