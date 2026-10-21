package app.organicmaps.test;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import org.junit.Assert;
import org.junit.Test;

/** Real linked native core coverage; independent of the retired Qt/host generator test closure. */
public class NativeCoreQualificationTest
{
  private static native void nativeQualifyBookmarks(String directory);

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
