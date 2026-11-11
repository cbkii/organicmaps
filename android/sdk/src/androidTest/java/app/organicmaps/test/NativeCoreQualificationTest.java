package app.organicmaps.test;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import app.organicmaps.sdk.OrganicMaps;
import app.organicmaps.sdk.util.ConnectionState;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Real linked native core coverage; independent of the retired Qt/host generator test closure. */
public class NativeCoreQualificationTest
{
  private static native void nativeQualifyBookmarks(String directory);

  private static File sPlatformRoot;

  @BeforeClass
  public static void initializeRealAndroidPlatform() throws Exception
  {
    System.loadLibrary("organicmaps");
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    sPlatformRoot = Files.createTempDirectory(context.getCacheDir().toPath(), "native-platform-").toFile();
    // Use the owning SDK initialisers without creating a Framework/routing authority.
    Method settings = OrganicMaps.class.getDeclaredMethod("nativeSetSettingsDir", String.class);
    Method initialize =
        OrganicMaps.class.getDeclaredMethod("nativeInitPlatform", Context.class, String.class, String.class,
                                            String.class, String.class, String.class, String.class, boolean.class);
    settings.setAccessible(true);
    initialize.setAccessible(true);
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      try
      {
        ConnectionState.INSTANCE.initialize(context);
        String path = sPlatformRoot.getAbsolutePath();
        settings.invoke(null, path);
        initialize.invoke(null, context, context.getApplicationInfo().sourceDir, path, path, path, "sdk-test", "debug",
                          false);
      }
      catch (ReflectiveOperationException error)
      {
        throw new AssertionError(error);
      }
    });
  }

  @AfterClass
  public static void removePlatformFixture()
  {
    if (sPlatformRoot != null)
      deleteTree(sPlatformRoot);
  }

  @Test
  public void bookmarkReloadPreservesIdentityMetadataAndDiskTimestamp() throws Exception
  {
    System.loadLibrary("organicmaps");
    File root =
        Files
            .createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(),
                                 "native-bookmark-qualification-")
            .toFile();
    try
    {
      nativeQualifyBookmarks(root.getAbsolutePath());
    }
    finally
    {
      deleteTree(root);
    }
  }

  @Test
  public void nativeFailureIsReportedAsAJavaException() throws Exception
  {
    System.loadLibrary("organicmaps");
    File file = File.createTempFile("native-bookmark-invalid-", ".tmp",
                                    InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
    try
    {
      nativeQualifyBookmarks(file.getAbsolutePath());
      Assert.fail("A file cannot be a bookmarks directory");
    }
    catch (IllegalStateException expected)
    {
      Assert.assertTrue(expected.getMessage().contains("isolated bookmarks directory"));
    }
    finally
    {
      file.delete();
    }
  }

  private static void deleteTree(File root)
  {
    File[] children = root.listFiles();
    if (children != null)
      for (File child : children)
        deleteTree(child);
    root.delete();
  }
}
