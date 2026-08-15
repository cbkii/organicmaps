package app.organicmaps.incar;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.MwmApplication;
import app.organicmaps.sdk.bookmarks.data.MapObject;

/** Small InCar-only preference store for Quick Destinations. */
public final class InCarQuickDestinationsStore
{
  public enum Action
  {
    FUEL_CHARGING,
    FUEL,
    CHARGING,
    PARKING,
    TOILETS,
    FOOD,
    HOME,
    WORK,
    RECENT_1,
    RECENT_2
  }

  private static final String PREFIX = "InCarQuick";
  private static final String KEY_START_COLLAPSED = PREFIX + "StartCollapsed";
  private static final String KEY_HOME = PREFIX + "HomeDestination";
  private static final String KEY_WORK = PREFIX + "WorkDestination";
  private static final String KEY_RECENT_1 = PREFIX + "RecentDestination1";
  private static final String KEY_RECENT_2 = PREFIX + "RecentDestination2";

  private InCarQuickDestinationsStore() {}

  public static boolean startCollapsed(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_START_COLLAPSED, true);
  }

  public static void setStartCollapsed(@NonNull Context context, boolean collapsed)
  {
    prefs(context).edit().putBoolean(KEY_START_COLLAPSED, collapsed).apply();
  }

  @NonNull
  public static String startCollapsedPreferenceKey()
  {
    return KEY_START_COLLAPSED;
  }

  public static boolean isActionEnabled(@NonNull Context context, @NonNull Action action)
  {
    final SharedPreferences preferences = prefs(context);
    final String key = actionKey(action);
    if (preferences.contains(key))
      return preferences.getBoolean(key, true);

    if ((action == Action.FUEL || action == Action.CHARGING)
        && preferences.contains(actionKey(Action.FUEL_CHARGING)))
      return preferences.getBoolean(actionKey(Action.FUEL_CHARGING), true);

    return true;
  }

  public static void setActionEnabled(@NonNull Context context, @NonNull Action action, boolean enabled)
  {
    prefs(context).edit().putBoolean(actionKey(action), enabled).apply();
  }

  @Nullable
  public static InCarQuickDestination getHome(@NonNull Context context)
  {
    return getDestination(context, KEY_HOME);
  }

  public static void setHome(@NonNull Context context, @Nullable InCarQuickDestination destination)
  {
    setDestination(context, KEY_HOME, destination);
  }

  @Nullable
  public static InCarQuickDestination getWork(@NonNull Context context)
  {
    return getDestination(context, KEY_WORK);
  }

  public static void setWork(@NonNull Context context, @Nullable InCarQuickDestination destination)
  {
    setDestination(context, KEY_WORK, destination);
  }

  @Nullable
  public static InCarQuickDestination getRecent(@NonNull Context context, int slot)
  {
    if (slot == 1)
      return getDestination(context, KEY_RECENT_1);
    if (slot == 2)
      return getDestination(context, KEY_RECENT_2);
    throw new IllegalArgumentException("Recent slot must be 1 or 2");
  }

  public static void recordRecent(@NonNull Context context, @Nullable MapObject mapObject)
  {
    final InCarQuickDestination candidate = InCarQuickDestination.fromMapObject(mapObject);
    if (candidate == null)
      return;

    final RecentPair pair = selectNewestTwo(candidate, getRecent(context, 1), getRecent(context, 2));
    final SharedPreferences.Editor editor = prefs(context).edit();
    putDestination(editor, KEY_RECENT_1, pair.first);
    putDestination(editor, KEY_RECENT_2, pair.second);
    editor.apply();
  }

  public static boolean isQuickPreferenceKey(@Nullable String key)
  {
    return key != null && key.startsWith(PREFIX);
  }

  @NonNull
  public static String preferenceKey(@NonNull Action action)
  {
    return actionKey(action);
  }

  @VisibleForTesting
  static RecentPair selectNewestTwo(@Nullable InCarQuickDestination candidate, @Nullable InCarQuickDestination first,
                                    @Nullable InCarQuickDestination second)
  {
    if (candidate == null || !candidate.isValid())
      return new RecentPair(first, second);
    if (candidate.samePlace(first))
      return new RecentPair(candidate, second);
    if (candidate.samePlace(second))
      return new RecentPair(candidate, first);
    return new RecentPair(candidate, first);
  }

  @VisibleForTesting
  @Nullable
  static String encodeDestination(@Nullable InCarQuickDestination destination)
  {
    return destination == null ? null : destination.encode();
  }

  @VisibleForTesting
  @Nullable
  static InCarQuickDestination decodeDestination(@Nullable String encoded)
  {
    return InCarQuickDestination.decode(encoded);
  }

  @VisibleForTesting
  static final class RecentPair
  {
    @Nullable
    final InCarQuickDestination first;
    @Nullable
    final InCarQuickDestination second;

    RecentPair(@Nullable InCarQuickDestination first, @Nullable InCarQuickDestination second)
    {
      this.first = first;
      this.second = second;
    }
  }

  @NonNull
  private static String actionKey(@NonNull Action action)
  {
    return PREFIX + "Enabled" + action.name();
  }

  @Nullable
  private static InCarQuickDestination getDestination(@NonNull Context context, @NonNull String key)
  {
    return decodeDestination(prefs(context).getString(key, null));
  }

  private static void setDestination(@NonNull Context context, @NonNull String key,
                                     @Nullable InCarQuickDestination destination)
  {
    final SharedPreferences.Editor editor = prefs(context).edit();
    putDestination(editor, key, destination);
    editor.apply();
  }

  private static void putDestination(@NonNull SharedPreferences.Editor editor, @NonNull String key,
                                     @Nullable InCarQuickDestination destination)
  {
    final String encoded = encodeDestination(destination);
    if (encoded == null)
      editor.remove(key);
    else
      editor.putString(key, encoded);
  }

  @NonNull
  private static SharedPreferences prefs(@NonNull Context context)
  {
    return MwmApplication.prefs(context);
  }
}
