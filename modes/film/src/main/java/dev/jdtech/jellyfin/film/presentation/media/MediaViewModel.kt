package dev.jdtech.jellyfin.film.presentation.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@HiltViewModel
class MediaViewModel @Inject constructor(private val repository: JellyfinRepository) : ViewModel() {
    private val _state = MutableStateFlow(MediaState())
    val state = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                val libraries = repository.getLibraries()
                
                // 加载服务器上所有的影视媒体（以服务器有的为主）
                var items: List<FindroidItem> = try {
                    repository.getItems(
                        includeTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.VIDEO),
                        recursive = true,
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to get recursive items, falling back to library latest")
                    emptyList()
                }

                // 如果直接 getItems 为空，尝试从各库拉取最新媒体或推荐媒体
                if (items.isEmpty()) {
                    val fallbackItems = mutableListOf<FindroidItem>()
                    libraries.forEach { lib ->
                        try {
                            val latest = repository.getLatestMedia(lib.id)
                            fallbackItems.addAll(latest)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to get latest for library ${lib.name}")
                        }
                    }
                    if (fallbackItems.isEmpty()) {
                        try {
                            fallbackItems.addAll(repository.getSuggestions())
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to get suggestions")
                        }
                    }
                    items = fallbackItems.distinctBy { it.id }
                }

                _state.emit(_state.value.copy(libraries = libraries, mediaItems = items))
            } catch (e: Exception) {
                Timber.e(e, "MediaViewModel loadData error")
                _state.emit(_state.value.copy(error = e))
            }
            _state.emit(_state.value.copy(isLoading = false))
        }
    }

    fun onAction(action: MediaAction) {
        when (action) {
            is MediaAction.OnRetryClick -> {
                loadData()
            }
            else -> Unit
        }
    }
}
