package app.organicmaps.incar;

/** Narrow JNI seam enabling display-only road matching for an attached InCar map session. */
final class InCarRoadSnapNative
{
  private InCarRoadSnapNative() {}

  static void setEnabled(boolean enabled)
  {
    nativeSetEnabled(enabled);
  }

  private static native void nativeSetEnabled(boolean enabled);
}
