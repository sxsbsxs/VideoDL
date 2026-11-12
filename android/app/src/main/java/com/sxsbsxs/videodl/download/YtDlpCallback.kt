package com.sxsbsxs.videodl.download

import org.json.JSONObject

class YtDlpCallback(
    private val isCancelled: () -> Boolean
) {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun shouldCancel(): Boolean = cancelled || isCancelled()

    fun onProgress(payload: String) {
        val data = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val status = data.optString("status")
        val percent = data.optDouble("percent", 0.0).toInt().coerceIn(0, 100)
        val speed = formatRate(data.optDouble("speed", 0.0))
        val etaSeconds = data.optInt("eta", -1)
        val eta = if (etaSeconds >= 0) formatEta(etaSeconds) else "—"

        val message = when (status) {
            "downloading" -> "正在下载"
            "finished" -> "下载完成，正在处理"
            "error" -> "下载出错"
            else -> "正在连接"
        }
        DownloadState.updateProgress(percent, speed, eta, message)
    }

    private fun formatRate(value: Double): String {
        if (value <= 0.0) return "—"
        var amount = value
        val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
        var index = 0
        while (amount >= 1024 && index < units.lastIndex) {
            amount /= 1024
            index++
        }
        return "%.1f %s".format(amount, units[index])
    }

    private fun formatEta(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
        else "%d:%02d".format(minutes, secs)
    }
}
