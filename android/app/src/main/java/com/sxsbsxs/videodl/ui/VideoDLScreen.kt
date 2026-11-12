package com.sxsbsxs.videodl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chaquo.python.Python
import com.sxsbsxs.videodl.download.DownloadService
import com.sxsbsxs.videodl.download.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Ink = Color(0xFF0B111B)
private val Panel = Color(0xFF151F2D)
private val PanelRaised = Color(0xFF1B293B)
private val Accent = Color(0xFF3588F5)
private val Muted = Color(0xFF9AAABD)
private val Good = Color(0xFF59D69B)

private data class QualityPreset(
    val title: String,
    val selector: String
)

private val qualityPresets = listOf(
    QualityPreset("最佳单文件", "best[ext=mp4]/best"),
    QualityPreset("最高 1080p", "best[height<=1080][ext=mp4]/best[height<=1080]/best"),
    QualityPreset("最高 720p", "best[height<=720][ext=mp4]/best[height<=720]/best"),
    QualityPreset("最高 480p", "best[height<=480][ext=mp4]/best[height<=480]/best")
)

@Composable
fun VideoDLScreen(
    cookieFileName: String,
    cookieFilePath: String,
    onChooseCookies: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloadState by DownloadState.state.collectAsState()

    var url by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var mediaInfo by remember { mutableStateOf<JSONObject?>(null) }
    var selectedQuality by remember { mutableStateOf(qualityPresets.first()) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    var audioOnly by remember { mutableStateOf(false) }

    val colorScheme = darkColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        secondary = Good,
        background = Ink,
        surface = Panel,
        onBackground = Color(0xFFEAF1FA),
        onSurface = Color(0xFFEAF1FA),
        surfaceVariant = PanelRaised,
        onSurfaceVariant = Muted,
        error = Color(0xFFFF7885)
    )

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Ink
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("VD", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("VideoDL", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("ANDROID DOWNLOAD MANAGER", fontSize = 10.sp, color = Muted, letterSpacing = 1.2.sp)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("新建下载", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                errorMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("粘贴视频或播放列表链接") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                val requestedUrl = url.trim()
                                if (requestedUrl.isBlank()) {
                                    errorMessage = "请先粘贴视频链接。"
                                } else {
                                    isAnalyzing = true
                                    errorMessage = ""
                                    mediaInfo = null
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                Python.getInstance()
                                                    .getModule("videodl_android")
                                                    .callAttr("analyze", requestedUrl, cookieFilePath)
                                                    .toString()
                                            }
                                        }.onSuccess { json ->
                                            mediaInfo = JSONObject(json)
                                        }.onFailure { throwable ->
                                            errorMessage = throwable.message
                                                ?.lineSequence()
                                                ?.firstOrNull()
                                                ?: "解析失败，请检查链接、网络或 Cookie。"
                                        }
                                        isAnalyzing = false
                                    }
                                }
                            },
                            enabled = !isAnalyzing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Text("正在解析", modifier = Modifier.padding(start = 9.dp))
                            } else {
                                Text("解析链接")
                            }
                        }
                        if (errorMessage.isNotBlank()) {
                            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                mediaInfo?.let { info ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val thumbnail = info.optString("thumbnail")
                            if (thumbnail.isNotBlank()) {
                                AsyncImage(
                                    model = thumbnail,
                                    contentDescription = "视频封面",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(178.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                info.optString("title", "未命名视频"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                buildString {
                                    val uploader = info.optString("uploader")
                                    if (uploader.isNotBlank()) append(uploader)
                                    val duration = info.optInt("duration", -1)
                                    if (duration >= 0) {
                                        if (isNotEmpty()) append("  •  ")
                                        append(formatDuration(duration))
                                    }
                                },
                                color = Muted,
                                fontSize = 12.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(info.optInt("format_count").toString() + " 个格式", color = Muted, fontSize = 12.sp)
                                Text("最高探测到 " + info.optInt("max_height") + "p", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        Text("下载设置", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("清晰度（单文件流）", color = Muted, fontSize = 12.sp)
                        Box {
                            OutlinedButton(
                                onClick = { qualityMenuOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Text(selectedQuality.title, modifier = Modifier.weight(1f))
                                Text("⌄", color = Muted)
                            }
                            DropdownMenu(
                                expanded = qualityMenuOpen,
                                onDismissRequest = { qualityMenuOpen = false }
                            ) {
                                qualityPresets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.title) },
                                        onClick = {
                                            selectedQuality = preset
                                            qualityMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("仅下载音频", fontWeight = FontWeight.Medium)
                                Text("保存网站提供的原始音频格式", color = Muted, fontSize = 11.sp)
                            }
                            Switch(checked = audioOnly, onCheckedChange = { audioOnly = it })
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cookie 授权", fontWeight = FontWeight.Medium)
                                Text(
                                    if (cookieFileName.isBlank()) "可选：导入自己的 cookies.txt"
                                    else "已选择：" + cookieFileName,
                                    color = Muted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(onClick = onChooseCookies) {
                                Text(if (cookieFileName.isBlank()) "导入" else "更换")
                            }
                        }

                        Button(
                            onClick = {
                                val info = mediaInfo
                                if (info != null && url.isNotBlank()) {
                                    DownloadService.start(
                                        context = context,
                                        url = url.trim(),
                                        title = info.optString("title", "VideoDL"),
                                        quality = selectedQuality.selector,
                                        audioOnly = audioOnly,
                                        cookiePath = cookieFilePath
                                    )
                                }
                            },
                            enabled = mediaInfo != null && !downloadState.running,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Text("开始下载")
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("下载任务", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    downloadState.running -> "进行中"
                                    downloadState.finished -> "已完成"
                                    downloadState.failed -> "失败"
                                    else -> "待命"
                                },
                                color = when {
                                    downloadState.running -> Accent
                                    downloadState.finished -> Good
                                    downloadState.failed -> MaterialTheme.colorScheme.error
                                    else -> Muted
                                },
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            downloadState.title.ifBlank { "还没有下载任务" },
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { downloadState.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = Accent,
                            trackColor = PanelRaised
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(downloadState.progress.toString() + "%", color = Muted, fontSize = 12.sp)
                            Text(
                                listOf(downloadState.speed, downloadState.eta)
                                    .filter { it.isNotBlank() }
                                    .joinToString("  •  "),
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                        Text(downloadState.message, fontSize = 12.sp, color = Muted)
                        if (downloadState.running) {
                            OutlinedButton(
                                onClick = {
                                    DownloadService.cancel(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("取消下载")
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelRaised),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("Android 版说明", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "下载文件会复制到系统“下载/VideoDL”目录。当前未内置 FFmpeg，视频使用单文件格式；YouTube 新式 JavaScript 挑战在此版本中可能无法通过。",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}
