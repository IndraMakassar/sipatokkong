package com.antoniusindra.sipatokkong.core.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object CameraUriManager {
    fun createTempUri(context: Context): Uri {
        val directory = File(context.cacheDir, "camera")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File.createTempFile(
            "camera_capture_",
            ".jpg",
            directory
        )
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
