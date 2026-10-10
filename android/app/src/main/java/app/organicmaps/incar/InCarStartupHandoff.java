package app.organicmaps.incar;

/** Activity-owned, main-thread startup state. Core completion does not confer foreground authority. */
public final class InCarStartupHandoff
{
  private boolean mResumed;
  private boolean mInitializing;
  private boolean mCoreReady;
  private boolean mHandedOff;
  private boolean mClosed;

  public void onResume()
  {
    mResumed = true;
  }

  public void onPause()
  {
    mResumed = false;
  }

  public boolean beginInitialization()
  {
    if (!mResumed || mInitializing || mCoreReady || mHandedOff || mClosed)
      return false;
    mInitializing = true;
    return true;
  }

  public void onCoreReady()
  {
    if (!mClosed)
      mCoreReady = true;
  }

  public boolean isCoreReady()
  {
    return mCoreReady;
  }

  /** Claim before dispatch, including ActivityResultLauncher dispatch which leaves Splash alive. */
  public boolean claimHandoff()
  {
    if (!mResumed || !mCoreReady || mHandedOff || mClosed)
      return false;
    mHandedOff = true;
    return true;
  }

  public void close()
  {
    mClosed = true;
    mResumed = false;
  }
}
