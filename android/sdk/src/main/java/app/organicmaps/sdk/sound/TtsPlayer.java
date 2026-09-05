package app.organicmaps.sdk.sound;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.concurrency.UiThread;
import app.organicmaps.sdk.util.log.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code TtsPlayer} class manages available TTS voice languages.
 * Single TTS language is described by {@link LanguageData} item.
 * <p>
 * We support a set of languages listed in {@code libs/platform/languages.hpp} and passed to Java by
 * {@link #nativeGetSupportedLanguages()}.
 * During loading each item in this list is marked as {@code downloaded} or {@code not downloaded},
 * unsupported voices are excluded.
 * <p>
 * At startup we check whether currently selected language is in our list of supported voices and its data is
 * downloaded. If not, we check system default locale. If failed, the same check is made for English language. Finally,
 * if mentioned checks fail we manually disable TTS, so the user must go to the settings and select preferred voice
 * language by hand. <p> If no core supported languages can be used by the system, TTS is locked down and can not be
 * enabled and used.
 */
public enum TtsPlayer
{
  INSTANCE;

  private static final String TAG = TtsPlayer.class.getSimpleName();
  private static final Locale DEFAULT_LOCALE = Locale.US;
  private static final float SPEECH_RATE = 1.0f;
  private static final int TTS_SPEAK_DELAY_MILLIS = 50;
  private static final String TTS_SILENT_UTTERANCE_ID = "SILENT_DELAY";

  @Nullable
  private static List<Pair<String, String>> sSupportedLanguages = null;

  public static Runnable sOnReloadCallback = null;

  private static final CopyOnWriteArrayList<Runnable> sStateChangedListeners = new CopyOnWriteArrayList<>();

  private ContentObserver mTtsEngineObserver;
  private TextToSpeech mTts;
  // Serializes playback-epoch transitions with completion accounting and audio-focus release. Without this boundary,
  // a late old-engine callback can pass a generation check immediately before a new queue starts and then mutate the
  // replacement queue or abandon its newly acquired focus.
  private final Object mPlaybackLock = new Object();
  private final AtomicInteger mTtsQueueSize = new AtomicInteger(0);
  // Invalidates callbacks belonging to an old queue or a replaced/shut-down engine. Android TTS callbacks can arrive
  // after stop()/shutdown(), so queue size and audio-focus state must never be shared with a later playback epoch.
  private final AtomicInteger mUtteranceGeneration = new AtomicInteger(0);
  private final UtteranceProgressListener mUtteranceProgressListener = new UtteranceProgressListener() {
    @Override
    public void onStart(@NonNull String utteranceId)
    {
      if (!isUtteranceForGeneration(utteranceId, mUtteranceGeneration.get()))
        return;
      Logger.d(TAG, "TTS Utterance started: " + utteranceId);
    }

    @Override
    public void onDone(@NonNull String utteranceId)
    {
      handleStop(utteranceId);
    }

    @Override
    @SuppressWarnings("deprecated") // abstract method must be implemented
    public void onError(@NonNull String utteranceId)
    {
      handleError(utteranceId, -1);
    }

    @Override
    public void onError(@NonNull String utteranceId, int errorCode)
    {
      handleError(utteranceId, errorCode);
    }

    private void handleError(@NonNull String utteranceId, int errorCode)
    {
      if (!isUtteranceForGeneration(utteranceId, mUtteranceGeneration.get()))
        return;
      Logger.w(TAG, "TTS Utterance error: " + utteranceId + ", code: " + errorCode);
      handleStop(utteranceId);
    }

    private void handleStop(@NonNull String utteranceId)
    {
      synchronized (mPlaybackLock)
      {
        if (!isUtteranceForGeneration(utteranceId, mUtteranceGeneration.get()))
          return;
        Logger.d(TAG, "TTS Utterance stopped: " + utteranceId);
        if (mTtsQueueSize.decrementAndGet() <= 0)
        {
          mTtsQueueSize.set(0);
          // Release while holding the same lock used to begin a replacement playback epoch. This guarantees an old
          // completion cannot abandon audio focus after a new queue has already requested it.
          releaseAudioFocusSafely();
        }
      }
    }
  };

  private boolean mInitializing;
  // Bumped on every initialize() start; the captured value lets the init callback
  // detect that it belongs to a TextToSpeech we've already shutdown and bail out.
  private int mInitGeneration;
  private boolean mReloadTriggered = false;
  private AudioFocusManager mAudioFocusManager;

  private final Bundle mParams = new Bundle();

  @Nullable
  private Context mContext;

  // Lockdown reasons: init ERROR, no OM-supported languages, engine IllegalArgumentException.
  // Reset on ContentObserver.onChange so a switched engine can re-initialize.
  private boolean mUnavailable;
  // Engine is ready and a downloaded, OM-supported voice language is selected.
  // Reflects the result of the latest refreshLanguages() pass.
  private boolean mHasUsableLanguage;

  public enum State
  {
    INITIALIZING,
    UNAVAILABLE,
    NEEDS_LANGUAGE,
    READY_ON,
    READY_OFF;

    public boolean isReady()
    {
      return this == READY_ON || this == READY_OFF;
    }
  }

  TtsPlayer() {}

  @NonNull
  static String makeUtteranceId(int generation, @NonNull String id)
  {
    return generation + ":" + id;
  }

  static boolean isUtteranceForGeneration(@NonNull String utteranceId, int generation)
  {
    return utteranceId.startsWith(generation + ":");
  }

  private static @Nullable LanguageData findSupportedLanguage(String internalCode, List<LanguageData> langs)
  {
    if (TextUtils.isEmpty(internalCode))
      return null;

    for (LanguageData lang : langs)
      if (lang.matchesInternalCode(internalCode))
        return lang;

    return null;
  }

  private static @Nullable LanguageData findSupportedLanguage(Locale locale, List<LanguageData> langs)
  {
    if (locale == null)
      return null;

    for (LanguageData lang : langs)
      if (lang.matchesLocale(locale))
        return lang;

    return null;
  }

  private boolean setLanguageInternal(@NonNull LanguageData lang)
  {
    final TextToSpeech tts = mTts;
    if (tts == null)
      return false;

    final int generation = mInitGeneration;
    try
    {
      final int status = tts.setLanguage(lang.locale);
      if (status < TextToSpeech.LANG_AVAILABLE)
      {
        Logger.w(TAG, "Failed to set TTS language " + lang.locale + ", status=" + status);
        return false;
      }

      nativeSetTurnNotificationsLocale(lang.internalCode);
      Config.TTS.setLanguage(lang.internalCode);
      return true;
    }
    catch (RuntimeException e)
    {
      failClosedForEngine(generation, "setting language", e);
      return false;
    }
  }

  public boolean setLanguage(LanguageData lang)
  {
    return (lang != null && setLanguageInternal(lang));
  }

  private static @Nullable LanguageData getDefaultLanguage(List<LanguageData> langs)
  {
    LanguageData res;

    Locale defLocale = Locale.getDefault();
    if (defLocale != null)
    {
      res = findSupportedLanguage(defLocale, langs);
      if (res != null && res.downloaded)
        return res;
    }

    res = findSupportedLanguage(DEFAULT_LOCALE, langs);
    if (res != null && res.downloaded)
      return res;

    return null;
  }

  public static @Nullable LanguageData getSelectedLanguage(List<LanguageData> langs)
  {
    return findSupportedLanguage(Config.TTS.getLanguage(), langs);
  }

  private void lockDown()
  {
    mUnavailable = true;
    setEnabled(false);
  }

  public void initialize(@NonNull Context context)
  {
    mContext = context;

    if (mTts != null || mInitializing || mUnavailable)
      return;

    ensureEngineObserver(context);
    mInitializing = true;
    final int generation = ++mInitGeneration;

    try
    {
      // Some engines can call OnInitListener before the TextToSpeech constructor returns. Always post
      // the callback so mTts is published before any successful-init code can dereference it. UiThread.run()
      // is not sufficient because it executes synchronously when the callback already arrives on the main thread.
      mTts = new TextToSpeech(
          context, status -> postInitializationCallback(() -> handleInitializationResult(context, generation, status)));
    }
    catch (RuntimeException e)
    {
      // A vendor engine can fail while binding/constructing. Invalidate any callback it may already have posted
      // and fail closed for TTS without taking down the map process.
      ++mInitGeneration;
      Logger.e(TAG, "Failed to create TextToSpeech", e);
      mInitializing = false;
      lockDown();
      notifyStateChanged();
    }
  }

  // Package-private for the instrumentation regression test. This must remain an unconditional queue operation.
  static void postInitializationCallback(@NonNull Runnable callback)
  {
    UiThread.runLater(callback);
  }

  private void handleInitializationResult(@NonNull Context context, int generation, int status)
  {
    // Stale callback from a TextToSpeech we've already shut down via onChange().
    if (generation != mInitGeneration)
      return;

    final TextToSpeech tts = mTts;
    if (tts == null)
    {
      // Defensive fallback: the queued-callback contract above should make this unreachable, but a broken
      // implementation must never turn TTS availability into an application-startup crash.
      Logger.e(TAG, "TextToSpeech init callback has no current engine");
      ++mInitGeneration;
      mInitializing = false;
      lockDown();
      notifyStateChanged();
      return;
    }

    if (status != TextToSpeech.SUCCESS)
    {
      Logger.e(TAG, "Failed to initialize TextToSpeech, status=" + status);
      ++mInitGeneration;
      shutdownTts();
      mInitializing = false;
      lockDown();
      notifyStateChanged();
      return;
    }

    try
    {
      refreshLanguages();

      // refreshLanguages() can lock TTS down when the engine is unusable. A missing selected/downloaded language is
      // not itself an engine failure; keep the configured engine available so settings can recover it later.
      if (generation != mInitGeneration || tts != mTts || mUnavailable)
      {
        mInitializing = false;
        notifyStateChanged();
        return;
      }

      requireTtsSuccess("setSpeechRate", tts.setSpeechRate(SPEECH_RATE));
      requireTtsSuccess("setAudioAttributes", tts.setAudioAttributes(AudioFocusManager.AUDIO_ATTRIBUTES));
      requireTtsSuccess("setOnUtteranceProgressListener",
                        tts.setOnUtteranceProgressListener(mUtteranceProgressListener));
      mAudioFocusManager = new AudioFocusManager(context, this::stop);
      mParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Config.TTS.getVolume());
    }
    catch (RuntimeException e)
    {
      // TTS is an external system/vendor service. A malformed or incompatible engine must fail closed for
      // navigation speech rather than crash Organic Maps during startup.
      Logger.e(TAG, "Failed to configure TextToSpeech", e);
      ++mInitGeneration;
      shutdownTts();
      mInitializing = false;
      lockDown();
      notifyStateChanged();
      return;
    }

    mInitializing = false;
    if (mReloadTriggered && sOnReloadCallback != null)
    {
      sOnReloadCallback.run();
      mReloadTriggered = false;
    }
    notifyStateChanged();
  }

  private static void requireTtsSuccess(@NonNull String operation, int status)
  {
    if (status != TextToSpeech.SUCCESS)
      throw new IllegalStateException(operation + " failed, status=" + status);
  }

  private void ensureEngineObserver(@NonNull Context context)
  {
    if (mTtsEngineObserver != null)
      return;

    final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
      @Override
      public void onChange(boolean selfChange)
      {
        Logger.d(TAG, "System TTS engine changed – reloading TTS engine");
        mReloadTriggered = true;
        // Invalidate a queued callback before replacing its engine.
        ++mInitGeneration;
        shutdownTts();
        mUnavailable = false;
        mInitializing = false;
        initialize(context);
      }
    };

    try
    {
      context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor("tts_default_synth"), false,
                                                           observer);
      mTtsEngineObserver = observer;
    }
    catch (RuntimeException e)
    {
      // Observer support is recovery-only. A vendor ROM which rejects it must not make app/TTS startup fatal;
      // changing the system TTS engine will simply require a process restart on that device.
      Logger.w(TAG, "Failed to observe system TTS engine changes", e);
    }
  }

  private void failClosedForEngine(int generation, @NonNull String operation, @NonNull RuntimeException e)
  {
    Logger.e(TAG, "TextToSpeech failed while " + operation, e);
    // Vendor callbacks are not guaranteed to run on the main thread. Keep the native turn-notification update made
    // by lockDown()/setEnabled() on the normal UI-thread path, and never let an old engine failure disable a new one.
    UiThread.run(() -> {
      if (generation != mInitGeneration)
        return;
      ++mInitGeneration;
      shutdownTts();
      mInitializing = false;
      lockDown();
      notifyStateChanged();
    });
  }

  private void shutdownTts()
  {
    final TextToSpeech tts = mTts;
    mTts = null;
    synchronized (mPlaybackLock)
    {
      // The current queue still owns audio focus at this point. Release it before invalidating the generation so
      // its now-stale completion callback is not the only path which could abandon that focus.
      releaseAudioFocusSafely();
      mUtteranceGeneration.incrementAndGet();
      mTtsQueueSize.set(0);
      mAudioFocusManager = null;
    }
    if (tts == null)
      return;

    try
    {
      tts.shutdown();
    }
    catch (RuntimeException e)
    {
      Logger.w(TAG, "Failed to shutdown TextToSpeech", e);
    }
  }

  private void releaseAudioFocusSafely()
  {
    final AudioFocusManager audioFocusManager = mAudioFocusManager;
    if (audioFocusManager == null)
      return;

    try
    {
      audioFocusManager.releaseAudioFocus();
    }
    catch (RuntimeException e)
    {
      Logger.w(TAG, "Failed to release TTS audio focus", e);
    }
  }

  private void releaseAudioFocusIfCurrent(int utteranceGeneration)
  {
    synchronized (mPlaybackLock)
    {
      if (utteranceGeneration != mUtteranceGeneration.get())
        return;
      releaseAudioFocusSafely();
    }
  }

  private static boolean isReady()
  {
    return INSTANCE.mTts != null && !INSTANCE.mUnavailable && !INSTANCE.mInitializing && INSTANCE.mHasUsableLanguage;
  }

  /**
   * Registers a listener invoked on the UI thread whenever the TTS lifecycle state may have
   * changed (init success / init failure / lockdown / enable toggle). Callers must remove
   * the listener (via {@link #removeStateChangedListener}) when their host Activity is
   * destroyed to avoid leaking its context.
   */
  public static void addStateChangedListener(@NonNull Runnable listener)
  {
    sStateChangedListeners.add(listener);
  }

  public static void removeStateChangedListener(@NonNull Runnable listener)
  {
    sStateChangedListeners.remove(listener);
  }

  private static void notifyStateChanged()
  {
    // CopyOnWriteArrayList iteration is safe under concurrent add/remove.
    for (Runnable listener : sStateChangedListeners)
      UiThread.run(listener);
  }

  @NonNull
  public static State getState()
  {
    if (INSTANCE.mUnavailable)
      return State.UNAVAILABLE;
    if (INSTANCE.mTts == null || INSTANCE.mInitializing)
      return State.INITIALIZING;
    if (!INSTANCE.mHasUsableLanguage)
      return State.NEEDS_LANGUAGE;
    return Config.TTS.isEnabled() ? State.READY_ON : State.READY_OFF;
  }

  public void speak(@NonNull String textToSpeak)
  {
    if (!isReady())
      return;

    if (!speakSequence(new String[] {textToSpeak}))
      stop();
  }

  public void playTurnNotifications(@NonNull String[] turnNotifications)
  {
    if (!isReady() || turnNotifications.length == 0)
      return;

    if (!speakSequence(turnNotifications))
      stop();
  }

  private boolean speakSequence(@NonNull String[] texts)
  {
    if (!Config.TTS.isEnabled() || texts.length == 0)
      return false;

    final TextToSpeech tts = mTts;
    final AudioFocusManager audioFocusManager = mAudioFocusManager;
    if (tts == null || audioFocusManager == null)
      return false;

    final int utteranceGeneration;
    try
    {
      synchronized (mPlaybackLock)
      {
        // Start the new playback epoch before requesting focus. A late callback which already acquired this lock
        // therefore finishes/relinquishes the old focus first; any later old callback observes the new generation and
        // cannot touch the replacement queue or focus.
        utteranceGeneration = mUtteranceGeneration.incrementAndGet();
        mTtsQueueSize.set(0);
        if (!audioFocusManager.requestAudioFocus())
          return false;
      }
    }
    catch (RuntimeException e)
    {
      Logger.w(TAG, "Failed to request TTS audio focus", e);
      return false;
    }

    final boolean isMusicActive;
    try
    {
      isMusicActive = audioFocusManager.isMusicActive();
    }
    catch (RuntimeException e)
    {
      Logger.w(TAG, "Failed to query music state for TTS", e);
      releaseAudioFocusIfCurrent(utteranceGeneration);
      return false;
    }

    synchronized (mPlaybackLock)
    {
      if (utteranceGeneration != mUtteranceGeneration.get())
        return false;

      // Reserve every callback in this sequence before crossing into vendor code. A broken TTS implementation may
      // invoke onDone/onError before speak()/playSilentUtterance() returns; pre-accounting prevents such a callback
      // from observing zero, releasing focus, and then having the caller resurrect the queue count afterwards.
      mTtsQueueSize.set(texts.length + (isMusicActive ? 1 : 0));
    }

    final int generation = mInitGeneration;
    try
    {
      if (isMusicActive
          && tts.playSilentUtterance(TTS_SPEAK_DELAY_MILLIS, TextToSpeech.QUEUE_FLUSH,
                                     makeUtteranceId(utteranceGeneration, TTS_SILENT_UTTERANCE_ID))
                 != TextToSpeech.SUCCESS)
      {
        Logger.d(TAG, "Failed to play silent utterance for music active delay");
        return false;
      }

      for (int i = 0; i < texts.length; ++i)
      {
        final int queueMode = i == 0 && !isMusicActive ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
        if (tts.speak(texts[i], queueMode, mParams, makeUtteranceId(utteranceGeneration, texts[i]))
            != TextToSpeech.SUCCESS)
        {
          Logger.d(TAG, "Failed to speak text: " + texts[i]);
          return false;
        }
      }
      return true;
    }
    catch (RuntimeException e)
    {
      failClosedForEngine(generation, "queueing speech", e);
      return false;
    }
  }

  public void stop()
  {
    if (!isReady())
      return;

    synchronized (mPlaybackLock)
    {
      mUtteranceGeneration.incrementAndGet();
      releaseAudioFocusSafely();
      mTtsQueueSize.set(0);
      final TextToSpeech tts = mTts;
      if (tts != null)
      {
        try
        {
          // Keep stop under the same playback boundary so an old stop request cannot race a replacement queue and
          // stop the newly-started speech after that queue has acquired focus.
          tts.stop();
        }
        catch (RuntimeException e)
        {
          Logger.w(TAG, "Failed to stop TextToSpeech", e);
        }
      }
    }
  }

  public static boolean isEnabled()
  {
    return getState() == State.READY_ON;
  }

  public static void setEnabled(boolean enabled)
  {
    final boolean wasEnabled = Config.TTS.isEnabled();
    Config.TTS.setEnabled(enabled);

    final Context context = INSTANCE.mContext;
    final boolean fallbackEnabled = context != null && OfflineNavigationVoicePack.isFallbackEnabled(context);
    nativeEnableTurnNotifications(
        TtsFallbackPolicy.shouldGenerateNotifications(enabled, Config.isInCar(), fallbackEnabled));

    if (wasEnabled != enabled)
      notifyStateChanged();
  }

  public float getVolume()
  {
    return Config.TTS.getVolume();
  }

  public void setVolume(final float volume)
  {
    mParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
    Config.TTS.setVolume(volume);
  }

  private boolean getUsableLanguages(List<LanguageData> outList)
  {
    for (final Pair<String, String> langNamePair : getSupportedLanguages())
    {
      try
      {
        outList.add(new LanguageData(langNamePair.first, langNamePair.second, mTts));
      }
      catch (LanguageData.NotAvailableException ex)
      {
        Logger.w(TAG, "Failed to get usable languages " + ex.getMessage());
      }
      catch (RuntimeException e)
      {
        Logger.e(TAG, "Failed to get usable languages", e);
        lockDown();
        return false;
      }
    }

    return true;
  }

  private @Nullable LanguageData refreshLanguagesInternal(List<LanguageData> outList)
  {
    if (!getUsableLanguages(outList))
      return null;

    if (outList.isEmpty())
    {
      // No supported languages found, lock down TTS :(
      lockDown();
      return null;
    }

    LanguageData res = getSelectedLanguage(outList);
    if (res == null || !res.downloaded)
      // Selected locale is not available or not downloaded
      res = getDefaultLanguage(outList);

    if (res == null || !res.downloaded)
    {
      // Default locale can not be used too
      Config.TTS.setEnabled(false);
      return null;
    }

    return res;
  }

  public @NonNull List<LanguageData> refreshLanguages()
  {
    List<LanguageData> res = new ArrayList<>();
    if (mUnavailable || mTts == null)
      return res;

    final LanguageData lang = refreshLanguagesInternal(res);
    mHasUsableLanguage = lang != null && setLanguage(lang);

    setEnabled(Config.TTS.isEnabled());
    notifyStateChanged();
    return res;
  }

  @NonNull
  private List<Pair<String, String>> getSupportedLanguages()
  {
    if (sSupportedLanguages == null)
      sSupportedLanguages = nativeGetSupportedLanguages();
    return sSupportedLanguages;
  }

  private native static void nativeEnableTurnNotifications(boolean enable);
  private native static boolean nativeAreTurnNotificationsEnabled();
  private native static void nativeSetTurnNotificationsLocale(String code);
  private native static String nativeGetTurnNotificationsLocale();
  @NonNull
  private native static List<Pair<String, String>> nativeGetSupportedLanguages();
}
