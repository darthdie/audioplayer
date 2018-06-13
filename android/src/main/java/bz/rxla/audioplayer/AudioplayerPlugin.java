package bz.rxla.audioplayer;

import android.app.Activity;
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
 * AudioplayerPlugin
 */
public class AudioplayerPlugin implements MethodCallHandler {
  private final MethodChannel channel;
  private Activity activity;
  private static AudioManager am;
  private String lastUrl;
  private boolean isPaused;

  final Handler handler = new Handler();

  MediaPlayer mediaPlayer;
  // MediaSession session;
  // Bundle sessionExtras;

  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "bz.rxla.flutter/audio");
    channel.setMethodCallHandler(new AudioplayerPlugin(registrar.activity(), channel));
  }

  private AudioplayerPlugin(Activity activity, MethodChannel channel) {
    this.activity = activity;
    this.channel = channel;
    this.channel.setMethodCallHandler(this);
    if(AudioplayerPlugin.am == null) {
      AudioplayerPlugin.am = (AudioManager)activity.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

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
    if (call.method.equals("play")) {
      String url = ((HashMap) call.arguments()).get("url").toString();
      Boolean resPlay = play(url);
      response.success(1);
    } else if (call.method.equals("pause")) {
      pause();
      response.success(1);
    } else if (call.method.equals("stop")) {
      stop();
      response.success(1);
    } else if (call.method.equals("seek")) {
      double position = call.arguments();
      seek(position);
      response.success(1);
    } else if (call.method.equals("mute")) {
      Boolean muted = call.arguments();
      mute(muted);
      response.success(1);
    } else if (call.method.equals("setRate")) {
      double rate = call.arguments();
      setRate(rate);
      response.success(1);
    } else if (call.method.equals("load")) {
      String url = ((HashMap) call.arguments()).get("url").toString();
      load(url);
      response.success(1);
    } else {
      response.notImplemented();
    }
  }
 
 private void mute(Boolean muted) {
  if(AudioplayerPlugin.am == null) return;
  if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    AudioplayerPlugin.am.adjustStreamVolume(AudioManager.STREAM_MUSIC, muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
  } else {
    AudioplayerPlugin.am.setStreamMute(AudioManager.STREAM_MUSIC, muted);
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
    }
  }

  private void pause() {
    Log.d("AUDIO", "PAUSE");

    isPaused = true;
    
    mediaPlayer.pause();
    handler.removeCallbacks(sendData);
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
    Log.d("AUDIO", "PLAY");

    if (mediaPlayer == null) {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

      try {
        mediaPlayer.setDataSource(url);
      } catch (IOException e) {
        e.printStackTrace();
        Log.d("AUDIO", "invalid DataSource");
      }

      mediaPlayer.prepareAsync();
    } else {
      isPaused = false;

      channel.invokeMethod("audio.onDuration", mediaPlayer.getDuration());

      mediaPlayer.start();
      channel.invokeMethod("audio.onStart", true);
    }

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


    handler.post(sendData);

    return true;
  }

  private final Runnable sendData = new Runnable(){
    public void run(){
      try {
        if( ! mediaPlayer.isPlaying() ){
          handler.removeCallbacks(sendData);
        }
        int time = mediaPlayer.getCurrentPosition();
        channel.invokeMethod("audio.onCurrentPosition", time);

        handler.postDelayed(this, 200);
      }
      catch (Exception e) {
        e.printStackTrace();
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
