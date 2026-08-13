package app.organicmaps.incar;

/** Tracks when the optional InCar controller may attach listeners and access the native map engine. */
final class InCarDrivingViewLifecycle
{
  enum Transition
  {
    NONE,
    ATTACH,
    DETACH
  }

  private boolean mFrameworkReady;
  private boolean mRenderingReady;
  private boolean mAttached;
  private int mStartedMapActivities;

  Transition onFrameworkReady()
  {
    if (mFrameworkReady)
      return Transition.NONE;
    mFrameworkReady = true;
    return updateAttachment();
  }

  Transition onFrameworkDetached()
  {
    if (!mFrameworkReady && !mAttached && !mRenderingReady)
      return Transition.NONE;
    mFrameworkReady = false;
    mRenderingReady = false;
    return updateAttachment();
  }

  Transition onMapActivityStarted()
  {
    ++mStartedMapActivities;
    return updateAttachment();
  }

  Transition onMapActivityStopped()
  {
    if (mStartedMapActivities > 0)
      --mStartedMapActivities;
    return updateAttachment();
  }

  void onRenderingCreated()
  {
    mRenderingReady = mFrameworkReady;
  }

  void onRenderingDetached()
  {
    mRenderingReady = false;
  }

  boolean isAttached()
  {
    return mAttached;
  }

  boolean hasStartedMapActivity()
  {
    return mStartedMapActivities > 0;
  }

  boolean canAccessNativeState()
  {
    return mAttached && mRenderingReady;
  }

  private Transition updateAttachment()
  {
    final boolean shouldAttach = mFrameworkReady && mStartedMapActivities > 0;
    if (mAttached == shouldAttach)
      return Transition.NONE;
    mAttached = shouldAttach;
    return shouldAttach ? Transition.ATTACH : Transition.DETACH;
  }
}
