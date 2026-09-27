package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Source-level guard for the non-blocking InCar location warning/settings contract. */
public final class InCarLocationSettingsContractTest
{
  private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
  private static final String WARNING_KEY = "@string/pref_in_car_location_disabled_warning";
  private static final String SETTINGS_KEY = "@string/pref_in_car_open_location_settings";

  @Test
  public void automaticLocationWarningDefaultsOffAndManualSettingsRemainAvailable() throws Exception
  {
    final Path root = findRepositoryRoot();
    final Path preferences = root.resolve("android/app/src/main/res/xml/prefs_in_car.xml");
    final NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(preferences.toFile())
                                                 .getDocumentElement().getElementsByTagName("*");

    Element warning = null;
    Element settings = null;
    for (int i = 0; i < nodes.getLength(); ++i)
    {
      final Element element = (Element) nodes.item(i);
      final String key = element.getAttributeNS(ANDROID_NS, "key");
      if (WARNING_KEY.equals(key))
        warning = element;
      else if (SETTINGS_KEY.equals(key))
        settings = element;
    }

    assertNotNull(warning);
    assertEquals("false", warning.getAttributeNS(ANDROID_NS, "defaultValue"));
    assertEquals("false", warning.getAttributeNS(ANDROID_NS, "persistent"));
    assertNotNull(settings);
  }

  @Test
  public void warningPreferenceUsesInCarSettingsStoreWithFalseFallbackAndSetter() throws Exception
  {
    final Path root = findRepositoryRoot();
    final Path store = root.resolve("android/app/src/main/java/app/organicmaps/incar/InCarSettingsStore.java");
    final String source = new String(Files.readAllBytes(store), StandardCharsets.UTF_8);

    assertTrue(source.contains("getBoolean(KEY_LOCATION_DISABLED_WARNING, false)"));
    assertTrue(source.contains("putBoolean(KEY_LOCATION_DISABLED_WARNING, enabled)"));
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