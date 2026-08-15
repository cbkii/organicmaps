package app.organicmaps.incar;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** Regression checks for the direct-display InCar road-vs-surroundings palette hierarchy. */
public class InCarMapPaletteContrastTest
{
  private static final Pattern COLOR =
      Pattern.compile("^@([A-Za-z0-9_]+):\\s*(#[0-9A-Fa-f]{6});\\s*$");
  private static final String[] ROAD_SURFACES = {
      "trunk0", "primary1", "secondary0", "residential", "unclassified"};
  private static final String[] NEUTRAL_SURROUNDINGS = {
      "background", "building0", "building1", "industrial", "parking", "parking_l", "aerodrome0"};

  @Test
  public void lightRoadSurfacesRemainDistinctFromNeutralSurroundings() throws IOException
  {
    final Map<String, Integer> palette = loadPalette("light");
    for (String road : ROAD_SURFACES)
    {
      for (String surrounding : NEUTRAL_SURROUNDINGS)
        assertContrastAtLeast(palette, road, surrounding, 2.0);
    }

    assertContrastAtLeast(palette, "casing_road_major", "background", 5.0);
    assertContrastAtLeast(palette, "casing_road_local", "background", 3.3);
    assertContrastAtLeast(palette, "label_light", "background", 4.5);
    assertContrastAtLeast(palette, "building_label", "building0", 4.5);
    assertContrastAtLeast(palette, "building_label", "building1", 4.5);
  }

  @Test
  public void darkRoadSurfacesRemainDistinctFromNeutralSurroundings() throws IOException
  {
    final Map<String, Integer> palette = loadPalette("dark");
    for (String road : ROAD_SURFACES)
    {
      for (String surrounding : NEUTRAL_SURROUNDINGS)
        assertContrastAtLeast(palette, road, surrounding, 3.8);
    }

    assertContrastAtLeast(palette, "trunk0", "casing_road_major", 9.0);
    assertContrastAtLeast(palette, "unclassified", "casing_road_local", 5.5);
  }

  @Test
  public void darkRoadLuminanceHierarchyKeepsMajorRoadsMostProminent() throws IOException
  {
    assertDescendingRoadLuminance(loadPalette("dark"));
  }

  private static void assertDescendingRoadLuminance(Map<String, Integer> palette)
  {
    double previous = Double.POSITIVE_INFINITY;
    for (String road : ROAD_SURFACES)
    {
      final double current = relativeLuminance(requireColor(palette, road));
      assertTrue(road + " must not be brighter than the preceding road class", current <= previous + 1.0e-9);
      previous = current;
    }
  }

  private static void assertContrastAtLeast(Map<String, Integer> palette, String foreground, String background,
                                            double minimum)
  {
    final double ratio = contrastRatio(requireColor(palette, foreground), requireColor(palette, background));
    assertTrue(foreground + " vs " + background + " contrast " + ratio + " must be >= " + minimum,
               ratio + 1.0e-9 >= minimum);
  }

  private static Map<String, Integer> loadPalette(String theme) throws IOException
  {
    final Path path = findRepositoryRoot().resolve("data/styles/in_car").resolve(theme).resolve("colors.mapcss");
    final Map<String, Integer> palette = new HashMap<>();
    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8))
    {
      final Matcher matcher = COLOR.matcher(line.trim());
      if (matcher.matches())
        palette.put(matcher.group(1), Integer.parseInt(matcher.group(2).substring(1), 16));
    }
    return palette;
  }

  private static Path findRepositoryRoot()
  {
    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null)
    {
      if (Files.isRegularFile(current.resolve("data/styles/in_car/light/colors.mapcss")))
        return current;
      current = current.getParent();
    }
    throw new IllegalStateException("Unable to locate repository root from " + System.getProperty("user.dir"));
  }

  private static int requireColor(Map<String, Integer> palette, String name)
  {
    final Integer color = palette.get(name);
    if (color == null)
      throw new IllegalStateException("Missing MapCSS colour @" + name);
    return color;
  }

  private static double contrastRatio(int first, int second)
  {
    final double firstLuminance = relativeLuminance(first);
    final double secondLuminance = relativeLuminance(second);
    final double lighter = Math.max(firstLuminance, secondLuminance);
    final double darker = Math.min(firstLuminance, secondLuminance);
    return (lighter + 0.05) / (darker + 0.05);
  }

  private static double relativeLuminance(int color)
  {
    final double red = linearComponent((color >> 16 & 0xFF) / 255.0);
    final double green = linearComponent((color >> 8 & 0xFF) / 255.0);
    final double blue = linearComponent((color & 0xFF) / 255.0);
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  }

  private static double linearComponent(double component)
  {
    if (component <= 0.04045)
      return component / 12.92;
    return Math.pow((component + 0.055) / 1.055, 2.4);
  }
}
