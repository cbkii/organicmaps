package app.organicmaps.sdk;

import androidx.annotation.Keep;

public interface MapRenderingListener
{
  default void onRenderingCreated() {}

  default void onRenderingRestored() {}

  default void onRenderingDetached() {}

  default void onRenderingPresentationRequired() {}

  // The Map owner marshals this callback to the current surface's UI generation.
  @Keep
  default void onRenderingPresentationChanged(boolean ready, long generation)
  {}

  // Called from JNI.
  @Keep
  @SuppressWarnings("unused")
  default void onRenderingInitializationFinished()
  {}
}
