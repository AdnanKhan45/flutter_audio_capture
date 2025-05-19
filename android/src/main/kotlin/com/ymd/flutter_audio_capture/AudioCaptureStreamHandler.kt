package com.ymd.flutter_audio_capture

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.os.Handler
import android.os.Looper

import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.EventChannel.EventSink
import java.lang.Exception

public class AudioCaptureStreamHandler: StreamHandler {
    public val eventChannelName = "ymd.dev/audio_capture_event_channel"
    public var actualSampleRate: Int = 0
    
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    private var AUDIO_SOURCE: Int = MediaRecorder.AudioSource.DEFAULT
    private var SAMPLE_RATE: Int = 44000
    private val TAG: String = "AudioCaptureStream"
    private var isCapturing: Boolean = false
    private var listener = null
    private var thread: Thread? = null
    private var _events: EventSink? = null
    private val uiThreadHandler: Handler = Handler(Looper.getMainLooper())

    override fun onListen(arguments: Any?, events: EventSink?) {
        Log.d(TAG, "onListen started")
        if (arguments != null && arguments is Map<*, *>) {
            val sampleRate = arguments["sampleRate"]
            if (sampleRate != null && sampleRate is Int) {
                SAMPLE_RATE = sampleRate
            }
            val audioSource = arguments["audioSource"]
            if (audioSource != null && audioSource is Int) {
                AUDIO_SOURCE = audioSource
            }
        }
        
        this._events = events
        startRecording()
    }

    override fun onCancel(p0: Any?) {
        Log.d(TAG, "onListen canceled")
        stopRecording()
    }

    public fun startRecording() {
        if (thread != null) return

        isCapturing = true
        val runnableObj: Runnable = object: Runnable {
          override public fun run() {
              record()
          }
        }
        thread = Thread(runnableObj)
        thread?.start()
    }

    public fun stopRecording() {
        if (thread == null) return
        isCapturing = false
        // Log.d(TAG, "stopping recording, isCapturing = " + isCapturing)
        
        actualSampleRate = 1 // -> we are currently stopping
        thread?.join(5000)
        thread = null
        actualSampleRate = 2 // -> we are stopped
    }

    private fun sendError(key: String?, msg: String?) {
        uiThreadHandler.post(object: Runnable {
            override fun run() {
                if (isCapturing) {
                    _events?.error(key, msg, null)
                }
            }
        })
    }

    private fun sendBuffer(audioBuffer: ArrayList<FloatArray>, bufferIndex: Int) {
        uiThreadHandler.post(object: Runnable {
            var index: Int = -1

            override fun run() {
                if (isCapturing) {
                    // Send the actualSampleRate as a special event
                    val data = mapOf(
                        "actualSampleRate" to actualSampleRate.toDouble(),
                        "audioData" to audioBuffer[index]
                    )
                    _events?.success(data)
                }
            }

            public fun init(idx: Int): Runnable {
                this.index = idx
                return this
            }

        }.init(bufferIndex))
    }

    private fun record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

        val bufferSize = SAMPLE_RATE / 50 // 20ms buffer at 24kHz = 480 samples
        val audioBuffer = ShortArray(bufferSize)

        val record = AudioRecord.Builder()
            .setAudioSource(AUDIO_SOURCE)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .build()

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            sendError("AUDIO_RECORD_INITIALIZE_ERROR", "AudioRecord can't initialize")
            return
        }

        record.startRecording()
        actualSampleRate = record.sampleRate

        while (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Thread.yield()
        }

        while (isCapturing) {
            try {
                val readCount = record.read(audioBuffer, 0, audioBuffer.size, AudioRecord.READ_BLOCKING)
                if (readCount > 0) {
                    val pcmBytes = ByteArray(readCount * 2)
                    for (i in 0 until readCount) {
                        val value = audioBuffer[i].toInt()
                        pcmBytes[i * 2] = (value and 0xFF).toByte()
                        pcmBytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
                    }

                    uiThreadHandler.post {
                        if (isCapturing) {
                            _events?.success(pcmBytes)
                        }
                    }
                }
            } catch (e: Exception) {
                sendError("AUDIO_RECORD_READ_ERROR", e.message ?: "Unknown read error")
                Thread.yield()
            }
        }

        record.stop()
        record.release()
    }


}
