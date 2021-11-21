package com.dontsu.samplerecordingwithmediastore2

import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.util.Log
import com.dontsu.samplerecordingwithmediastore2.util.Constants
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_ALL_DELETE_PERMISSION_CODE
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_MODIFY_PERMISSION_CODE
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_SINGLE_DELETE_PERMISSION_CODE

class RecorderUpdateDeleteResultReceiver(
    handler: Handler,
    private val receiver: Receiver? = null
): ResultReceiver(handler) {

    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        when (resultData?.getInt(Constants.RECORDER_RECEIVE_MODE , -1)) {
            RECORDER_MODIFY_PERMISSION_CODE -> {
                receiver?.onModifyReceiveResult(resultCode, resultData)
            }
            RECORDER_SINGLE_DELETE_PERMISSION_CODE,
            RECORDER_ALL_DELETE_PERMISSION_CODE -> {
                receiver?.onDeleteReceiveResult(resultCode, resultData)
            }
            else -> Unit
        }

    }

    interface Receiver {

        fun onModifyReceiveResult(resultCode: Int, data: Bundle?)

        fun onDeleteReceiveResult(resultCode: Int, data: Bundle?)

    }

}