package app.organicmaps.incar;

import androidx.annotation.StringRes;
import app.organicmaps.R;

/** Search-category mapping for the fixed InCar quick actions. */
public final class InCarQuickCategoryPolicy
{
  public enum Category
  {
    FUEL_CHARGING,
    PARKING,
    TOILETS,
    FOOD,
    REST_WATER
  }

  private InCarQuickCategoryPolicy() {}

  @StringRes
  public static int searchTermRes(Category category)
  {
    return switch (category)
    {
      case FUEL_CHARGING -> R.string.in_car_quick_fuel_charging_category_query;
      case PARKING -> R.string.category_parking;
      case TOILETS -> R.string.category_toilet;
      case FOOD -> R.string.category_eat;
      case REST_WATER -> R.string.in_car_quick_rest_water_category_query;
    };
  }

  public static boolean usesEnglishCanonicalQuery(Category category)
  {
    return category == Category.FUEL_CHARGING || category == Category.REST_WATER;
  }
}
