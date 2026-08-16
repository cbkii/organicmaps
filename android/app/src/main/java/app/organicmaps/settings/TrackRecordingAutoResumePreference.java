package app.organicmaps.settings;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.SwitchPreferenceCompat;
import app.organicmaps.sdk.location.TrackRecorder;

/** Keeps the Android preference and native restart gate in sync when the user changes the setting. */
public class TrackRecordingAutoResumePreference extends SwitchPreferenceCompat
{
  public TrackRecordingAutoResumePreference(@NonNull Context context)
  {
    super(context);
  }

  public TrackRecordingAutoResumePreference(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
  }

  public TrackRecordingAutoResumePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  public TrackRecordingAutoResumePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                                            int defStyleRes)
  {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void onClick()
  {
    super.onClick();
    // Turning this off only disarms restart persistence; it deliberately does not stop a recording
    // that is currently running in this process.
    TrackRecorder.nativeSetAutoResumeFeatureEnabled(isChecked());
  }
}
