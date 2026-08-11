package app.organicmaps.incar;

import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.sdk.bookmarks.data.MapObject;

/** Minimal persisted destination used only by the direct-display InCar quick-destination adapter. */
public final class InCarQuickDestination
{
  @NonNull
  private final String mTitle;
  @NonNull
  private final String mSubtitle;
  private final double mLat;
  private final double mLon;

  public InCarQuickDestination(@NonNull String title, @NonNull String subtitle, double lat, double lon)
  {
    mTitle = title;
    mSubtitle = subtitle;
    mLat = lat;
    mLon = lon;
  }

  @Nullable
  public static InCarQuickDestination fromMapObject(@Nullable MapObject mapObject)
  {
    if (mapObject == null || mapObject.isMyPosition())
      return null;

    final InCarQuickDestination destination = new InCarQuickDestination(mapObject.getTitle(), mapObject.getSubtitle(),
                                                                        mapObject.getLat(), mapObject.getLon());
    return destination.isValid() ? destination : null;
  }

  @Nullable
  public static InCarQuickDestination fromLocation(@NonNull String title, @Nullable Location location)
  {
    if (location == null)
      return null;
    final InCarQuickDestination destination =
        new InCarQuickDestination(title, "", location.getLatitude(), location.getLongitude());
    return destination.isValid() ? destination : null;
  }

  public boolean isValid()
  {
    return isValidCoordinate(mLat, -90.0, 90.0) && isValidCoordinate(mLon, -180.0, 180.0);
  }

  @NonNull
  public String getTitle()
  {
    return mTitle;
  }

  @NonNull
  public String getSubtitle()
  {
    return mSubtitle;
  }

  public double getLat()
  {
    return mLat;
  }

  public double getLon()
  {
    return mLon;
  }

  @NonNull
  public String getDisplayLabel()
  {
    if (!mTitle.trim().isEmpty())
      return mTitle.trim();
    if (!mSubtitle.trim().isEmpty())
      return mSubtitle.trim();
    return "";
  }

  @NonNull
  public MapObject toMapObject()
  {
    return MapObject.createMapObject(MapObject.SEARCH, mTitle, mSubtitle, mLat, mLon);
  }

  public boolean samePlace(@Nullable InCarQuickDestination other)
  {
    if (other == null)
      return false;
    return Math.abs(mLat - other.mLat) < 1.0e-6 && Math.abs(mLon - other.mLon) < 1.0e-6;
  }

  @NonNull
  public String encode()
  {
    return mTitle.length() + ":" + mTitle + mSubtitle.length() + ":" + mSubtitle + mLat + ":" + mLon;
  }

  @Nullable
  public static InCarQuickDestination decode(@Nullable String encoded)
  {
    if (encoded == null || encoded.isEmpty())
      return null;

    try
    {
      int cursor = 0;
      final int titleSeparator = encoded.indexOf(':', cursor);
      if (titleSeparator < 0)
        return null;
      final int titleLength = Integer.parseInt(encoded.substring(cursor, titleSeparator));
      cursor = titleSeparator + 1;
      if (titleLength < 0 || cursor + titleLength > encoded.length())
        return null;
      final String title = encoded.substring(cursor, cursor + titleLength);
      cursor += titleLength;

      final int subtitleSeparator = encoded.indexOf(':', cursor);
      if (subtitleSeparator < 0)
        return null;
      final int subtitleLength = Integer.parseInt(encoded.substring(cursor, subtitleSeparator));
      cursor = subtitleSeparator + 1;
      if (subtitleLength < 0 || cursor + subtitleLength > encoded.length())
        return null;
      final String subtitle = encoded.substring(cursor, cursor + subtitleLength);
      cursor += subtitleLength;

      final int coordinateSeparator = encoded.indexOf(':', cursor);
      if (coordinateSeparator < 0)
        return null;
      final double lat = Double.parseDouble(encoded.substring(cursor, coordinateSeparator));
      final double lon = Double.parseDouble(encoded.substring(coordinateSeparator + 1));
      final InCarQuickDestination destination = new InCarQuickDestination(title, subtitle, lat, lon);
      return destination.isValid() ? destination : null;
    }
    catch (IndexOutOfBoundsException | NumberFormatException ignored)
    {
      return null;
    }
  }

  @VisibleForTesting
  static boolean codecRoundTrips(@NonNull InCarQuickDestination destination)
  {
    final InCarQuickDestination decoded = decode(destination.encode());
    if (decoded == null)
      return false;
    final boolean sameLabels = destination.mTitle.equals(decoded.mTitle) && destination.mSubtitle.equals(decoded.mSubtitle);
    final boolean sameLat = Double.doubleToLongBits(destination.mLat) == Double.doubleToLongBits(decoded.mLat);
    final boolean sameLon = Double.doubleToLongBits(destination.mLon) == Double.doubleToLongBits(decoded.mLon);
    return sameLabels && sameLat && sameLon;
  }

  private static boolean isValidCoordinate(double coordinate, double minimum, double maximum)
  {
    return Double.isFinite(coordinate) && coordinate >= minimum && coordinate <= maximum;
  }
}
