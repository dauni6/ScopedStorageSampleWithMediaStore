package com.dontsu.samplerecordingwithmediastore2

import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.dontsu.samplerecordingwithmediastore2.RecorderDeleteModifyActivity.Companion.RECORDER_MODE_ALL_DELETE
import com.dontsu.samplerecordingwithmediastore2.RecorderDeleteModifyActivity.Companion.RECORDER_MODE_MODIFY
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_DATA
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_ITEM_POSITION
import com.dontsu.samplerecordingwithmediastore2.RecorderDeleteModifyActivity.Companion.RECORDER_MODE_SINGLE_DELETE
import com.dontsu.samplerecordingwithmediastore2.databinding.ListItemBinding
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_ALL_DELETE_PERMISSION_CODE
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_RECEIVE_MODE
import com.dontsu.samplerecordingwithmediastore2.util.Constants.RECORDER_SINGLE_DELETE_PERMISSION_CODE
import com.dontsu.samplerecordingwithmediastore2.util.Utils.modifyRecord
import kotlinx.coroutines.*

class RecordListAdapter(
    private val context: Context
) : RecyclerView.Adapter<RecordListAdapter.RecordViewHolder>() {
    var recordList = mutableListOf<Record>()
    var mediaPlayer: MediaPlayer? = null

    /** Coroutine */
    private val ioJob = Job()
    private val ioCoroutineScope = CoroutineScope(Dispatchers.IO + ioJob)

    /** ResultReceiver */
    private val recorderResultReceiver = RecorderUpdateDeleteResultReceiver(
        Handler(Looper.getMainLooper()),
        object : RecorderUpdateDeleteResultReceiver.Receiver {
            @RequiresApi(Build.VERSION_CODES.Q)
            override fun onModifyReceiveResult(resultCode: Int, data: Bundle?) {
                if (resultCode == RESULT_OK) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val itemPosition = data?.getInt(RECORDER_ITEM_POSITION, -1) ?: -1
                        val record =
                            data?.getParcelable(RECORDER_DATA) as Record? ?: return@launch
                        val newRecord = getRecordWithId(context, record.id)

                        newRecord?.let {
                            recordList[itemPosition] = it
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "파일이름을 변경했습니다.", Toast.LENGTH_SHORT).show()
                            notifyItemChanged(itemPosition)
                        }
                    }
                }
            }

            override fun onDeleteReceiveResult(resultCode: Int, data: Bundle?) {
                if (resultCode == RESULT_OK) {
                    val records =
                        data?.getParcelableArrayList<Record>(RECORDER_DATA) as ArrayList<Record>?
                            ?: return
                    when (data?.getInt(RECORDER_RECEIVE_MODE, -1)) {
                        RECORDER_SINGLE_DELETE_PERMISSION_CODE -> {
                            clearRecord(records.first())
                        }
                        RECORDER_ALL_DELETE_PERMISSION_CODE -> {
                            clearAllRecord()
                        }
                    }
                    Toast.makeText(context, "파일을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        })

    @RequiresApi(Build.VERSION_CODES.Q)
    fun deleteAllRecords() {
        ioCoroutineScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var records = arrayListOf<Record>()
                try {
                    records = getAllRecord(context) as ArrayList<Record>
                    deleteRecords(context, records)
                    withContext(Dispatchers.Main) {
                        clearAllRecord()
                    }
                } catch (res: RecoverableSecurityException) {
                    val intentSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11 R부터는 createDeleteRequest를 통하여 다중삭제가 가능
                        MediaStore.createDeleteRequest(
                            context.contentResolver,
                            records.map { it.getRecordUri() }
                        ).intentSender
                    } else {
                        // Android 10 Q에서는 단일삭제만 가능 => 따라서 일일이 삭제를 해줘야함
                        res.userAction.actionIntent.intentSender
                    }

                    intentSender?.let { sender ->
                        context.startActivity(
                            RecorderDeleteModifyActivity.newIntent(
                                context = context,
                                intentSender = sender,
                                mode = RECORDER_MODE_ALL_DELETE,
                                records = records,
                                receiver = recorderResultReceiver
                            )
                        )
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun deleteRecords(
        context: Context,
        records: List<Record>
    ) {
        records.forEach { record ->
            context.contentResolver.delete(
                record.getRecordUri(),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(record.id)
            )
        }
    }

    fun clearAllRecord() {
        recordList.clear()
        notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun getAllRecord(context: Context): List<Record> {
        val listUrl = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE
        )

//    val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} >= ?" // SQL where 절
//    val selectionArgs = arrayOf(
//        "monkey_" // where의 조건칼럼
//    )

        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} like?" // SQL where 절
        val selectionArgs = arrayOf(
            "%MyRecordFolder%" // where의 조건칼럼, 외부저장소의 folder이름
        )
        val sortOrder = "${MediaStore.Audio.Media._ID} DESC"

        val cursor =
            context.contentResolver.query(listUrl, proj, selection, selectionArgs, sortOrder)
        val recordList = mutableListOf<Record>()

        while (cursor?.moveToNext() == true) {
            val id = cursor.getString(0)
            val displayName = cursor.getString(1)
            val duration = cursor.getLong(2)
            val createAt = cursor.getLong(3) * 1000

            val record = Record(id, displayName, duration, createAt)
            recordList.add(record)
        }

        cursor?.close()

        return recordList
    }

    private fun clearRecord(vararg record: Record) {
        val positions = IntArray(record.size)
        record.indices.forEach { i ->
            positions[i] = recordList.indexOf(record.elementAt(i))
        }
        recordList.removeAll(ArrayList(listOf(*record)))
        positions.indices.forEach { i ->
            notifyItemRemoved(positions[i])
        }
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun getRecordWithId(
        context: Context,
        id: String
    ): Record? {
        val listUrl = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE
        )
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(
            id // where의 조건칼럼
        )

        val cursor = context.contentResolver.query(listUrl, proj, selection, selectionArgs, null)
        return if (cursor?.moveToNext() == true) {
            val record = Record(
                id = cursor.getString(0),
                displayName = cursor.getString(1),
                duration = cursor.getLong(2),
                createAt = cursor.getLong(3) * 1000,
            )
            cursor.close()
            record
        } else {
            cursor?.close()
            null
        }
    }

    fun setNewRecordList(allList: List<Record>) {
        recordList.clear()
        recordList.addAll(allList)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = recordList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        return RecordViewHolder(
            ListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(recordList[position])
    }

    inner class RecordViewHolder(private val binding: ListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var record: Record? = null

        init {
            itemView.setOnClickListener {
                if (mediaPlayer != null) {
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
                mediaPlayer = MediaPlayer.create(itemView.context, record?.getRecordUri())
                mediaPlayer?.start()
            }

            binding.deleteButton.setOnClickListener {
                // todo 단일 삭제
                ioCoroutineScope.launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            record?.getRecordUri()?.let { uri ->
                                itemView.context.contentResolver.delete(uri, null, null)
                                withContext(Dispatchers.Main) {
                                    clearRecord(record!!)
                                    notifyItemRemoved(adapterPosition)
                                }
                            }
                        } catch (res: RecoverableSecurityException) {
                            val intentSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                MediaStore.createDeleteRequest(
                                    itemView.context.contentResolver,
                                    listOf(record?.getRecordUri())
                                ).intentSender
                            } else {
                                res.userAction.actionIntent.intentSender
                            }

                            intentSender?.let { sender ->
                                itemView.context.startActivity(
                                    RecorderDeleteModifyActivity.newIntent(
                                        context = itemView.context,
                                        intentSender = sender,
                                        mode = RECORDER_MODE_SINGLE_DELETE,
                                        records = arrayListOf(record!!),
                                        position = adapterPosition,
                                        receiver = recorderResultReceiver
                                    )
                                )
                            }
                        }
                    }
                }
            }

            binding.modifyButton.setOnClickListener {
                val alertDialog = AlertDialog.Builder(itemView.context)
                val editText = EditText(itemView.context)
                alertDialog.setTitle("파일명을 변경해주세요.")
                alertDialog.setView(editText)
                alertDialog.setPositiveButton("확인") { i, dialog ->
                    // 파일명 수정하기
                    val newRecordName = editText.text.toString()
                    ioCoroutineScope.launch {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                modifyRecord(itemView.context, record!!.getRecordUri(), newRecordName)
                                val newRecord = getRecordWithId(itemView.context, record!!.id)
                                newRecord?.let { record ->
                                    recordList[adapterPosition] = record
                                }
                                withContext(Dispatchers.Main) {
                                    notifyItemChanged(adapterPosition)
                                    Toast.makeText(itemView.context, "파일이름을 변경했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (res: RecoverableSecurityException) {
                                val intentSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    // Android 11 R부터는 createDeleteRequest를 통하여 다중수정이 가능
                                    MediaStore.createWriteRequest(
                                        context.contentResolver,
                                        listOf(record?.getRecordUri())
                                    ).intentSender
                                } else {
                                    // Android 10 Q에서는 단일수정만 가능
                                    res.userAction.actionIntent.intentSender
                                }

                                intentSender?.let { sender ->
                                    context.startActivity(
                                        RecorderDeleteModifyActivity.newIntent(
                                            context = context,
                                            intentSender = sender,
                                            mode = RECORDER_MODE_MODIFY,
                                            records = arrayListOf(record!!),
                                            position = adapterPosition,
                                            receiver = recorderResultReceiver,
                                            newFileName = newRecordName
                                        )
                                    )
                                }
                            }
                        }

                    }

                }
                alertDialog.setNegativeButton("취소") { _, _ -> }
                alertDialog.show()

            }

        }

        fun bind(record: Record) {
            binding.pathTextView.text = "${record.id} / ${record.displayName}"
            this.record = record
        }
    }
}
