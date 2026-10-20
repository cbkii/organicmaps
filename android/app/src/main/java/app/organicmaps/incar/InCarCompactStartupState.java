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
  private boolean mPresentationRequired;
  private boolean mPresented;
  private long mPresentationGeneration = -1;
  private String mStage = "PROCESS";

  public synchronized void splash()
  {
    mStage = "SPLASH_OR_SETUP";
  }

  public synchronized void coreReady()
  {
    mCore = true;
  }

  public synchronized void created(int instance, int task)
  {
    mActivity = instance;
    mTask = task;
    mWidth = mHeight = 0;
    mAttached = mResumed = false;
    mPresentationRequired = mPresented = false;
    mPresentationGeneration = -1;
    mStage = "MAP_ACTIVITY";
  }

  public synchronized void activity(int instance, int task, boolean resumed)
  {
    if (mActivity != instance)
      return;
    mTask = task;
    mResumed = resumed;
  }

  public synchronized void attached(int instance, boolean attached)
  {
    if (mActivity != instance)
      return;
    mAttached = attached;
    if (!attached)
      mWidth = mHeight = 0;
  }

  public synchronized void renderingCreated(int instance)
  {
    if (mActivity != instance)
      return;
    mNative = false;
    mAttached = true;
    mWidth = mHeight = 0;
  }

  public synchronized void nativeReady(int instance)
  {
    if (mActivity == instance)
      mNative = true;
  }

  public synchronized void surface(int instance, int width, int height)
  {
    if (mActivity != instance)
      return;
    mWidth = Math.max(0, width);
    mHeight = Math.max(0, height);
  }

  public synchronized void presentationRequired(int instance)
  {
    if (mActivity != instance)
      return;
    mPresentationRequired = true;
    mPresented = false;
    mPresentationGeneration = -1;
  }

  public synchronized void presentation(int instance, boolean ready, long generation)
  {
    if (mActivity != instance || generation < mPresentationGeneration)
      return;
    mPresentationGeneration = generation;
    mPresented = ready;
  }

  public synchronized boolean ready()
  {
    return mCore && mNative && mResumed && mAttached && mWidth > 0 && mHeight > 0
 && (!mPresentationRequired || mPresented);
  }

  public synchronized Object[] row()
  {
    return new Object[] {mStage, mTask,   mCore ? 1 : 0,    mNative ? 1 : 0, mAttached ? 1 : 0,
                         mWidth, mHeight, mResumed ? 1 : 0, ready() ? 1 : 0};
  }
}
