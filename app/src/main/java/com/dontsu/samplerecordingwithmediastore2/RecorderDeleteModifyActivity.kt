package com.dontsu.samplerecordingwithmediastore2

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_ALL_DELETE_PERMISSION_CODE
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_DATA
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_ITEM_POSITION
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_MODIFY_PERMISSION_CODE
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_RECEIVE_MODE
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_SINGLE_DELETE_PERMISSION_CODE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

class RecorderDeleteModifyActivity : AppCompatActivity() {

    private val records by lazy {
        intent.getSerializableExtra(RECORDER_RECORDS) as List<*>
    }
    private val newFileName by lazy {
        intent.getStringExtra(RECORDER_NEW_FILE_NAME)
    }
    private val resultReceiver by lazy {
        intent.getParcelableExtra(RECORDER_RECEIVER) as ResultReceiver?
    }
    private val position by lazy {
        intent.getIntExtra(RECORDER_ITEM_POSITION, -1)
    }
    private val requestCode by lazy {
        when (intent.getIntExtra(RECORDER_MODE, -1)) {
            RECORDER_MODE_SINGLE_DELETE -> {
                RECORDER_SINGLE_DELETE_PERMISSION_CODE
            }
            RECORDER_MODE_ALL_DELETE -> {
                RECORDER_ALL_DELETE_PERMISSION_CODE
            }
            else -> {
                RECORDER_MODIFY_PERMISSION_CODE
            }
        }
    }

    /** Coroutine */
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startIntentSender()

    }

    private fun startIntentSender() {
        val intentSender = intent.getParcelableExtra<IntentSender>(RECORDER_INTENT_SENDER)
        intentSender?.let {
            startIntentSenderForResult(
                intentSender,
                requestCode,
                null,
                0,
                0,
                0,
                null
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val bundle = Bundle()
            coroutineScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (requestCode == RECORDER_MODIFY_PERMISSION_CODE) {
                        newFileName?.let { filename ->
                            updateRecord(
                                applicationContext,
                                (records as ArrayList<Record>).first().getRecordUri(),
                                filename
                            )
                        }
                        bundle.putInt(RECORDER_RECEIVE_MODE, RECORDER_MODIFY_PERMISSION_CODE)
                        bundle.putInt(RECORDER_ITEM_POSITION, position)
                        bundle.putParcelable(RECORDER_DATA, (records as ArrayList<Record>).first())
                    } else {
                        var permissionCode = -1
                        permissionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            when (requestCode) {
                                RECORDER_ALL_DELETE_PERMISSION_CODE -> RECORDER_ALL_DELETE_PERMISSION_CODE
                                else -> RECORDER_SINGLE_DELETE_PERMISSION_CODE
                            }
                        } else {
                            RECORDER_SINGLE_DELETE_PERMISSION_CODE
                        }

                        bundle.putInt(RECORDER_RECEIVE_MODE, permissionCode)
                        bundle.putParcelableArrayList(RECORDER_DATA, records as ArrayList<Record>)
                    }

                }
                resultReceiver?.send(Activity.RESULT_OK, bundle)
                finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun updateRecord(context: Context, uri: Uri, fileName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp3")
            put(MediaStore.Audio.Media.DATE_MODIFIED, Date().time)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    companion object {

        private const val RECORDER_INTENT_SENDER = "RECORDER_INTENT_SENDER"
        private const val RECORDER_RECORDS = "RECORDER_RECORDS"
        private const val RECORDER_NEW_FILE_NAME = "RECORDER_NEW_FILE_NAME"
        private const val RECORDER_RECEIVER = "RECORDER_RECEIVER"

        private const val RECORDER_MODE = "RECORDER_MODE"
        const val RECORDER_MODE_SINGLE_DELETE = 0
        const val RECORDER_MODE_ALL_DELETE = 2
        const val RECORDER_MODE_MODIFY = 1

        fun newIntent(
            context: Context,
            intentSender: IntentSender,
            mode: Int,
            records: ArrayList<Record>?,
            newFileName: String? = null,
            position: Int? = null,
            receiver: ResultReceiver? = null
        ) = Intent(
            context,
            RecorderDeleteModifyActivity::class.java
        ).apply {
            putExtra(RECORDER_INTENT_SENDER, intentSender)
            putExtra(RECORDER_MODE, mode)
            putExtra(RECORDER_RECORDS, records)
            newFileName?.let {
                putExtra(RECORDER_NEW_FILE_NAME, newFileName)
            }
            position?.let {
                putExtra(RECORDER_ITEM_POSITION, position)
            }
            receiver?.let {
                putExtra(RECORDER_RECEIVER, receiver)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    }

}
