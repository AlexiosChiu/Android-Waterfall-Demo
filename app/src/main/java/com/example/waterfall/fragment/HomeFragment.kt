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
import com.example.waterfall.view_model.RefreshEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FeedAdapter

    // 使用 viewModels 委托初始化 ViewModel
    private val viewModel: HomePageViewModel by viewModels()

    private var isLoading = false
    private var isLastPage = false
    private var shouldScrollToTopAfterRefresh = false


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        adapter.setHasStableIds(true)

        recyclerView.apply {
            layoutManager =
                StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                    gapStrategy =
                        StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS // 解决RecyclerView中的空位
                }

            setItemViewCacheSize(20)

            adapter = this@HomeFragment.adapter

            // 永久禁用所有 RecyclerView 的 item 动画
            itemAnimator = null

            // 如果 RecyclerView 的父容器存在 LayoutTransition，也清除，避免布局变更动画
            (parent as? ViewGroup)?.layoutTransition = null


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
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0 && totalItemCount >= 4) { // 滑过4个item就加载更多
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
                        // 下拉刷新成功后滚动到顶部
                        if (swipeRefreshLayout.isRefreshing || shouldScrollToTopAfterRefresh) {
                            scrollToTop()
                        }
                    }

                    is HomeUiState.Error -> {
                        isLoading = false
                        // 错误时也停止刷新动画
                        swipeRefreshLayout.isRefreshing = false
                        shouldScrollToTopAfterRefresh = false
                        showError(state.message)
                    }
                }
            }
        }

        // 观察一次性刷新事件
        lifecycleScope.launch {
            viewModel.refreshEvent.collect { event ->
                if (event is RefreshEvent.ScrollToTopAfterRefresh) {
                    shouldScrollToTopAfterRefresh = true
                    // 显示刷新指示器，提供视觉反馈
                    if (!swipeRefreshLayout.isRefreshing) {
                        swipeRefreshLayout.isRefreshing = true
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
        // 过滤掉 clips 为 null/空 的帖子并构造 FeedItem 列表
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
                    images = post.clips.map { it.url },
                    coverImage = firstClip.url,
                    coverHeight = firstClip.height,
                    coverWidth = firstClip.width,
                    likes = 0,
                    liked = false,
                    createTime = post.createTime,
                    hashTags = post.hashtag ?: emptyList()
                )
            }
            .toList()

        // 抑制布局期间避免中途重排，提交后重新分配 spans 并请求布局
        recyclerView.suppressLayout(true)
        adapter.submitList(feedItems)
        recyclerView.post {
            (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.invalidateSpanAssignments()
            recyclerView.requestLayout()
            recyclerView.suppressLayout(false)
        }
    }

    // 刷新功能
    private fun setupSwipeRefresh(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        // 设置颜色方案
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright
        )

        swipeRefreshLayout.setOnRefreshListener {
            // 开始刷新时清除之前的滚动状态
            recyclerView.stopScroll()
            viewModel.refreshPosts(10)
        }
    }

    fun scrollToTop() {
        // 确保在主线程中执行滚动操作
        recyclerView.post {
            // 先滚动到顶部
            recyclerView.scrollToPosition(0)

            // 然后确保SwipeRefreshLayout的状态正确
            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
            // 重置标志位
            if (shouldScrollToTopAfterRefresh) {
                shouldScrollToTopAfterRefresh = false
            }
        }
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}
