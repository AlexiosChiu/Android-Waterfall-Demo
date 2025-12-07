package com.alexioschiu.waterfall.view_model

    import android.app.Application
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alexioschiu.waterfall.data.FeedItem
    import com.alexioschiu.waterfall.data.LikePreferences
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.launch

    class PostPageViewModel(application: Application) : AndroidViewModel(application) {
        private val likePrefs = LikePreferences(application)

        private val _uiState = MutableStateFlow<PostPageUiState>(PostPageUiState.Loading)
        val uiState: StateFlow<PostPageUiState> = _uiState.asStateFlow()

        private val _currentPost = MutableStateFlow<FeedItem.ImageTextItem?>(null)
        val currentPost: StateFlow<FeedItem.ImageTextItem?> = _currentPost.asStateFlow()

        fun setPostData(postItem: FeedItem.ImageTextItem?) {
            viewModelScope.launch {
                if (postItem == null) {
                    _uiState.value = PostPageUiState.Error("帖子数据为空")
                } else {
                    val persistedLikes = likePrefs.getLikes(postItem.id, postItem.likes)
                    val persistedLiked = likePrefs.getLiked(postItem.id, postItem.liked)

                    val merged = postItem.copy(
                        likes = persistedLikes,
                        liked = persistedLiked
                    )

                    _currentPost.value = merged
                    _uiState.value = PostPageUiState.Success(merged)
                }
            }
        }

        fun likePost() {
            viewModelScope.launch {
                val current = _currentPost.value
                if (current == null) return@launch

                val newLiked = !current.liked
                val newLikes = if (newLiked) {
                    current.likes + 1
                } else {
                    (current.likes - 1).coerceAtLeast(0)
                }

                val updatedPost = current.copy(
                    liked = newLiked,
                    likes = newLikes
                )

                _currentPost.value = updatedPost
                _uiState.value = PostPageUiState.Success(updatedPost)

                likePrefs.setLiked(updatedPost.id, updatedPost.liked)
                likePrefs.setLikes(updatedPost.id, updatedPost.likes)
            }
        }
    }

    sealed class PostPageUiState {
        object Loading : PostPageUiState()
        data class Success(val post: FeedItem.ImageTextItem) : PostPageUiState()
        data class Error(val message: String) : PostPageUiState()
    }