package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import dev.jdtech.jellyfin.models.UiText
import java.util.Locale

data class DownloaderState(
    val status: Int = 0,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val errorText: UiText? = null,
) {
    val isDownloading: Boolean
        get() =
            status in
                arrayOf(
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                )

    val speedText: String
        get() {
            if (speedBytesPerSecond <= 0L) return "正在连接..."
            val mb = speedBytesPerSecond / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB/s", mb)
            } else {
                val kb = speedBytesPerSecond / 1024.0
                String.format(Locale.US, "%.0f KB/s", kb)
            }
        }
}
