package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun AppleTvUpdateDialog() {
    val context = LocalContext.current
    val status by AppUpdateManager.status.collectAsStateWithLifecycle()
    val (_, currentVersionName) = AppUpdateManager.getCurrentVersion(context)

    val isVisible = status !is UpdateStatus.Idle && status !is UpdateStatus.Checking

    if (isVisible) {
        Dialog(
            onDismissRequest = {
                if (status !is UpdateStatus.Downloading) {
                    AppUpdateManager.dismiss()
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = status !is UpdateStatus.Downloading,
                dismissOnClickOutside = status !is UpdateStatus.Downloading,
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF1C1C1E),
                border = BorderStroke(1.dp, Color(0xFF38383A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 顶部 Apple 专属徽章
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color(0x33FFFFFF),
                        border = BorderStroke(0.6.dp, Color(0x44FFFFFF)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_apple_tv),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "软件在线更新",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    when (val currentStatus = status) {
                        is UpdateStatus.NewVersionAvailable -> {
                            val info = currentStatus.info

                            Text(
                                text = "发现新版本 v${info.versionName}",
                                color = Color.White,
                                fontSize = 21.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = "当前已安装 v$currentVersionName · 推荐立即升级体验",
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(16.dp))

                            // 更新日志卡片
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2C2C2E),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    Text(
                                        text = "更新内容：",
                                        color = Color(0xFFD1D1D6),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = info.changelog,
                                        color = Color(0xFFE5E5EA),
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                    )
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            // 按钮行
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (!info.forceUpdate) {
                                    Surface(
                                        onClick = { AppUpdateManager.dismiss() },
                                        shape = RoundedCornerShape(percent = 50),
                                        color = Color(0xFF2C2C2E),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "稍后再说",
                                                color = Color(0xFFD1D1D6),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    onClick = { AppUpdateManager.startDownload(context, info) },
                                    shape = RoundedCornerShape(percent = 50),
                                    color = Color.White,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(CoreR.drawable.ic_download),
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "立即更新",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateStatus.Downloading -> {
                            val info = currentStatus.info

                            Text(
                                text = "正在下载更新",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = "Findroid Apple TV v${info.versionName}",
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                            )

                            Spacer(Modifier.height(22.dp))

                            LinearProgressIndicator(
                                progress = { currentStatus.progress },
                                color = Color(0xFF0A84FF),
                                trackColor = Color(0xFF38383A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )

                            Spacer(Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${(currentStatus.progress * 100).toInt()}% · ${currentStatus.speedText}",
                                    color = Color(0xFF0A84FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )

                                val downStr = AppUpdateManager.formatBytes(currentStatus.downloadedBytes)
                                val totalStr = AppUpdateManager.formatBytes(currentStatus.totalBytes)
                                Text(
                                    text = "$downStr / $totalStr",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp,
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            Surface(
                                onClick = { AppUpdateManager.cancelDownload() },
                                shape = RoundedCornerShape(percent = 50),
                                color = Color(0xFF2C2C2E),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "取消下载",
                                        color = Color(0xFFD1D1D6),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        is UpdateStatus.DownloadCompleted -> {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xE61E3A2B)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_check),
                                    contentDescription = null,
                                    tint = Color(0xFF34C759),
                                    modifier = Modifier.size(30.dp),
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = "新版本安装包下载完成",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = "请在系统安装器弹窗中点击「更新」即可覆盖安装",
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(20.dp))

                            Surface(
                                onClick = { AppUpdateManager.installApk(context, currentStatus.apkFile) },
                                shape = RoundedCornerShape(percent = 50),
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "立即安装",
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        is UpdateStatus.Error -> {
                            Text(
                                text = "更新遇到问题",
                                color = Color(0xFFFF453A),
                                fontSize = 18.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = currentStatus.message,
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(18.dp))

                            Surface(
                                onClick = { AppUpdateManager.dismiss() },
                                shape = RoundedCornerShape(percent = 50),
                                color = Color(0xFF2C2C2E),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "知道了",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

