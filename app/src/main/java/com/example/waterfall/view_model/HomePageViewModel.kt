package com.example.waterfall.view_model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterfall.data.ResponseDTO
import com.example.waterfall.network.ApiCallback
import com.example.waterfall.network.NetworkManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomePageViewModel : ViewModel() {

    // UI状态
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    // 添加一个 SharedFlow 用于处理一次性事件
    private val _refreshEvent = MutableSharedFlow<RefreshEvent>()
    val refreshEvent: SharedFlow<RefreshEvent> = _refreshEvent

    // 帖子列表数据
    private val _postList = mutableListOf<ResponseDTO.Post>()

    // 是否还有更多数据
    var hasMore = true
    private var isLoadingMore = false

    /**
     * 加载帖子数据
     */
    fun loadPosts(count: Int = 10) {
        _uiState.value = HomeUiState.Loading
        NetworkManager.getInstance()
            .getPostList(count, true, object : ApiCallback<ResponseDTO.ApiResponse> {
                override fun onSuccess(data: ResponseDTO.ApiResponse) {
                    viewModelScope.launch {
                        _postList.clear()
                        _postList.addAll(data.postList)
                        hasMore = data.hasMore == 1
                        _uiState.value = HomeUiState.Success(_postList.toList())
                    }
                }

                override fun onFailure(error: String) {
                    viewModelScope.launch {
                        _uiState.value = HomeUiState.Error(error)
                    }
                }
            })
    }

    /**
     * 加载更多帖子
     */
    fun loadMorePosts(count: Int = 10) {
        if (!hasMore || isLoadingMore) return

        isLoadingMore = true

        NetworkManager.getInstance()
            .getPostList(count, true, object : ApiCallback<ResponseDTO.ApiResponse> {
                override fun onSuccess(data: ResponseDTO.ApiResponse) {
                    viewModelScope.launch {
                        _postList.addAll(data.postList)
                        hasMore = data.hasMore == 1
                        isLoadingMore = false
                        _uiState.value = HomeUiState.Success(_postList.toList())
                    }
                }

                override fun onFailure(error: String) {
                    viewModelScope.launch {
                        isLoadingMore = false
                        _uiState.value = HomeUiState.Error("加载更多失败: $error")
                    }
                }
            })
    }

    /**
     * 刷新数据
     */
    fun refreshPosts(count: Int = 10) {
        loadPosts(count)
    }

    /**
     * 触发刷新，并在完成后滚动到顶部
     */
    fun refreshAndScrollToTop() {
        viewModelScope.launch {
            _refreshEvent.emit(RefreshEvent.ScrollToTopAfterRefresh)
        }
        refreshPosts()
    }
}

// 定义一次性事件的密封类
sealed class RefreshEvent {
    object ScrollToTopAfterRefresh : RefreshEvent()
}


// UI状态密封类
sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val posts: List<ResponseDTO.Post>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()

}
