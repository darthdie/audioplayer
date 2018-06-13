import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

typedef void TimeChangeHandler(Duration duration);
typedef void ErrorHandler(String message);

class AudioPlayer {
  final MethodChannel channel =
      new MethodChannel('bz.rxla.flutter/audio');

  TimeChangeHandler durationHandler;
  TimeChangeHandler positionHandler;
  VoidCallback startHandler;
  VoidCallback completionHandler;
  ErrorHandler errorHandler;

  // TODO ? use a notifier ?...
  //ValueNotifier<Duration> durationNotifier;

  AudioPlayer() {
    channel.setMethodCallHandler(platformCallHandler);
    //durationNotifier = new ValueNotifier(new Duration());
  }

  Future<dynamic> play(String url, {bool isLocal: false}) =>
      channel.invokeMethod('play', {"url": url, "isLocal": isLocal});

  Future<dynamic> load(String url, {bool isLocal: false}) =>
      channel.invokeMethod('load', {"url": url, "isLocal": isLocal});

  Future<dynamic> pause() => channel.invokeMethod('pause');

  Future<dynamic> resume() => channel.invokeMethod('resume');

  Future<dynamic> stop() => channel.invokeMethod('stop');

  Future<dynamic> mute(bool muted) => channel.invokeMethod('mute', muted);

  Future<dynamic> seek(double seconds) => channel.invokeMethod('seek', seconds);

  Future<dynamic> setRate(double rate) => channel.invokeMethod('setRate', rate);

  void setDurationHandler(TimeChangeHandler handler) {
    durationHandler = handler;
  }

  void setPositionHandler(TimeChangeHandler handler) {
    positionHandler = handler;
  }


  void setStartHandler(VoidCallback callback) {
    startHandler = callback;
  }

  void setCompletionHandler(VoidCallback callback) {
    completionHandler = callback;
  }

  void setErrorHandler(ErrorHandler handler) {
    errorHandler = handler;
  }

  Future platformCallHandler(MethodCall call) async {
    //    print("_platformCallHandler call ${call.method} ${call.arguments}");
    switch (call.method) {
      case "audio.onDuration":
        final duration = new Duration(milliseconds: call.arguments);
        if (durationHandler != null) {
          durationHandler(duration);
        }
        //durationNotifier.value = duration;
        break;
      case "audio.onCurrentPosition":
        if (positionHandler != null) {
          positionHandler(new Duration(milliseconds: call.arguments));
        }
        break;
      case "audio.onStart":
        if (startHandler != null) {
          startHandler();
        }
        break;
      case "audio.onComplete":
        if (completionHandler != null) {
          completionHandler();
        }
        break;
      case "audio.onError":
        if (errorHandler != null) {
          errorHandler(call.arguments);
        }
        break;
      default:
        print('Unknowm method ${call.method} ');
    }
  }
}
