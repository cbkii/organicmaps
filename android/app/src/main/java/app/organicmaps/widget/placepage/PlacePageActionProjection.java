package app.organicmaps.widget.placepage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared direct/overflow projection reserves the primary destination action. */
public final class PlacePageActionProjection
{
  private PlacePageActionProjection() {}
  public record Result(List<PlacePageButtons.ButtonType> direct, List<PlacePageButtons.ButtonType> overflow)
  {}
  public static int slotsForWidth(int available, int preferredFootprint, int maximum)
  {
    return Math.min(Math.max(2, maximum), Math.max(2, available / Math.max(1, preferredFootprint)));
  }

  public static int cellWidth(int available, int slots, int preferredWidth, int gap)
  {
    return Math.max(1, Math.min(preferredWidth, available / Math.max(1, slots) - 2 * gap));
  }

  public static Result project(List<PlacePageButtons.ButtonType> actions, int maximum, boolean inCar)
  {
    List<PlacePageButtons.ButtonType> ordered = new ArrayList<>(inCar ? new LinkedHashSet<>(actions) : actions);
    if (inCar && ordered.remove(PlacePageButtons.ButtonType.ROUTE_TO))
      ordered.add(0, PlacePageButtons.ButtonType.ROUTE_TO);
    int slots = Math.max(inCar ? 2 : 1, maximum);
    int directCount = ordered.size() > slots ? slots - 1 : ordered.size();
    List<PlacePageButtons.ButtonType> direct = new ArrayList<>(ordered.subList(0, directCount));
    List<PlacePageButtons.ButtonType> overflow = new ArrayList<>(ordered.subList(directCount, ordered.size()));
    if (!overflow.isEmpty())
      direct.add(PlacePageButtons.ButtonType.MORE);
    return new Result(direct, overflow);
  }
}
