package com.dontsu.samplerecordingwithmediastore2.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.util.*

object Utils {

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun modifyRecord(context: Context, uri: Uri, fileName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp3")
            put(MediaStore.Audio.Media.DATE_MODIFIED, Date().time)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

}