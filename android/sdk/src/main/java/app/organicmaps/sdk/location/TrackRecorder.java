package app.organicmaps.sdk.location;

import androidx.annotation.Keep;
import app.organicmaps.sdk.bookmarks.data.ElevationInfo;
import app.organicmaps.sdk.bookmarks.data.TrackStatistics;

public class TrackRecorder
{
  public static native void nativeStartTrackRecording();

  public static native void nativeStopTrackRecording();

  public static native void nativeSaveTrackRecordingWithName(String name);

  public static native boolean nativeIsTrackRecordingEmpty();

  public static native boolean nativeIsTrackRecordingEnabled();

  public static native void nativeSetAutoResumeFeatureEnabled(boolean enabled);

  public static native void nativeSetAutoResumeForCurrentRecording(boolean enabled);

  public static native void nativeSetTrackRecordingStatsListener(TrackRecorder.TrackRecordingUpdateHandler listener);

  public static native ElevationInfo nativeGetElevationInfo();

  /** Save any recorded points before an explicit stop. An empty track has nothing to save. */
  public static boolean saveAndStop()
  {
    nativeSetTrackRecordingStatsListener(null);
    nativeSetAutoResumeForCurrentRecording(false);
    final boolean saved = !nativeIsTrackRecordingEmpty();
    if (saved)
      nativeSaveTrackRecordingWithName("");
    nativeStopTrackRecording();
    return saved;
  }

  public interface TrackRecordingUpdateHandler
  {
    @Keep
    @SuppressWarnings("unused")
    void onTrackRecordingUpdate(TrackStatistics trackStatistics);
  }
}
