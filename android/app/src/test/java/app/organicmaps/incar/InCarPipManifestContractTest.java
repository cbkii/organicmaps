package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Source-level guard for the deliberately narrow standard-Android InCar PiP capability. */
public class InCarPipManifestContractTest
{
  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
  private static final String MWM_ACTIVITY = "app.organicmaps.MwmActivity";

  @Test
  public void pipCapabilityLivesOnlyInInCarManifest() throws Exception
  {
    final Path root = findRepositoryRoot();
    final Path mainManifest = root.resolve("android/app/src/main/AndroidManifest.xml");
    final Path inCarManifest = root.resolve("android/app/src/inCar/AndroidManifest.xml");

    assertNull(activityAttribute(mainManifest, MWM_ACTIVITY, "supportsPictureInPicture"));
    assertEquals("true", activityAttribute(inCarManifest, MWM_ACTIVITY, "supportsPictureInPicture"));
  }

  @Test
  public void noAutomaticPipEntryPathIsIntroduced() throws IOException
  {
    final Path root = findRepositoryRoot();
    final String sharedActivity =
        Files.readString(root.resolve("android/app/src/main/java/app/organicmaps/MwmActivity.java"), StandardCharsets.UTF_8);
    assertNoAutoPip(sharedActivity);

    final Path inCarJava = root.resolve("android/app/src/main/java/app/organicmaps/incar");
    try (var files = Files.walk(inCarJava))
    {
      files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
        try
        {
          assertNoAutoPip(Files.readString(path, StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
          throw new RuntimeException(e);
        }
      });
    }
  }

  private static void assertNoAutoPip(String source)
  {
    assertFalse(source.contains("onUserLeaveHint("));
    assertFalse(source.contains("enterPictureInPictureMode("));
    assertFalse(source.contains("setAutoEnterEnabled("));
  }

  private static String activityAttribute(Path manifest, String activityName, String attribute) throws Exception
  {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    final NodeList activities = factory.newDocumentBuilder().parse(manifest.toFile()).getElementsByTagName("activity");
    for (int i = 0; i < activities.getLength(); ++i)
    {
      final Element activity = (Element) activities.item(i);
      if (!activityName.equals(activity.getAttributeNS(ANDROID_NS, "name")))
        continue;
      return activity.hasAttributeNS(ANDROID_NS, attribute) ? activity.getAttributeNS(ANDROID_NS, attribute) : null;
    }
    return null;
  }

  private static Path findRepositoryRoot()
  {
    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.isRegularFile(current.resolve("android/app/src/main/AndroidManifest.xml")))
        return current;
      current = current.getParent();
    }
    throw new IllegalStateException("Unable to locate repository root from " + System.getProperty("user.dir"));
  }
}
