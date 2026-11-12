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

class DownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cancelRequested = false

    private var callback: YtDlpCallback? = null

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
                val message = error.message?.lineSequence()?.firstOrNull().orEmpty()
                DownloadState.fail(message.ifBlank { "下载失败，请检查链接或网络。" })
                updateNotification("下载失败", 0, finished = true)
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
