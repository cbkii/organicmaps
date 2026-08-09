package app.organicmaps.background;

import android.content.Context;
import androidx.annotation.NonNull;
import app.organicmaps.BuildConfig;
import java.lang.reflect.Method;

/** Keeps shared application code independent of the optional WorkManager-backed worker. */
public final class OsmUploadScheduler
{
  private static final String WORKER_CLASS = "app.organicmaps.background.OsmUploadWork";

  private OsmUploadScheduler() {}

  public static void schedule(@NonNull Context context)
  {
    if (BuildConfig.IS_IN_CAR)
      return;

    try
    {
      final Class<?> worker = Class.forName(WORKER_CLASS);
      final Method schedule = worker.getMethod("startActionUploadOsmChanges", Context.class);
      schedule.invoke(null, context);
    }
    catch (ReflectiveOperationException e)
    {
      throw new IllegalStateException("OSM upload worker is unavailable", e);
    }
  }
}
