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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.chaquo.python.AndroidPlatform
import com.chaquo.python.Python
import com.sxsbsxs.videodl.ui.VideoDLScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var cookiePicker: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermission: ActivityResultLauncher<String>

    private var cookieFilePath by mutableStateOf("")
    private var cookieFileName by mutableStateOf("")

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

        val preferences = getSharedPreferences("videodl_android", MODE_PRIVATE)
        cookieFilePath = preferences.getString("cookie_path", "").orEmpty()
        cookieFileName = preferences.getString("cookie_name", "").orEmpty()

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
                getSharedPreferences("videodl_android", MODE_PRIVATE).edit()
                    .putString("cookie_path", cookieFilePath)
                    .putString("cookie_name", cookieFileName)
                    .apply()
            } ?: error("无法读取选择的文件")
        }.onFailure {
            cookieFilePath = ""
            cookieFileName = ""
            getSharedPreferences("videodl_android", MODE_PRIVATE).edit()
                .remove("cookie_path")
                .remove("cookie_name")
                .apply()
        }
    }
}
