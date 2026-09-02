package app.organicmaps.sdk.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Config
{
  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private static SharedPreferences mPrefs;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private static String mFlavor;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private static String mApplicationId;

  private static int mVersionCode;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private static String mVersionName;

  private static final String KEY_APP_STORAGE = "StoragePath";

  private static final String KEY_DOWNLOADER_AUTO = "AutoDownloadEnabled";
  private static final String KEY_PREF_ZOOM_BUTTONS = "ZoomButtonsEnabled";
  private static final String KEY_AUTO_START_LOCATION_FOLLOW_AND_ROTATE = "AutoStartLocationFollowAndRotate";
  private static final String KEY_PREF_IN_CAR_FREE_DRIVING_AUTO_ZOOM = "InCarFreeDrivingAutoZoom";
  private static final String KEY_PREF_IN_CAR_OPTIMISED_VISUALS = "InCarOptimisedVisuals";
  static final String KEY_PREF_STATISTICS = "StatisticsEnabled";
  private static final String KEY_PREF_USE_GS = "UseGoogleServices";

  private static final String KEY_MISC_DISCLAIMER_ACCEPTED = "IsDisclaimerApproved";

  private static final String KEY_MISC_LOCATION_REQUESTED = "LocationRequested";
  private static final String KEY_MISC_USE_MOBILE_DATA = "UseMobileData";
  private static final String KEY_MISC_USE_MOBILE_DATA_TIMESTAMP = "UseMobileDataTimestamp";
  private static final String KEY_MISC_USE_MOBILE_DATA_ROAMING = "UseMobileDataRoaming";
  private static final String KEY_MISC_KEEP_SCREEN_ON = "KeepScreenOn";

  private static final String KEY_MISC_SHOW_ON_LOCK_SCREEN = "ShowOnLockScreen";
  private static final String KEY_MISC_AGPS_TIMESTAMP = "AGPSTimestamp";
  private static final String KEY_DONATE_URL = "DonateUrl";
  private static final String KEY_PREF_SEARCH_HISTORY = "SearchHistoryEnabled";

  public static final String KEY_PREF_LAST_SEARCHED_TAB = "LastSearchTab";

  /**
   * The total number of app launches.
   */
  private static final String KEY_APP_LAUNCH_NUMBER = "LaunchNumber";
  /**
   * The timestamp for the most recent app launch.
   */
  private static final String KEY_APP_LAST_SESSION_TIMESTAMP = "LastSessionTimestamp";
  /**
   * The version code of the first installed version of the app.
   */
  private static final String KEY_APP_FIRST_INSTALL_VERSION_CODE = "FirstInstallVersion";
  /**
   * The version code of the last launched version of the app.
   */
  private static final String KEY_APP_LAST_INSTALL_VERSION_CODE = "LastInstallVersion";
  /**
   * True if the first start animation has been seen.
   */
  private static final String KEY_MISC_FIRST_START_DIALOG_SEEN = "FirstStartDialogSeen";

  private Config() {}

  private static boolean isFdroid()
  {
    return mFlavor.equals("fdroid");
  }

  public static boolean isInCar()
  {
    return mFlavor.equals("inCar");
  }

  private static int getInt(String key, int def)
  {
    return nativeGetInt(key, def);
  }

  private static long getLong(String key, long def)
  {
    return nativeGetLong(key, def);
  }

  private static float getFloat(@NonNull final String key, final float def)
  {
    return (float) nativeGetDouble(key, def);
  }

  @NonNull
  private static String getString(String key)
  {
    return getString(key, "");
  }

  @NonNull
  private static String getString(String key, String def)
  {
    return nativeGetString(key, def);
  }

  private static boolean getBool(String key)
  {
    return getBool(key, false);
  }

  private static boolean getBool(String key, boolean def)
  {
    return nativeGetBoolean(key, def);
  }

  private static void setInt(String key, int value)
  {
    nativeSetInt(key, value);
  }

  private static void setLong(String key, long value)
  {
    nativeSetLong(key, value);
  }

  private static void setFloat(@NonNull final String key, final float value)
  {
    nativeSetDouble(key, value);
  }

  private static void setString(String key, String value)
  {
    nativeSetString(key, value);
  }

  private static void setBool(String key)
  {
    setBool(key, true);
  }

  private static void setBool(String key, boolean value)
  {
    nativeSetBoolean(key, value);
  }

  @NonNull
  public static String getApplicationId()
  {
    return mApplicationId;
  }

  public static int getVersionCode()
  {
    return mVersionCode;
  }

  @NonNull
  public static String getVersionName()
  {
    return mVersionName;
  }

  public static String getStoragePath()
  {
    return getString(KEY_APP_STORAGE);
  }

  public static void setStoragePath(String path)
  {
    setString(KEY_APP_STORAGE, path);
  }

  public static boolean isAutodownloadEnabled()
  {
    return getBool(KEY_DOWNLOADER_AUTO, true);
  }

  public static void setAutodownloadEnabled(boolean enabled)
  {
    setBool(KEY_DOWNLOADER_AUTO, enabled);
  }

  public static boolean showZoomButtons()
  {
    return getBool(KEY_PREF_ZOOM_BUTTONS, true);
  }

  public static void setShowZoomButtons(boolean show)
  {
    setBool(KEY_PREF_ZOOM_BUTTONS, show);
  }

  public static boolean isAutoStartLocationFollowAndRotateEnabled()
  {
    return getBool(KEY_AUTO_START_LOCATION_FOLLOW_AND_ROTATE, false);
  }

  public static void setAutoStartLocationFollowAndRotateEnabled(boolean enabled)
  {
    setBool(KEY_AUTO_START_LOCATION_FOLLOW_AND_ROTATE, enabled);
  }

  public static boolean isInCarFreeDrivingAutoZoomEnabled()
  {
    return getBool(KEY_PREF_IN_CAR_FREE_DRIVING_AUTO_ZOOM, false);
  }

  public static void setInCarFreeDrivingAutoZoomEnabled(boolean enabled)
  {
    setBool(KEY_PREF_IN_CAR_FREE_DRIVING_AUTO_ZOOM, enabled);
  }

  public static boolean isInCarOptimisedVisualsEnabled()
  {
    return getBool(KEY_PREF_IN_CAR_OPTIMISED_VISUALS, false);
  }

  public static void setInCarOptimisedVisualsEnabled(boolean enabled)
  {
    setBool(KEY_PREF_IN_CAR_OPTIMISED_VISUALS, enabled);
  }

  public static void setStatisticsEnabled(boolean enabled)
  {
    setBool(KEY_PREF_STATISTICS, enabled);
  }

  public static boolean isKeepScreenOnEnabled()
  {
    return getBool(KEY_MISC_KEEP_SCREEN_ON, false);
  }

  public static void setKeepScreenOnEnabled(boolean enabled)
  {
    setBool(KEY_MISC_KEEP_SCREEN_ON, enabled);
  }

  public static boolean isShowOnLockScreenEnabled()
  {
    // Disabled by default on Android 7.1 and earlier devices.
    // See links below for details:
    // https://github.com/organicmaps/organicmaps/issues/2857
    // https://github.com/organicmaps/organicmaps/issues/3967
    return getBool(KEY_MISC_SHOW_ON_LOCK_SCREEN, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
  }

  public static void setShowOnLockScreenEnabled(boolean enabled)
  {
    setBool(KEY_MISC_SHOW_ON_LOCK_SCREEN, enabled);
  }

  public static boolean isSearchHistoryEnabled()
  {
    return getBool(KEY_PREF_SEARCH_HISTORY, true);
  }

  public static void setSearchHistoryEnabled(boolean enabled)
  {
    setBool(KEY_PREF_SEARCH_HISTORY, enabled);
  }

  public static void setUseMobileDataSettings(@NonNull final Context context, @NonNull final MobileDataSettings settings)
  {
    final boolean value = switch (settings)
    {
      case ALWAYS -> true;
      case NEVER -> false;
      case TODAY -> true;
    };
    setBool(KEY_MISC_USE_MOBILE_DATA, value);
    mPrefs.edit().putInt(KEY_MISC_USE_MOBILE_DATA, settings.ordinal()).apply();
    if (settings == MobileDataSettings.TODAY)
      setLong(KEY_MISC_USE_MOBILE_DATA_TIMESTAMP, System.currentTimeMillis());
  }

  public static MobileDataSettings getUseMobileDataSettings(@NonNull final Context context)
  {
    final int ordinal = mPrefs.getInt(KEY_MISC_USE_MOBILE_DATA, MobileDataSettings.NEVER.ordinal());
    final MobileDataSettings settings = MobileDataSettings.values()[ordinal];
    if (settings == MobileDataSettings.TODAY)
    {
      final long timestamp = getLong(KEY_MISC_USE_MOBILE_DATA_TIMESTAMP, 0L);
      final long diff = System.currentTimeMillis() - timestamp;
      if (diff > 24 * 60 * 60 * 1000)
        return MobileDataSettings.NEVER;
    }
    return settings;
  }

  public static boolean getUseMobileData()
  {
    return getBool(KEY_MISC_USE_MOBILE_DATA);
  }

  public static boolean getUseMobileDataRoaming()
  {
    return getBool(KEY_MISC_USE_MOBILE_DATA_ROAMING);
  }

  public static void setUseMobileDataRoaming(boolean value)
  {
    setBool(KEY_MISC_USE_MOBILE_DATA_ROAMING, value);
  }

  public static boolean isDisclaimerApproved()
  {
    return getBool(KEY_MISC_DISCLAIMER_ACCEPTED, false);
  }

  public static void setDisclaimerApproved(boolean approved)
  {
    setBool(KEY_MISC_DISCLAIMER_ACCEPTED, approved);
  }

  public static boolean isLocationRequested()
  {
    return getBool(KEY_MISC_LOCATION_REQUESTED, false);
  }

  public static void setLocationRequested()
  {
    setBool(KEY_MISC_LOCATION_REQUESTED);
  }

  public static long getAgpsTimestamp()
  {
    return getLong(KEY_MISC_AGPS_TIMESTAMP, 0L);
  }

  public static void setAgpsTimestamp(long timestamp)
  {
    setLong(KEY_MISC_AGPS_TIMESTAMP, timestamp);
  }

  @NonNull
  public static String getDonateUrl()
  {
    return getString(KEY_DONATE_URL);
  }

  public static void setDonateUrl(@NonNull String url)
  {
    setString(KEY_DONATE_URL, url);
  }

  public static boolean isFirstStartDialogSeen()
  {
    return getBool(KEY_MISC_FIRST_START_DIALOG_SEEN, false);
  }

  public static void setFirstStartDialogSeen()
  {
    setBool(KEY_MISC_FIRST_START_DIALOG_SEEN);
  }

  public static int getLaunchNumber()
  {
    return getInt(KEY_APP_LAUNCH_NUMBER, 0);
  }

  public static void setLaunchNumber(int number)
  {
    setInt(KEY_APP_LAUNCH_NUMBER, number);
  }

  public static long getLastSessionTimestamp()
  {
    return getLong(KEY_APP_LAST_SESSION_TIMESTAMP, 0L);
  }

  public static void setLastSessionTimestamp(long timestamp)
  {
    setLong(KEY_APP_LAST_SESSION_TIMESTAMP, timestamp);
  }

  public static int getFirstInstallVersionCode()
  {
    return getInt(KEY_APP_FIRST_INSTALL_VERSION_CODE, 0);
  }

  public static void setFirstInstallVersionCode(int versionCode)
  {
    setInt(KEY_APP_FIRST_INSTALL_VERSION_CODE, versionCode);
  }

  public static int getLastInstallVersionCode()
  {
    return getInt(KEY_APP_LAST_INSTALL_VERSION_CODE, 0);
  }

  public static void setLastInstallVersionCode(int versionCode)
  {
    setInt(KEY_APP_LAST_INSTALL_VERSION_CODE, versionCode);
  }

  public static boolean useGoogleServices()
  {
    return getBool(KEY_PREF_USE_GS, true);
  }

  public static void setUseGoogleServices(boolean enabled)
  {
    setBool(KEY_PREF_USE_GS, enabled);
  }

  public static int getStatisticsConfig()
  {
    return nativeGetInt(KEY_PREF_STATISTICS, 0);
  }

  public static void setStatisticsConfig(int value)
  {
    nativeSetInt(KEY_PREF_STATISTICS, value);
  }

  @NonNull
  public static String getFlavor()
  {
    return mFlavor;
  }

  public static void init(@NonNull SharedPreferences prefs, @NonNull String flavor, @NonNull String applicationId,
                          int versionCode, @NonNull String versionName)
  {
    mPrefs = prefs;
    mFlavor = flavor;
    mApplicationId = applicationId;
    mVersionCode = versionCode;
    mVersionName = versionName;
  }

  @NonNull
  public static native String nativeGetString(@NonNull String name, @NonNull String def);

  public static native void nativeSetString(@NonNull String name, @NonNull String value);

  public static native int nativeGetInt(@NonNull String name, int def);

  public static native void nativeSetInt(@NonNull String name, int value);

  public static native long nativeGetLong(@NonNull String name, long def);

  public static native void nativeSetLong(@NonNull String name, long value);

  public static native double nativeGetDouble(@NonNull String name, double def);

  public static native void nativeSetDouble(@NonNull String name, double value);

  public static native boolean nativeGetBoolean(@NonNull String name, boolean def);

  public static native void nativeSetBoolean(@NonNull String name, boolean value);
}
