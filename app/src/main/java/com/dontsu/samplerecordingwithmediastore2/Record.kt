package com.dontsu.samplerecordingwithmediastore2

import android.net.Uri
import android.os.Parcelable
import android.provider.MediaStore
import kotlinx.parcelize.Parcelize

@Parcelize
data class Record(
    val id: String,
    val displayName: String?,
    val duration: Long?,
    val createAt: Long?
): Parcelable {

    fun getRecordUri(): Uri = Uri.withAppendedPath(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
    )

}
