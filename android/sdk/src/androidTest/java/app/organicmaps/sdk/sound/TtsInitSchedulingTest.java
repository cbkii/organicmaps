package app.organicmaps.sdk.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class TtsInitSchedulingTest
{
  @Test
  public void synchronousInitCallbackIsQueuedUntilEngineAssignmentCompletes() throws Exception
  {
    final CountDownLatch callbackRan = new CountDownLatch(1);
    final AtomicBoolean engineAssigned = new AtomicBoolean(false);
    final AtomicBoolean sawUnassignedEngine = new AtomicBoolean(false);

    // Model a TextToSpeech implementation that invokes OnInitListener synchronously on the main thread
    // before its constructor returns. The production dispatcher must queue the callback, otherwise TtsPlayer
    // can dereference mTts before the constructor result has been assigned to it.
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      TtsPlayer.postInitializationCallback(() -> {
        sawUnassignedEngine.set(!engineAssigned.get());
        callbackRan.countDown();
      });

      assertEquals(1L, callbackRan.getCount());
      engineAssigned.set(true);
    });

    assertTrue("Deferred TTS init callback did not run", callbackRan.await(2, TimeUnit.SECONDS));
    assertFalse("TTS init callback observed pre-assignment state", sawUnassignedEngine.get());
  }
}
