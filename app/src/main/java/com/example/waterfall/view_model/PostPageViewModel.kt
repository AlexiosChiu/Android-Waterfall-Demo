package com.example.waterfall.view_model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waterfall.data.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostPageViewModel : ViewModel() {
    // UI状态
    private val _uiState = MutableStateFlow<PostPageUiState>(PostPageUiState.Loading)
    val uiState: StateFlow<PostPageUiState> = _uiState.asStateFlow()

    // 当前帖子数据
    private val _currentPost = MutableStateFlow<FeedItem.ImageTextItem?>(null)
    val currentPost: StateFlow<FeedItem.ImageTextItem?> = _currentPost.asStateFlow()

    /**
     * 设置帖子数据
     */
    fun setPostData(postItem: FeedItem.ImageTextItem?) {
        viewModelScope.launch {
            if (postItem == null) {
                _uiState.value = PostPageUiState.Error("帖子数据为空")
            } else {
                _currentPost.value = postItem
                _uiState.value = PostPageUiState.Success(postItem)
            }
        }
    }

    /**
     * 点赞功能
     */
    fun likePost() {
        viewModelScope.launch {
            val current = _currentPost.value
            if (current != null) {
                val updatedPost = current.copy(likes = current.likes + 1)
                _currentPost.value = updatedPost
                _uiState.value = PostPageUiState.Success(updatedPost)
            }
        }
    }
}

// UI状态定义
sealed class PostPageUiState {
    object Loading : PostPageUiState()
    data class Success(val post: FeedItem.ImageTextItem) : PostPageUiState()
    data class Error(val message: String) : PostPageUiState()
}