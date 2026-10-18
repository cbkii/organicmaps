package app.organicmaps.incar;

/** Process-local evidence only. Native readiness is never persisted across process death. */
public final class InCarCompactStartupState
{
  public static final InCarCompactStartupState INSTANCE = new InCarCompactStartupState();
  private int mActivity;
  private int mTask = -1;
  private int mWidth;
  private int mHeight;
  private boolean mResumed;
  private boolean mCore;
  private boolean mNative;
  private boolean mAttached;
  private String mStage = "PROCESS";

  public synchronized void splash()
  {
    mStage = "SPLASH_OR_SETUP";
  }

  public synchronized void coreReady()
  {
    mCore = true;
  }

  public synchronized void activity(int instance, int task, boolean resumed)
  {
    if (resumed)
    {
      if (mActivity != instance)
      {
        mWidth = mHeight = 0;
      }
      mActivity = instance;
      mTask = task;
      mStage = "MAP_ACTIVITY";
      mResumed = true;
    }
    else if (mActivity == instance)
      mResumed = false;
  }

  public synchronized void attached(boolean attached)
  {
    mAttached = attached;
    if (!attached)
      mWidth = mHeight = 0;
  }

  public synchronized void nativeReady()
  {
    mNative = true;
  }

  public synchronized void surface(int width, int height)
  {
    mWidth = Math.max(0, width);
    mHeight = Math.max(0, height);
  }

  public synchronized boolean ready()
  {
    return mCore && mNative && mResumed && mAttached && mWidth > 0 && mHeight > 0;
  }

  public synchronized Object[] row()
  {
    return new Object[] {mStage, mTask, mCore ? 1 : 0, mNative ? 1 : 0, mAttached ? 1 : 0,
                         mWidth, mHeight, mResumed ? 1 : 0, ready() ? 1 : 0};
  }
}
