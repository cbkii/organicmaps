package app.organicmaps.sdk.sound;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import app.organicmaps.sdk.util.log.Logger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;

public class MediaPlayerWrapper
{
  private static final String TAG = MediaPlayerWrapper.class.getSimpleName();

  @NonNull
  private final Context mContext;
  @NonNull
  private final MediaPlayer mPlayer;
  @NonNull
  private final ArrayDeque<File> mQueuedFiles = new ArrayDeque<>();

  private boolean mIsInitialized;
  private boolean mIsPreparing;
  private String mStreamKey;
  private String mSequenceKey;

  public MediaPlayerWrapper(@NonNull Context context)
  {
    if (android.os.Build.VERSION.SDK_INT >= 30)
      mContext = context.createAttributionContext("media_playback");
    else
      mContext = context;
    mPlayer = new MediaPlayer();
    configurePlayer();
  }

  public void release()
  {
    stop();
    mPlayer.release();
  }

  public boolean playback(@RawRes int streamResId)
  {
    clearSequence();
    final String key = "res:" + streamResId;
    if (isActive(key))
    {
      startIfPrepared();
      return true;
    }
    return initialize(streamResId, key);
  }

  public boolean playback(@NonNull File file)
  {
    clearSequence();
    final String key = fileKey(file);
    if (isActive(key))
    {
      startIfPrepared();
      return true;
    }
    return initialize(file, key);
  }

  public boolean playback(@NonNull List<File> files)
  {
    if (files.isEmpty())
      return false;

    final String sequenceKey = sequenceKey(files);
    if (sequenceKey.equals(mSequenceKey) && (mIsPreparing || mIsInitialized))
      return true;

    mQueuedFiles.clear();
    mQueuedFiles.addAll(files);
    mSequenceKey = sequenceKey;
    return playNextQueuedFile();
  }

  public void stop()
  {
    try
    {
      mPlayer.stop();
    }
    catch (IllegalStateException ignored)
    {
      // The player may still be idle or preparing when the owning service stops.
    }
    clearPlaybackState();
    clearSequence();
  }

  private boolean initialize(@RawRes int streamResId, @NonNull String key)
  {
    resetForInitialization();
    try (final AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(streamResId))
    {
      if (afd == null)
        return false;
      mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
      return prepareAsync(key);
    }
    catch (IllegalStateException e)
    {
      Logger.w(TAG, "MediaPlayer illegal state while initializing", e);
    }
    catch (IllegalArgumentException e)
    {
      Logger.w(TAG, "AssetFileDescriptor is not a valid FileDescriptor", e);
    }
    catch (IOException e)
    {
      Logger.w(TAG, "AssetFileDescriptor cannot be read", e);
    }
    clearPlaybackState();
    return false;
  }

  private boolean initialize(@NonNull File file, @NonNull String key)
  {
    resetForInitialization();
    try
    {
      mPlayer.setDataSource(file.getAbsolutePath());
      return prepareAsync(key);
    }
    catch (IllegalStateException e)
    {
      Logger.w(TAG, "MediaPlayer illegal state while initializing file", e);
    }
    catch (IllegalArgumentException e)
    {
      Logger.w(TAG, "Offline navigation clip path is invalid", e);
    }
    catch (IOException e)
    {
      Logger.w(TAG, "Offline navigation clip cannot be read", e);
    }
    clearPlaybackState();
    return false;
  }

  private boolean playNextQueuedFile()
  {
    final File next = mQueuedFiles.pollFirst();
    if (next == null)
    {
      clearSequence();
      return false;
    }

    if (initialize(next, fileKey(next)))
      return true;

    clearSequence();
    return false;
  }

  private void resetForInitialization()
  {
    clearPlaybackState();
    mPlayer.reset();
    configurePlayer();
  }

  private void configurePlayer()
  {
    mPlayer.setAudioAttributes(AudioFocusManager.AUDIO_ATTRIBUTES);
    mPlayer.setOnPreparedListener(this::onPrepared);
    mPlayer.setOnCompletionListener(this::onCompletion);
    mPlayer.setOnErrorListener(this::onError);
  }

  private boolean prepareAsync(@NonNull String key)
  {
    mStreamKey = key;
    mIsPreparing = true;
    mPlayer.prepareAsync();
    return true;
  }

  private boolean isActive(@NonNull String key)
  {
    return key.equals(mStreamKey) && (mIsPreparing || mIsInitialized);
  }

  private void startIfPrepared()
  {
    if (!mIsInitialized)
      return;
    try
    {
      if (!mPlayer.isPlaying())
        mPlayer.start();
    }
    catch (IllegalStateException e)
    {
      Logger.w(TAG, "MediaPlayer cannot resume playback", e);
    }
  }

  private void onPrepared(@NonNull MediaPlayer unused)
  {
    mIsPreparing = false;
    mIsInitialized = true;
    try
    {
      mPlayer.start();
    }
    catch (IllegalStateException e)
    {
      Logger.w(TAG, "MediaPlayer cannot start after preparation", e);
      clearPlaybackState();
      clearSequence();
    }
  }

  private void onCompletion(@NonNull MediaPlayer unused)
  {
    clearPlaybackState();
    if (mSequenceKey != null)
      playNextQueuedFile();
  }

  private boolean onError(@NonNull MediaPlayer unused, int what, int extra)
  {
    Logger.w(TAG, "MediaPlayer playback error: what=" + what + ", extra=" + extra);
    clearPlaybackState();
    clearSequence();
    return true;
  }

  private void clearPlaybackState()
  {
    mIsInitialized = false;
    mIsPreparing = false;
    mStreamKey = null;
  }

  private void clearSequence()
  {
    mQueuedFiles.clear();
    mSequenceKey = null;
  }

  @NonNull
  private static String fileKey(@NonNull File file)
  {
    return "file:" + file.getAbsolutePath();
  }

  @NonNull
  private static String sequenceKey(@NonNull List<File> files)
  {
    final StringBuilder key = new StringBuilder("sequence:");
    for (File file : files)
      key.append(file.getAbsolutePath()).append('\n');
    return key.toString();
  }
}
