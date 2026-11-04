package dev.jdtech.jellyfin.presentation.film.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String,
    val forceUpdate: Boolean = false,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class NewVersionAvailable(val info: UpdateInfo) : UpdateStatus
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val speedText: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateStatus
    data class DownloadCompleted(val info: UpdateInfo, val apkFile: File) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

object AppUpdateManager {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var downloadJob: Job? = null
    private val httpClient = OkHttpClient.Builder().build()

    // 默认或动态注入的 Jellyfin 服务端基地址
    var customBaseUrl: String? = null

    /**
     * 获取本地 App 实际版本信息
     */
    fun getCurrentVersion(context: Context): Pair<Long, String> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            Pair(code, pInfo.versionName ?: "1.0.0")
        } catch (e: Exception) {
            Pair(1L, "1.0.0")
        }
    }

    /**
     * 发起版本检测（支持自动静默检测与手动点击检测）
     */
    fun checkForUpdate(context: Context, isManual: Boolean = false, serverUrl: String? = null) {
        if (_status.value is UpdateStatus.Checking || _status.value is UpdateStatus.Downloading) {
            return
        }

        val baseUrl = serverUrl ?: customBaseUrl.orEmpty()
        if (baseUrl.isBlank()) {
            if (isManual) {
                _status.value = UpdateStatus.Error("未获取到服务器连接地址，请登录后重试")
            }
            return
        }
        val (currentCode, _) = getCurrentVersion(context)

        scope.launch {
            _status.value = UpdateStatus.Checking
            val candidateEndpoints = buildCandidateUrls(baseUrl)

            var foundInfo: UpdateInfo? = null
            var lastErrorMsg = "无法连接至更新服务器"

            for (endpoint in candidateEndpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string().orEmpty()
                            if (bodyString.isNotBlank()) {
                                val json = JSONObject(bodyString)

                                val remoteCode = json.optLong("versionCode", 0L)
                                val remoteName = json.optString("versionName", "最新版")
                                var downloadUrl = json.optString("downloadUrl", "")
                                val changelog = json.optString("changelog", "性能与界面体验优化")
                                val forceUpdate = json.optBoolean("forceUpdate", false)

                                // 相对路径智能补全为绝对路径
                                if (downloadUrl.isNotBlank() && !downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
                                    val rootUri = Uri.parse(endpoint)
                                    downloadUrl = "${rootUri.scheme}://${rootUri.authority}/${downloadUrl.trimStart('/')}"
                                }

                                if (remoteCode > currentCode) {
                                    foundInfo = UpdateInfo(
                                        versionCode = remoteCode,
                                        versionName = remoteName,
                                        downloadUrl = downloadUrl,
                                        changelog = changelog,
                                        forceUpdate = forceUpdate,
                                    )
                                    return@use
                                } else {
                                    foundInfo = null
                                    lastErrorMsg = "ALREADY_LATEST"
                                    return@use
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.d(e, "Check update failed at $endpoint")
                }

                if (foundInfo != null || lastErrorMsg == "ALREADY_LATEST") break
            }

            withContext(Dispatchers.Main) {
                when {
                    foundInfo != null -> {
                        _status.value = UpdateStatus.NewVersionAvailable(foundInfo!!)
                    }
                    lastErrorMsg == "ALREADY_LATEST" -> {
                        _status.value = UpdateStatus.Idle
                        if (isManual) {
                            Toast.makeText(context, "当前已是最新版本 (v${getCurrentVersion(context).second})", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        _status.value = UpdateStatus.Idle
                        if (isManual) {
                            Toast.makeText(context, "暂未检测到更新或服务器未配置 version.json", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * 智能构造候选版本描述文件端点（兼容 8096 端口与 80 端口）
     */
    private fun buildCandidateUrls(rawUrl: String): List<String> {
        val list = mutableListOf<String>()
        val clean = rawUrl.trim().trimEnd('/')

        // 1. 直接拼接原始 baseUrl
        list.add("$clean/version.json")
        list.add("$clean/update/version.json")

        // 2. 剥除端口后的主机根路径 (例如将 http://host:8096 转为 http://host/version.json)
        val hostOnly = clean.replace(Regex(":\\d+$"), "")
        if (hostOnly != clean) {
            list.add("$hostOnly/version.json")
            list.add("$hostOnly/update/version.json")
        }


        return list.distinct()
    }

    /**
     * 开始下载 APK 文件并流式回传下载进度与网速
     */
    fun startDownload(context: Context, info: UpdateInfo) {
        if (_status.value is UpdateStatus.Downloading) return

        downloadJob?.cancel()
        downloadJob = scope.launch {
            _status.value = UpdateStatus.Downloading(
                info = info,
                progress = 0.01f,
                speedText = "0 KB/s",
                downloadedBytes = 0L,
                totalBytes = 0L,
            )

            try {
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                if (!targetDir.exists()) targetDir.mkdirs()
                val outputFile = File(targetDir, "Findroid-AppleTV-v${info.versionName}.apk")

                val request = Request.Builder().url(info.downloadUrl).build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP error ${response.code}")
                    }

                    val body = response.body ?: throw IOException("Empty response body")
                    val totalLength = body.contentLength().coerceAtLeast(1L)

                    var downloaded = 0L
                    var lastSampleBytes = 0L
                    var lastSampleTime = System.currentTimeMillis()
                    var currentSpeed = "0 KB/s"

                    val buffer = ByteArray(16 * 1024)
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(outputFile)

                    outputStream.use { out ->
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            val deltaMs = now - lastSampleTime
                            if (deltaMs >= 500L) {
                                val speedBytesPerSec = (downloaded - lastSampleBytes) * 1000L / deltaMs
                                currentSpeed = formatSpeed(speedBytesPerSec)
                                lastSampleBytes = downloaded
                                lastSampleTime = now

                                val progress = (downloaded.toFloat() / totalLength.toFloat()).coerceIn(0.01f, 0.99f)
                                _status.value = UpdateStatus.Downloading(
                                    info = info,
                                    progress = progress,
                                    speedText = currentSpeed,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalLength,
                                )
                            }
                        }
                        out.flush()
                    }

                    _status.value = UpdateStatus.DownloadCompleted(info, outputFile)

                    // 下载完成自动调起系统安装器
                    withContext(Dispatchers.Main) {
                        installApk(context, outputFile)
                    }
                }
            } catch (e: CancellationException) {
                _status.value = UpdateStatus.Idle
            } catch (e: Exception) {
                Timber.e(e, "Download update APK failed")
                _status.value = UpdateStatus.Error("下载更新失败: ${e.localizedMessage ?: "网络异常"}")
            }
        }
    }

    /**
     * 取消下载
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        _status.value = UpdateStatus.Idle
    }

    /**
     * 忽略更新或关闭弹窗
     */
    fun dismiss() {
        if (_status.value !is UpdateStatus.Downloading) {
            _status.value = UpdateStatus.Idle
        }
    }

    /**
     * 通过 FileProvider 唤起 Android 系统原生安装界面
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Android 8.0+ 未知来源权限检查
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "请开启允许安装未知应用权限以继续更新", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.e(e, "Launch APK installer failed")
            Toast.makeText(context, "调起系统安装器失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec.toDouble() / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format(Locale.US, "%.0f KB/s", bytesPerSec.toDouble() / 1024)
            else -> "$bytesPerSec B/s"
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }
}
