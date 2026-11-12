package com.sxsbsxs.videodl.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DownloadUiState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val failed: Boolean = false,
    val title: String = "",
    val message: String = "等待下载",
    val progress: Int = 0,
    val speed: String = "",
    val eta: String = "",
    val savedFiles: List<String> = emptyList()
)

object DownloadState {
    private val _state = MutableStateFlow(DownloadUiState())
    val state = _state.asStateFlow()

    fun update(value: DownloadUiState) {
        _state.value = value
    }

    fun reset(title: String) {
        _state.value = DownloadUiState(
            running = true,
            title = title,
            message = "正在启动下载"
        )
    }

    fun updateProgress(progress: Int, speed: String, eta: String, message: String) {
        _state.value = _state.value.copy(
            running = true,
            progress = progress.coerceIn(0, 100),
            speed = speed,
            eta = eta,
            message = message
        )
    }

    fun complete(files: List<String>) {
        _state.value = _state.value.copy(
            running = false,
            finished = true,
            failed = false,
            progress = 100,
            message = if (files.isEmpty()) "下载完成，但没有发现新文件" else "下载完成，已保存到 Download/VideoDL",
            savedFiles = files
        )
    }

    fun fail(message: String) {
        _state.value = _state.value.copy(
            running = false,
            finished = false,
            failed = true,
            message = message
        )
    }

    fun cancelled() {
        _state.value = _state.value.copy(
            running = false,
            finished = false,
            failed = false,
            message = "已取消下载"
        )
    }
}
