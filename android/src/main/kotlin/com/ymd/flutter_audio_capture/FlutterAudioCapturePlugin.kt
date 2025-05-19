package com.ymd.flutter_audio_capture

import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** FlutterAudioCapturePlugin */
class FlutterAudioCapturePlugin : FlutterPlugin, MethodCallHandler {
  private val methodChannelName = "ymd.dev/audio_capture_method_channel"
  private val audioCaptureStreamHandler: AudioCaptureStreamHandler = AudioCaptureStreamHandler()
  private val TAG: String = "FlutterAudioCapture"

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    val messenger = flutterPluginBinding.binaryMessenger
    EventChannel(messenger, this.audioCaptureStreamHandler.eventChannelName).setStreamHandler(this.audioCaptureStreamHandler)
    MethodChannel(messenger, methodChannelName).setMethodCallHandler(this)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "init" -> {
        result.success(true)
      }
      "getSampleRate" -> {
        result.success(this.audioCaptureStreamHandler.actualSampleRate.toDouble())
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    // You can clean up resources here if needed
  }
}
