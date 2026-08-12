package app.organicmaps.incar;

import androidx.annotation.StringRes;
import app.organicmaps.R;

/** Search-category mapping for the fixed InCar quick actions. */
public final class InCarQuickCategoryPolicy
{
  public enum Category
  {
    FUEL,
    CHARGING,
    PARKING,
    TOILETS,
    FOOD
  }

  private InCarQuickCategoryPolicy() {}

  @StringRes
  public static int searchTermRes(Category category)
  {
    return switch (category)
    {
      case FUEL -> R.string.category_fuel;
      case CHARGING -> R.string.in_car_quick_charging_category_query;
      case PARKING -> R.string.category_parking;
      case TOILETS -> R.string.category_toilet;
      case FOOD -> R.string.category_eat;
    };
  }

  public static boolean usesEnglishCanonicalQuery(Category category)
  {
    return category == Category.CHARGING;
  }
}
