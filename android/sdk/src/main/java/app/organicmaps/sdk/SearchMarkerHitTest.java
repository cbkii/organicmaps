package app.organicmaps.sdk;

/**
 * Read-only hit testing for currently rendered search-result marks.
 *
 * <p>This deliberately does not perform a map selection. Callers can use it to decide whether a
 * completed tap should be delivered to the normal map tap pipeline while leaving pan/pinch gestures
 * untouched.</p>
 */
public final class SearchMarkerHitTest
{
  private SearchMarkerHitTest() {}

  public static native boolean nativeHasSearchMarkerAt(float xPx, float yPx, float radiusPx);
}
