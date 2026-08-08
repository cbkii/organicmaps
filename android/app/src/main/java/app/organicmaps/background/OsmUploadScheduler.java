package app.organicmaps.background;

import android.content.Context;
import androidx.annotation.NonNull;

/** Keeps callers independent of the WorkManager-backed OSM upload worker implementation. */
public final class OsmUploadScheduler
{
  private OsmUploadScheduler() {}

  public static void schedule(@NonNull Context context)
  {
    OsmUploadWork.startActionUploadOsmChanges(context);
  }
}
