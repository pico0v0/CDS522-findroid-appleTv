package dev.jdtech.jellyfin.presentation.film.components

import android.app.DownloadManager
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.utils.Downloader
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TaskStatus {
    WAITING,      // 排队等待下载（显示等待时钟图标）
    DOWNLOADING,  // 正在下载（显示动态圆环进度条与实时速度）
    COMPLETED,    // 缓存完成（显示绿色打勾图标）
    FAILED,       // 失败
}

data class AppleTvDownloadTask(
    val movie: FindroidMovie,
    val status: TaskStatus = TaskStatus.WAITING,
    val progress: Float = 0f,
    val speedText: String = "0 KB/s",
    val downloadId: Long? = null,
)

/**
 * 全局顺序下载队列管理器（单任务顺序下载：一次只下一个，多任务自动排队并显示等待状态）
 */
object AppleTvDownloadQueue {
    private val _tasks = MutableStateFlow<List<AppleTvDownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private var downloaderRef: Downloader? = null
    private var activeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun registerDownloader(downloader: Downloader) {
        downloaderRef = downloader
    }

    fun initCompletedFromLocal(movies: List<FindroidMovie>) {
        val current = _tasks.value.toMutableList()
        movies.forEach { movie ->
            if (current.none { it.movie.id == movie.id }) {
                current.add(
                    AppleTvDownloadTask(
                        movie = movie,
                        status = TaskStatus.COMPLETED,
                        progress = 1f,
                    )
                )
            }
        }
        _tasks.value = current
    }

    fun enqueue(movie: FindroidMovie, downloader: Downloader? = downloaderRef) {
        downloader?.let { downloaderRef = it }
        val d = downloaderRef ?: return

        val current = _tasks.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.movie.id == movie.id }
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            if (existing.status == TaskStatus.COMPLETED) {
                return
            }
        } else {
            // 刚点击下载，立即以 WAITING 状态加入队列，即刻呈现在下载列表！
            current.add(
                AppleTvDownloadTask(
                    movie = movie,
                    status = TaskStatus.WAITING,
                )
            )
            _tasks.value = current
        }

        processQueue(d)
    }

    fun cancel(movieId: UUID, downloader: Downloader? = downloaderRef) {
        downloader?.let { downloaderRef = it }
        val d = downloaderRef

        val current = _tasks.value.toMutableList()
        val task = current.firstOrNull { it.movie.id == movieId } ?: return

        if (task.status == TaskStatus.DOWNLOADING) {
            activeJob?.cancel()
            activeJob = null
            if (d != null && task.downloadId != null) {
                scope.launch {
                    d.cancelDownload(task.movie, task.downloadId)
                }
            }
        }

        current.removeAll { it.movie.id == movieId }
        _tasks.value = current

        if (d != null) {
            processQueue(d)
        }
    }

    fun getTask(movieId: UUID): AppleTvDownloadTask? {
        return _tasks.value.firstOrNull { it.movie.id == movieId }
    }

    private fun processQueue(downloader: Downloader) {
        val current = _tasks.value.toMutableList()
        // 一次只下一个：若已有正在下载的任务，保持其它任务等待
        if (current.any { it.status == TaskStatus.DOWNLOADING }) {
            return
        }

        val nextWaitingIndex = current.indexOfFirst { it.status == TaskStatus.WAITING }
        if (nextWaitingIndex < 0) {
            return
        }

        val targetTask = current[nextWaitingIndex]
        current[nextWaitingIndex] = targetTask.copy(status = TaskStatus.DOWNLOADING, progress = 0.01f)
        _tasks.value = current

        // 启动该任务的顺序下载
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                val (downloadId, _) = downloader.downloadItem(
                    item = targetTask.movie,
                    sourceId = targetTask.movie.sources.first().id,
                    storageIndex = 0,
                )

                if (downloadId != -1L) {
                    updateTask(targetTask.movie.id) { it.copy(downloadId = downloadId) }
                    pollProgress(targetTask.movie.id, downloadId, downloader)
                } else {
                    updateTask(targetTask.movie.id) { it.copy(status = TaskStatus.FAILED) }
                    processQueue(downloader)
                }
            } catch (_: Exception) {
                updateTask(targetTask.movie.id) { it.copy(status = TaskStatus.FAILED) }
                processQueue(downloader)
            }
        }
    }

    private suspend fun pollProgress(movieId: UUID, downloadId: Long, downloader: Downloader) {
        var lastBytes = -1L
        var lastTime = System.currentTimeMillis()
        var filteredSpeed = 0L

        while (true) {
            val info = downloader.getDetailedProgress(downloadId)
            val now = System.currentTimeMillis()
            val dt = (now - lastTime).coerceAtLeast(1)

            if (lastBytes == -1L) {
                lastBytes = info.downloadedBytes
                lastTime = now
            } else {
                val dBytes = (info.downloadedBytes - lastBytes).coerceAtLeast(0)
                val instant = (dBytes * 1000L) / dt
                lastBytes = info.downloadedBytes
                lastTime = now
                filteredSpeed = if (dBytes > 0) {
                    if (filteredSpeed == 0L) instant
                    else (filteredSpeed * 0.35 + instant * 0.65).toLong()
                } else {
                    (filteredSpeed * 0.5).toLong()
                }
            }

            val speedText = formatSpeed(if (info.status == DownloadManager.STATUS_RUNNING) filteredSpeed else 0L)
            val progressFloat = (info.progress.coerceAtLeast(1) / 100f).coerceAtMost(1f)

            if (info.status == DownloadManager.STATUS_SUCCESSFUL || info.progress >= 100) {
                // 下载完成！标记为 COMPLETED，并自动启动队列中的下一个等待任务
                updateTask(movieId) {
                    it.copy(
                        status = TaskStatus.COMPLETED,
                        progress = 1f,
                        speedText = "0 KB/s",
                    )
                }
                activeJob = null
                processQueue(downloader)
                break
            } else if (info.status == DownloadManager.STATUS_FAILED) {
                updateTask(movieId) { it.copy(status = TaskStatus.FAILED) }
                activeJob = null
                processQueue(downloader)
                break
            } else {
                updateTask(movieId) {
                    it.copy(
                        progress = progressFloat,
                        speedText = speedText,
                    )
                }
            }

            delay(800L)
        }
    }

    private fun updateTask(movieId: UUID, transform: (AppleTvDownloadTask) -> AppleTvDownloadTask) {
        val current = _tasks.value.toMutableList()
        val index = current.indexOfFirst { it.movie.id == movieId }
        if (index >= 0) {
            current[index] = transform(current[index])
            _tasks.value = current
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        val mb = bytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mb)
        } else {
            val kb = bytesPerSec / 1024.0
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }
}

