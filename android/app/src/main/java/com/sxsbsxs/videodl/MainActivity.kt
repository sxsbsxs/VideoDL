package com.sxsbsxs.videodl

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.chaquo.python.AndroidPlatform
import com.chaquo.python.Python
import com.sxsbsxs.videodl.ui.VideoDLScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var cookiePicker: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermission: ActivityResultLauncher<String>

    private var cookieFilePath: String = ""
    private var cookieFileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cookiePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importCookieFile(uri)
        }

        notificationPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            VideoDLScreen(
                cookieFileName = cookieFileName,
                cookieFilePath = cookieFilePath,
                onChooseCookies = {
                    cookiePicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                }
            )
        }
    }

    private fun importCookieFile(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val destination = File(filesDir, "videodl-cookies.txt")
                destination.outputStream().use { output -> input.copyTo(output) }
                cookieFilePath = destination.absolutePath
                cookieFileName = uri.lastPathSegment?.substringAfterLast('/')?.take(48)
                    ?: "cookies.txt"
            } ?: error("无法读取选择的文件")
        }.onFailure {
            cookieFilePath = ""
            cookieFileName = ""
        }
        recreateContent()
    }

    private fun recreateContent() {
        // Recompose the current screen with the newly imported private cookie path.
        setContent {
            VideoDLScreen(
                cookieFileName = cookieFileName,
                cookieFilePath = cookieFilePath,
                onChooseCookies = {
                    cookiePicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                }
            )
        }
    }
}
