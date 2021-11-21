package com.dontsu.samplerecordingwithmediastore2

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import com.dontsu.samplerecordingwithmediastore2.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

const val DELAY_INTERVAL = 1000L

class MainActivity : AppCompatActivity() {

    /** Binding */
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    /** Handler */
    private val handler by lazy {
        binding.root.handler
    }

    /** Runnable */
    private val updateUIRunnable = object : Runnable {
        override fun run() {
            updateTimeCount()
            handler.postDelayed(this, DELAY_INTERVAL)
        }
    }

    /** recorder */
    private var recorder: MediaRecorder? = null
    private val recordingFilePath: String by lazy {
        "${externalCacheDir?.absolutePath}/recording${SystemClock.elapsedRealtime()}.mp4" // 녹음한것을 저장할 path
    }


    private var startTimeStamp = 0L // it's like a pauseOffset

    /** State */
    private var timeState = TimeState.STOP // initialize with STOP
    private var recordingState = RecordingState.BEFORE_RECORDING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        requestAudioPermission()
        initListeners()

    }

    private fun requestAudioPermission() {
        requestPermissions(permissions, REQUEST_RECORD_AUDIO_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val audioRecordPermissionGranted = requestCode == REQUEST_RECORD_AUDIO_PERMISSION &&
                grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (!audioRecordPermissionGranted) {
            finish()
        }

    }

    private fun initListeners() = with(binding) {
        startBtn.setOnClickListener {
            if (timeState == TimeState.STOP) {
                startTimeCount()
                startRecording()
                timeState = TimeState.START
                Toast.makeText(this@MainActivity, "START", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (timeState == TimeState.START) {
                Toast.makeText(this@MainActivity, "it's already started.", Toast.LENGTH_SHORT).show()
            }
        }

        stopBtn.setOnClickListener {
            if (timeState == TimeState.START) {
                stopTimeCount()
                stopRecording()
                timeState = TimeState.STOP
                Toast.makeText(this@MainActivity, "STOP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (timeState == TimeState.STOP) {
                Toast.makeText(this@MainActivity, "it's already stopped.", Toast.LENGTH_SHORT).show()
            }
        }

        listButton.setOnClickListener {
            startActivity(ListActivity.newIntent(this@MainActivity))
        }

    }

    private fun startTimeCount() {
        startTimeStamp = SystemClock.elapsedRealtime()
        handler?.post(updateUIRunnable)
    }

    @SuppressLint("SetTextI18n")
    private fun stopTimeCount() {
        binding.timeTextView.text = "00:00"
        startTimeStamp = 0L
        handler?.removeCallbacks(updateUIRunnable)
    }

    @SuppressLint("SetTextI18n")
    private fun updateTimeCount() {
        val currentTimeStamp = SystemClock.elapsedRealtime()
        val countTimeSeconds = ((currentTimeStamp - startTimeStamp) / 1000L).toInt()
        val minutes = countTimeSeconds / 60
        val seconds = countTimeSeconds % 60
        binding.timeTextView.text = "%02d:%02d".format(minutes, seconds)
    }

    private fun startRecording() {
        // MediaRecorder 공식문서를 참조하여 각각의 상태를 확인
        recorder = MediaRecorder()
            .apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFilePath)
                prepare()
            }
        recorder?.start()
        recordingState = RecordingState.ON_RECORDING
    }

    private fun stopRecording() {
        recorder?.run {
            stop()
            release() // 메모리 해제
            saveToMediaStore()
        }
        recorder = null
        recordingState = RecordingState.AFTER_RECORDING
    }

    private fun saveToMediaStore() {
        val recordingFile = File(recordingFilePath)

        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA)
        val tempFileName = "myrecording_${sdf.format(Date())}.mp3"

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, tempFileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp3")
            put(MediaStore.Audio.Media.DATE_ADDED, Date().time)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1) // 1로 설정할 경우 보류중인 파일로 구분되어 다른 앱에 바로 노출되지 않아 다른 앱의 요청을 무시할 수 있음
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MyRecordFolder/")
            }
        }

        // MediaStore에 파일을 등록하고, 등록된 Uri를 item 변수에 저장
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)

        // 파일디스크럽터를 열고 descriptor 변수에 저장하기. 파일디스크럽터로 파일을 읽거나 쓸 수 있다
        if (uri != null) {
            contentResolver.openFileDescriptor(uri, "w" ,null).use {  descriptor ->
                descriptor?.let {
                    val fos = FileOutputStream(descriptor.fileDescriptor)
                    val bytes = recordingFile.readBytes()
                    fos.write(bytes)
                    fos.close()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    }
                }
            }
        }

    }

    enum class TimeState {
        START,
        STOP
    }

    enum class RecordingState {
        BEFORE_RECORDING,
        ON_RECORDING,
        AFTER_RECORDING,
        ON_PLAYING
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 201

        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )

    }

}