package dev.jdtech.jellyfin.film.presentation.media

import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidItem

data class MediaState(
    val libraries: List<FindroidCollection> = emptyList(),
    val mediaItems: List<FindroidItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
