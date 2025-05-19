// ✅ flutter_audio_capture.dart (optimized for PCM16 Uint8List)

import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/services.dart';

const AUDIO_CAPTURE_EVENT_CHANNEL_NAME = "ymd.dev/audio_capture_event_channel";
const AUDIO_CAPTURE_METHOD_CHANNEL_NAME = "ymd.dev/audio_capture_method_channel";

const ANDROID_AUDIOSRC_DEFAULT = 0;
const ANDROID_AUDIOSRC_MIC = 1;
const ANDROID_AUDIOSRC_CAMCORDER = 5;
const ANDROID_AUDIOSRC_VOICERECOGNITION = 6;
const ANDROID_AUDIOSRC_VOICECOMMUNICATION = 7;
const ANDROID_AUDIOSRC_UNPROCESSED = 9;

class FlutterAudioCapture {
  static const _audioCaptureEventChannel = EventChannel(AUDIO_CAPTURE_EVENT_CHANNEL_NAME);
  static const _audioCaptureMethodChannel = MethodChannel(AUDIO_CAPTURE_METHOD_CHANNEL_NAME);

  StreamSubscription? _audioCaptureEventChannelSubscription;
  bool? _initialized;

  Future<bool?> init() async {
    if (_initialized != null) return _initialized;
    _initialized = await _audioCaptureMethodChannel.invokeMethod<bool>("init");
    return _initialized;
  }

  Future<void> start(
      void Function(Uint8List) listener,
      Function onError, {
        int sampleRate = 24000,
        int bufferSize = 480,
        int androidAudioSource = ANDROID_AUDIOSRC_DEFAULT,
        Duration firstDataTimeout = const Duration(seconds: 1),
        bool waitForFirstDataOnAndroid = true,
        bool waitForFirstDataOnIOS = false,
      }) async {
    if (_initialized == null) throw Exception("FlutterAudioCapture must be initialized before use");
    if (_initialized == false) throw Exception("FlutterAudioCapture failed to initialize");
    if (_audioCaptureEventChannelSubscription != null) return;

    final stream = _audioCaptureEventChannel.receiveBroadcastStream({
      "sampleRate": sampleRate,
      "bufferSize": bufferSize,
      "audioSource": androidAudioSource,
    }).cast<Uint8List>();

    final waitForFirstData = (Platform.isAndroid && waitForFirstDataOnAndroid) ||
        (Platform.isIOS && waitForFirstDataOnIOS);

    _audioCaptureEventChannelSubscription = stream.listen(listener, onError: onError);

    if (waitForFirstData) {
      try {
        await stream.firstWhere((element) => element.isNotEmpty).timeout(firstDataTimeout);
      } catch (e) {
        await stop();
        rethrow;
      }
    }
  }

  Future<void> stop() async {
    if (_audioCaptureEventChannelSubscription == null) return;
    final temp = _audioCaptureEventChannelSubscription;
    _audioCaptureEventChannelSubscription = null;
    await temp!.cancel();
  }
}
