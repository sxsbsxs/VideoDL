package com.sxsbsxs.videodl.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.sxsbsxs.videodl.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private fun describeDownloadError(error: Throwable): String {
    val raw = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .joinToString("\\n")
        .take(1200)
    val lower = raw.lowercase()

    val hint = when {
        "no supported javascript runtime" in lower ||
            "javascript challenge" in lower ||
            "challenge solving failed" in lower ->
            "该网站（常见于 YouTube）需要 JavaScript 运行环境；当前 Android 版尚未内置该运行环境。"
        "requested format is not available" in lower ||
            "requested format not available" in lower ->
            "当前清晰度没有可用的单文件格式。请重新解析后改选较低清晰度或“最佳单文件”。"
        "http error 403" in lower || "status code 403" in lower ->
            "网站拒绝了媒体请求（HTTP 403）。请重新解析；需要登录的网站请导入有效 Cookie。"
        "http error 429" in lower || "too many requests" in lower ->
            "请求过于频繁（HTTP 429）。请稍后再试。"
        "sign in" in lower || "login required" in lower || "private video" in lower ->
            "该视频需要登录或没有公开访问权限。请确认账号有权访问，并导入有效 Cookie。"
        "unsupported url" in lower ->
            "当前链接格式或网站暂不受 yt-dlp 支持。"
        "ffmpeg" in lower ->
            "该视频可能需要合并独立的视频流和音频流；当前 Android 版尚未内置 FFmpeg。"
        "timed out" in lower || "timeout" in lower || "ssl" in lower ||
            "certificate" in lower || "urlopen error" in lower ->
            "网络连接失败或被当前网络环境拦截。请检查网络、代理和 DNS 后重试。"
        else -> "下载失败。请查看下面的 yt-dlp 错误详情。"
    }
    return if (raw.isBlank()) hint else "$hint\\n\\n错误详情：$raw"
}

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cancelRequested = false

    private var callback: YtDlpCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelRequested = true
                callback?.cancel()
                DownloadState.update(
                    DownloadState.state.value.copy(message = "正在停止下载…")
                )
                return START_NOT_STICKY
            }
            ACTION_START -> {
                cancelRequested = false
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                val quality = intent.getStringExtra(EXTRA_QUALITY).orEmpty()
                val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
                val cookiePath = intent.getStringExtra(EXTRA_COOKIE_PATH).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val outputDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?.resolve("VideoDL")
                    ?: File(filesDir, "downloads/VideoDL")

                outputDir.mkdirs()
                DownloadState.reset(title.ifBlank { url })
                startForeground(NOTIFICATION_ID, buildNotification("正在准备下载", 0))
                acquireWakeLock()
                callback = YtDlpCallback(
                    isCancelled = { cancelRequested },
                    onProgressUpdate = { percent, speed, eta, message ->
                        updateNotification("$message  •  $speed  •  $eta", percent)
                    }
                )

                serviceScope.launch {
                    runDownload(
                        url = url,
                        outputDir = outputDir,
                        quality = quality,
                        audioOnly = audioOnly,
                        cookiePath = cookiePath
                    )
                    stopForeground(STOP_FOREGROUND_DETACH)
                    releaseWakeLock()
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runDownload(
        url: String,
        outputDir: File,
        quality: String,
        audioOnly: Boolean,
        cookiePath: String
    ) {
        try {
            val python = Python.getInstance()
            val module = python.getModule("videodl_android")
            val activeCallback = callback ?: YtDlpCallback(isCancelled = { cancelRequested })
            val json = module.callAttr(
                "download",
                url,
                outputDir.absolutePath,
                quality,
                audioOnly,
                cookiePath,
                activeCallback
            ).toString()

            if (cancelRequested) {
                DownloadState.cancelled()
                updateNotification("下载已取消", DownloadState.state.value.progress, finished = true)
                return
            }

            val result = JSONObject(json)
            val paths = result.optJSONArray("files")
            val saved = mutableListOf<String>()
            if (paths != null) {
                for (index in 0 until paths.length()) {
                    val path = paths.optString(index)
                    if (path.isNotBlank()) {
                        val publicUri = publishToDownloads(File(path))
                        if (publicUri != null) saved += publicUri.toString()
                    }
                }
            }
            DownloadState.complete(saved)
            updateNotification("下载完成", 100, finished = true)
        } catch (error: Throwable) {
            if (error is CancellationException || cancelRequested) {
                DownloadState.cancelled()
                updateNotification("下载已取消", DownloadState.state.value.progress, finished = true)
            } else {
                DownloadState.fail(describeDownloadError(error))
                updateNotification("下载失败，打开 VideoDL 查看详情", 0, finished = true)
            }
        }
    }

    private fun publishToDownloads(source: File): Uri? {
        if (!source.isFile || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val extension = source.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, source.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VideoDL")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法写入系统下载目录")
            val ready = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, ready, null, null)
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VideoDL:Download"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VideoDL 下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示 VideoDL 当前下载进度"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, progress: Int, finished: Boolean = false): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VideoDL")
            .setContentText(text)
            .setContentIntent(openPending)
            .setOngoing(!finished)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)

        if (!finished) {
            val cancelIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            val cancelPending = PendingIntent.getService(
                this, 1, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPending)
        }
        return builder.build()
    }

    private fun updateNotification(text: String, progress: Int, finished: Boolean = false) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, progress, finished))
    }

    override fun onDestroy() {
        cancelRequested = true
        callback?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "videodl_downloads"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.sxsbsxs.videodl.START"
        private const val ACTION_CANCEL = "com.sxsbsxs.videodl.CANCEL"
        private const val EXTRA_URL = "url"
        private const val EXTRA_QUALITY = "quality"
        private const val EXTRA_AUDIO_ONLY = "audio_only"
        private const val EXTRA_COOKIE_PATH = "cookie_path"
        private const val EXTRA_TITLE = "title"

        fun start(
            context: Context,
            url: String,
            title: String,
            quality: String,
            audioOnly: Boolean,
            cookiePath: String
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_QUALITY, quality)
                putExtra(EXTRA_AUDIO_ONLY, audioOnly)
                putExtra(EXTRA_COOKIE_PATH, cookiePath)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
