package bz.rxla.audioplayer;

import android.media.AudioManager;
import android.media.MediaPlayer;
// import android.media.session.MediaSession;
// import android.media.session.MediaSessionCallback;
import android.media.MediaMetadata;
import android.os.Handler;
import android.util.Log;
import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

import android.content.Context;
import android.os.Build;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;

import android.app.Notification;
import android.app.NotificationManager;

import android.media.AudioAttributes;

/**
 * Android implementation for AudioPlayerPlugin.
 */
public class AudioplayerPlugin implements MethodCallHandler {
  private static final String ID = "bz.rxla.flutter/audio";
  private final MethodChannel channel;
  private Activity activity;
  private static AudioManager am;
  private String lastUrl;
  private boolean isPaused;

  private final Handler handler = new Handler();

  MediaPlayer mediaPlayer;
  // MediaSession session;
  // Bundle sessionExtras;

  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), ID);
    channel.setMethodCallHandler(new AudioplayerPlugin(registrar, channel));
  }

  private AudioplayerPlugin(Registrar registrar, MethodChannel channel) {
    this.registrar = registrar;
    this.channel = channel;
    this.channel.setMethodCallHandler(this);
    Context context = registrar.context().getApplicationContext();
    this.am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

    // session = new MediaSession(this, "FlutterAudioPlayer");  
    // session.setCallback(new MediaSessionCallback());  
    // session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

    // Intent intent = new Intent(activity.getApplicationContext(), AudioplayerPlugin.class);  
    // PendingIntent pi = PendingIntent.getActivity(activity.getApplicationContext(), 99 /*request code*/,
    //         intent, PendingIntent.FLAG_UPDATE_CURRENT);
    // session.setSessionActivity(pi);

    // sessionExtras = new Bundle();
    // session.setExtras(sessionExtras);
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result response) {
    switch (call.method) {
      case "play":
        play(call.argument("url").toString());
        response.success(null);
        break;
      case "pause":
        pause();
        response.success(null);
        break;
      case "stop":
        stop();
        response.success(null);
        break;
      case "seek":
        double position = call.arguments();
        seek(position);
        response.success(null);
        break;
      case "mute":
        Boolean muted = call.arguments();
        mute(muted);
        response.success(null);
        break;
      case "setRate":
        setRate(call.arguments());
        response.success(null);
        break;
      case "load":
        load(call.argument("url").toString());
        response.success(null);
        break;
      default:
        response.notImplemented();
    }
  }

  private void mute(Boolean muted) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      am.adjustStreamVolume(AudioManager.STREAM_MUSIC, muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
    } else {
      am.setStreamMute(AudioManager.STREAM_MUSIC, muted);
    }
  }

  private void seek(double position) {
    mediaPlayer.seekTo((int) (position * 1000));

    int time = mediaPlayer.getCurrentPosition();
    channel.invokeMethod("audio.onCurrentPosition", time);
  }

  private void setRate(double rate) {
    mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed((float)rate));

    if(isPaused) {
      pause();
    }
  }

  private void stop() {
    Log.d("AUDIO", "STOP");

    handler.removeCallbacks(sendData);
    if (mediaPlayer != null) {
      mediaPlayer.stop();
      mediaPlayer.reset();
      mediaPlayer.release();
      mediaPlayer = null;
      channel.invokeMethod("audio.onStop", null);
    }
  }

  private void pause() {
    handler.removeCallbacks(sendData);
    if (mediaPlayer != null) {
      isPaused = true;
      mediaPlayer.pause();
      channel.invokeMethod("audio.onPause", true);
    }
  }

  private void load(String url) {
    if (mediaPlayer != null) {
      stop();
    }

    mediaPlayer = new MediaPlayer();
    mediaPlayer.setAudioAttributes(
      new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.USAGE_MEDIA)
      .build()
    );

    try {
      mediaPlayer.setDataSource(url);
    } catch (IOException e) {
      e.printStackTrace();
      Log.d("AUDIO", "invalid DataSource");
    }

    mediaPlayer.prepareAsync();

    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){
      @Override
      public void onPrepared(MediaPlayer mp) {
        isPaused = false;

        channel.invokeMethod("audio.onDuration", mediaPlayer.getDuration());

        channel.invokeMethod("audio.onStart", true);
      }
    });

    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
      @Override
      public void onCompletion(MediaPlayer mp) {
        stop();
        channel.invokeMethod("audio.onComplete", true);
      }
    });

    mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener(){
      @Override
      public boolean onError(MediaPlayer mp, int what, int extra) {
        channel.invokeMethod("audio.onError", String.format("{\"what\":%d,\"extra\":%d}", what, extra));
        return true;
      }
    });

    handler.post(sendData);    
  }

  private Boolean play(String url) {
    if (mediaPlayer == null) {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

      try {
        mediaPlayer.setDataSource(url);
      } catch (IOException e) {
        Log.w(ID, "Invalid DataSource", e);
        channel.invokeMethod("audio.onError", "Invalid Datasource");
        return;
      }

      mediaPlayer.prepareAsync();

      mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){
        @Override
        public void onPrepared(MediaPlayer mp) {
          isPaused = false;

          channel.invokeMethod("audio.onDuration", mediaPlayer.getDuration());

          mediaPlayer.start();
          channel.invokeMethod("audio.onStart", true);
        }
      });

      mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
        @Override
        public void onCompletion(MediaPlayer mp) {
          stop();
          channel.invokeMethod("audio.onComplete", true);
        }
      });

      mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener(){
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
          channel.invokeMethod("audio.onError", String.format("{\"what\":%d,\"extra\":%d}", what, extra));
          return true;
        }
      });

      mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
        @Override
        public void onCompletion(MediaPlayer mp) {
          stop();
          channel.invokeMethod("audio.onComplete", null);
        }
      });

      mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener(){
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
          channel.invokeMethod("audio.onError", String.format("{\"what\":%d,\"extra\":%d}", what, extra));
          return true;
        }
      });
    } else {
      mediaPlayer.start();
      channel.invokeMethod("audio.onStart", mediaPlayer.getDuration());
    }

    handler.post(sendData);
  }

  private final Runnable sendData = new Runnable(){
    public void run(){
      try {
        if (!mediaPlayer.isPlaying()) {
          handler.removeCallbacks(sendData);
        }
        int time = mediaPlayer.getCurrentPosition();
        channel.invokeMethod("audio.onCurrentPosition", time);
        handler.postDelayed(this, 200);
      }
      catch (Exception e) {
        Log.w(ID, "When running handler", e);
      }
    }
  };

  // private Notification createNotification() {  
  //   if (mMetadata == null || mPlaybackState == null) {
  //       return null;
  //   }

  //   Notification.Builder notificationBuilder = new Notification.Builder(mService);
  //   int playPauseButtonPosition = 0;

  //   // If skip to previous action is enabled
  //   // if ((mPlaybackState.getActions() & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
  //   //     notificationBuilder.addAction(R.drawable.ic_prev_gray,
  //   //             mService.getString(R.string.label_previous), mPreviousIntent);

  //   //     // If there is a "skip to previous" button, the play/pause button will
  //   //     // be the second one. We need to keep track of it, because the MediaStyle notification
  //   //     // requires to specify the index of the buttons (actions) that should be visible
  //   //     // when in compact view.
  //   //     playPauseButtonPosition = 1;
  //   // }

  //   addPlayPauseAction(notificationBuilder);

  //   // If skip to next action is enabled
  //   // if ((mPlaybackState.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
  //   //     notificationBuilder.addAction(R.drawable.ic_next_gray,
  //   //             mService.getString(R.string.label_next), mNextIntent);
  //   // }

  //   MediaDescription description = mMetadata.getDescription();

  //   // String fetchArtUrl = null;
  //   // Bitmap art = null;
  //   // if (description.getIconUri() != null) {
  //   //     // This sample assumes the iconUri will be a valid URL formatted String, but
  //   //     // it can actually be any valid Android Uri formatted String.
  //   //     // async fetch the album art icon
  //   //     String artUrl = description.getIconUri().toString();
  //   //     if (art == null) {
  //   //         fetchArtUrl = artUrl;
  //   //         // use a placeholder art while the remote art is being downloaded
  //   //         art = BitmapFactory.decodeResource(mService.getResources(), R.mipmap.ic_launcher);
  //   //     }
  //   // }

  //   notificationBuilder
  //     .setStyle(new Notification.MediaStyle()
  //         .setShowActionsInCompactView(new int[]{playPauseButtonPosition})  // show only play/pause in compact view
  //         .setMediaSession(mSessionToken))
  //     .setColor(mNotificationColor)
  //     .setSmallIcon(R.drawable.ic_notification)
  //     .setVisibility(Notification.VISIBILITY_PUBLIC)
  //     .setUsesChronometer(true)
  //     .setContentIntent(createContentIntent(description)) // Create an intent that would open the UI when user clicks the notification
  //     .setContentTitle(description.getTitle())
  //     .setContentText(description.getSubtitle());
  //     //.setLargeIcon(art);

  //   setNotificationPlaybackState(notificationBuilder);
  //   // if (fetchArtUrl != null) {
  //   //     fetchBitmapFromURLAsync(fetchArtUrl, notificationBuilder);
  //   // }

  //   return notificationBuilder.build();
  // }

  // private void addPlayPauseAction(Notification.Builder builder) {  
  //   String label;
  //   int icon;
  //   PendingIntent intent;
  //   if (mPlaybackState.getState() == PlaybackState.STATE_PLAYING) {
  //       label = mService.getString(R.string.label_pause);
  //       icon = R.drawable.ic_pause_green;
  //       intent = mPauseIntent;
  //   } else {
  //       label = mService.getString(R.string.label_play);
  //       icon = R.drawable.ic_play_gray;
  //       intent = mPlayIntent;
  //   }
  //   builder.addAction(new Notification.Action(icon, label, intent));
  // }
}
