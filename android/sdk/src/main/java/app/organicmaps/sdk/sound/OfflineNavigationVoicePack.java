package app.organicmaps.sdk.sound;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.NavigationNotification;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.concurrency.ThreadPool;
import app.organicmaps.sdk.util.log.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Lightweight InCar navigation fallback used only when system TTS cannot speak. */
public final class OfflineNavigationVoicePack
{
  private static final String TAG = OfflineNavigationVoicePack.class.getSimpleName();
  private static final String PREFERENCE_FILE_SUFFIX = "_preferences";
  private static final String PREFERENCE_MODE_KEY = "pref_in_car_navigation_fallback_mode";
  private static final String LEGACY_PREFERENCE_KEY = "pref_in_car_offline_navigation_voice_pack";

  private static final String PACK_ASSET_PATH = "offline_navigation_voice_pack_v2/pack.zip";
  private static final long PACK_EXPECTED_SIZE = 48_544L;
  private static final String PACK_EXPECTED_SHA256 = "effdbacbd9984382f578602123f7f12409425125b0babcce088cc3dce490ffc1";
  private static final String CACHE_DIRECTORY =
      "offline_navigation_voice_pack_" + PACK_EXPECTED_SHA256.substring(0, 12);
  private static final String COMPLETE_MARKER = ".complete";
  private static final Object EXTRACTION_LOCK = new Object();
  @Nullable
  private static volatile File sCacheDirectory;
  private static boolean sExtractionStarted;

  private static final Set<String> PACK_CLIPS = new HashSet<>(Arrays.asList(
      "02_navigation_started_lets_roll.ogg", "05_continue_straight_nice_easy.ogg", "07_turn_left.ogg",
      "08_turn_right.ogg", "09_bear_left_stay_cool.ogg", "10_bear_right_easy_now.ogg", "13_make_u_turn.ogg",
      "15_take_first_exit.ogg", "16_take_second_exit.ogg", "17_take_third_exit.ogg", "18_take_fourth_exit.ogg",
      "19_exit_left.ogg", "20_exit_right.ogg", "26_take_next_exit.ogg", "36_you_made_it_irie.ogg", "39_way_updated.ogg",
      "40_gps_lost.ogg", "41_gps_restored.ogg", "47_sharp_turn.ogg"));

  public enum Mode
  {
    OFF("off"),
    VOICE("voice"),
    TONE_ALERTS("tone_alerts"),
    TONE_ALL("tone_all");

    @NonNull
    private final String mPreferenceValue;

    Mode(@NonNull String preferenceValue)
    {
      mPreferenceValue = preferenceValue;
    }

    @NonNull
    public String getPreferenceValue()
    {
      return mPreferenceValue;
    }

    @NonNull
    public static Mode fromPreferenceValue(@Nullable String value)
    {
      for (Mode mode : values())
        if (mode.mPreferenceValue.equals(value))
          return mode;
      return TONE_ALL;
    }
  }

  public enum Cue
  {
    MANEUVER,
    ROUTE_UPDATED,
    GPS_LOST,
    GPS_RESTORED
  }

  public enum GpsSignalEvent
  {
    NONE,
    LOST,
    RESTORED
  }

  /** Converts location callbacks into one-shot lost/restored transitions. */
  public static final class GpsSignalState
  {
    private boolean mUnavailable;

    @NonNull
    public GpsSignalEvent onUnavailable()
    {
      if (mUnavailable)
        return GpsSignalEvent.NONE;
      mUnavailable = true;
      return GpsSignalEvent.LOST;
    }

    @NonNull
    public GpsSignalEvent onLocationUpdated()
    {
      if (!mUnavailable)
        return GpsSignalEvent.NONE;
      mUnavailable = false;
      return GpsSignalEvent.RESTORED;
    }
  }

  private OfflineNavigationVoicePack() {}

  @NonNull
  public static Mode getMode(@NonNull Context context)
  {
    final SharedPreferences preferences = preferences(context);
    final boolean hasMode = preferences.contains(PREFERENCE_MODE_KEY);
    final String modeValue = hasMode ? preferences.getString(PREFERENCE_MODE_KEY, null) : null;
    final Boolean legacyEnabled =
        preferences.contains(LEGACY_PREFERENCE_KEY) ? preferences.getBoolean(LEGACY_PREFERENCE_KEY, true) : null;
    return resolveMode(hasMode, modeValue, legacyEnabled);
  }

  @NonNull
  static Mode resolveMode(boolean hasMode, @Nullable String modeValue, @Nullable Boolean legacyEnabled)
  {
    if (hasMode)
      return Mode.fromPreferenceValue(modeValue);
    if (legacyEnabled != null)
      return legacyEnabled ? Mode.VOICE : Mode.OFF;
    return Mode.TONE_ALL;
  }

  public static void setMode(@NonNull Context context, @NonNull Mode mode)
  {
    preferences(context)
        .edit()
        .putString(PREFERENCE_MODE_KEY, mode.getPreferenceValue())
        .remove(LEGACY_PREFERENCE_KEY)
        .apply();
  }

  public static boolean isFallbackEnabled(@NonNull Context context)
  {
    return Config.isInCar() && getMode(context) != Mode.OFF;
  }

  public static boolean hasNotifications(@Nullable String[] notifications)
  {
    return notifications != null && notifications.length > 0;
  }

  public static boolean shouldPlayTone(@NonNull Mode mode, boolean hasNotifications, boolean criticalEvent)
  {
    return switch (mode)
    {
      case TONE_ALERTS -> criticalEvent;
      case TONE_ALL -> hasNotifications || criticalEvent;
      default -> false;
    };
  }

  public static boolean shouldPlayVoiceCue(@NonNull NavigationNotification.Event event,
                                           @NonNull NavigationNotification.Stage stage)
  {
    return event == NavigationNotification.Event.ROUTE_RECALCULATION
 || (event == NavigationNotification.Event.MANEUVER && stage == NavigationNotification.Stage.IMMEDIATE);
  }

  public static boolean isCriticalEvent(@Nullable RoutingInfo routingInfo)
  {
    // This typed signal currently represents the speed-camera warning path.
    return routingInfo != null && routingInfo.shouldPlayWarningSignal();
  }

  @NonNull
  public static List<File> resolveCurrentCues(@NonNull Context context, @Nullable RoutingInfo routingInfo,
                                              @NonNull Cue cue)
  {
    final CarDirection direction = routingInfo == null ? null : routingInfo.carDirection;
    final int exitNumber = routingInfo == null ? 0 : routingInfo.exitNum;
    final List<String> names = selectClipNames(direction, exitNumber, cue);
    if (names.isEmpty())
      return Collections.emptyList();

    final File cacheDirectory = cacheDirectoryAsync(context);
    if (cacheDirectory == null)
      return Collections.emptyList();

    final List<File> clips = new ArrayList<>(names.size());
    for (String name : names)
    {
      final File clip = new File(cacheDirectory, name);
      if (!clip.isFile() || clip.length() == 0)
      {
        Logger.w(TAG, "Offline navigation cue is unavailable: " + name);
        return Collections.emptyList();
      }
      clips.add(clip);
    }
    return clips;
  }

  @NonNull
  static List<String> selectClipNames(@Nullable CarDirection direction, int exitNumber, @NonNull Cue cue)
  {
    switch (cue)
    {
    case ROUTE_UPDATED: return Collections.singletonList("39_way_updated.ogg");
    case GPS_LOST: return Collections.singletonList("40_gps_lost.ogg");
    case GPS_RESTORED: return Collections.singletonList("41_gps_restored.ogg");
    case MANEUVER: break;
    }

    if (direction == null)
      return Collections.emptyList();

    return switch (direction)
    {
      case StartAtEndOfStreet ->
        Arrays.asList("02_navigation_started_lets_roll.ogg", "05_continue_straight_nice_easy.ogg");
      case NoTurn, GoStraight -> Collections.singletonList("05_continue_straight_nice_easy.ogg");
      case TurnRight -> Collections.singletonList("08_turn_right.ogg");
      case TurnSharpRight -> Arrays.asList("47_sharp_turn.ogg", "08_turn_right.ogg");
      case TurnSlightRight -> Collections.singletonList("10_bear_right_easy_now.ogg");
      case TurnLeft -> Collections.singletonList("07_turn_left.ogg");
      case TurnSharpLeft -> Arrays.asList("47_sharp_turn.ogg", "07_turn_left.ogg");
      case TurnSlightLeft -> Collections.singletonList("09_bear_left_stay_cool.ogg");
      case UTurnLeft, UTurnRight -> Collections.singletonList("13_make_u_turn.ogg");
      case EnterRoundAbout, LeaveRoundAbout, StayOnRoundAbout ->
        Collections.singletonList(selectRoundaboutClip(exitNumber));
      case ReachedYourDestination -> Collections.singletonList("36_you_made_it_irie.ogg");
      case ExitHighwayToLeft -> Collections.singletonList("19_exit_left.ogg");
      case ExitHighwayToRight -> Collections.singletonList("20_exit_right.ogg");
    };
  }

  static boolean containsClip(@NonNull String name)
  {
    return PACK_CLIPS.contains(name);
  }

  @NonNull
  private static String selectRoundaboutClip(int exitNumber)
  {
    return switch (exitNumber)
    {
      case 1 -> "15_take_first_exit.ogg";
      case 2 -> "16_take_second_exit.ogg";
      case 3 -> "17_take_third_exit.ogg";
      case 4 -> "18_take_fourth_exit.ogg";
      default -> "26_take_next_exit.ogg";
    };
  }

  @Nullable
  private static File cacheDirectoryAsync(@NonNull Context context)
  {
    final File ready = sCacheDirectory;
    if (ready != null)
      return ready;

    synchronized (EXTRACTION_LOCK)
    {
      if (sExtractionStarted)
        return null;
      sExtractionStarted = true;
    }

    final Context applicationContext = context.getApplicationContext();
    final Context extractionContext = applicationContext == null ? context : applicationContext;
    ThreadPool.getStorage().execute(() -> {
      final File extracted = ensureExtracted(extractionContext);
      synchronized (EXTRACTION_LOCK)
      {
        sCacheDirectory = extracted;
        sExtractionStarted = false;
      }
    });
    return null;
  }

  @Nullable
  private static File ensureExtracted(@NonNull Context context)
  {
    final File cacheDirectory = new File(context.getCacheDir(), CACHE_DIRECTORY);
    if (isCompleteCache(cacheDirectory))
      return cacheDirectory;

    synchronized (EXTRACTION_LOCK)
    {
      if (isCompleteCache(cacheDirectory))
        return cacheDirectory;

      deleteRecursively(cacheDirectory);
      if (!cacheDirectory.mkdirs() && !cacheDirectory.isDirectory())
      {
        Logger.w(TAG, "Cannot create offline navigation voice cache");
        return null;
      }

      final File archive = new File(cacheDirectory, "pack.zip");
      try
      {
        reconstructArchive(context, archive);
        extractArchive(archive, cacheDirectory);

        final File marker = new File(cacheDirectory, COMPLETE_MARKER);
        if (!marker.createNewFile() && !marker.isFile())
          throw new IOException("Cannot create completion marker");
        return cacheDirectory;
      }
      catch (IOException | GeneralSecurityException e)
      {
        Logger.w(TAG, "Cannot prepare offline navigation voice pack", e);
        deleteRecursively(cacheDirectory);
        return null;
      }
      finally
      {
        if (archive.exists() && !archive.delete())
          Logger.w(TAG, "Cannot delete reconstructed voice archive");
      }
    }
  }

  private static void reconstructArchive(@NonNull Context context, @NonNull File archive)
      throws IOException, GeneralSecurityException
  {
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    long size = 0;
    final byte[] buffer = new byte[8 * 1024];

    try (InputStream input = context.getAssets().open(PACK_ASSET_PATH);
         FileOutputStream output = new FileOutputStream(archive))
    {
      int count;
      while ((count = input.read(buffer)) != -1)
      {
        output.write(buffer, 0, count);
        digest.update(buffer, 0, count);
        size += count;
      }
      output.getFD().sync();
    }

    final String actualDigest = toHex(digest.digest());
    if (size != PACK_EXPECTED_SIZE || !PACK_EXPECTED_SHA256.equals(actualDigest))
      throw new IOException("Offline navigation voice pack integrity mismatch: size=" + size
                            + ", sha256=" + actualDigest);
  }

  private static void extractArchive(@NonNull File archive, @NonNull File cacheDirectory) throws IOException
  {
    final Set<String> extracted = new HashSet<>();
    final byte[] buffer = new byte[8 * 1024];

    try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive)))
    {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null)
      {
        final String name = entry.getName();
        if (entry.isDirectory() || !PACK_CLIPS.contains(name) || !extracted.add(name))
          throw new IOException("Unexpected or duplicate offline navigation voice entry: " + name);

        final File output = new File(cacheDirectory, name);
        try (FileOutputStream stream = new FileOutputStream(output))
        {
          int count;
          while ((count = zip.read(buffer)) != -1)
            stream.write(buffer, 0, count);
          stream.getFD().sync();
        }
        zip.closeEntry();
      }
    }

    if (!extracted.equals(PACK_CLIPS))
      throw new IOException("Offline navigation voice pack is incomplete");
  }

  private static boolean isCompleteCache(@NonNull File cacheDirectory)
  {
    if (!new File(cacheDirectory, COMPLETE_MARKER).isFile())
      return false;
    for (String name : PACK_CLIPS)
    {
      final File clip = new File(cacheDirectory, name);
      if (!clip.isFile() || clip.length() == 0)
        return false;
    }
    return true;
  }

  @NonNull
  private static String toHex(@NonNull byte[] bytes)
  {
    final StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes)
      result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
    return result.toString();
  }

  @NonNull
  private static SharedPreferences preferences(@NonNull Context context)
  {
    return context.getSharedPreferences(context.getPackageName() + PREFERENCE_FILE_SUFFIX, Context.MODE_PRIVATE);
  }

  private static void deleteRecursively(@NonNull File file)
  {
    final File[] children = file.listFiles();
    if (children != null)
      for (File child : children)
        deleteRecursively(child);
    if (file.exists() && !file.delete())
      Logger.w(TAG, "Cannot delete stale offline navigation voice cache: " + file);
  }
}
