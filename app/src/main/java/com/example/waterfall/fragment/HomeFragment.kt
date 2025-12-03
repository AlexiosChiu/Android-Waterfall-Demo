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