package app.organicmaps.sdk.routing.roadshield;

import androidx.annotation.Keep;

/** Native routing code resolves these enum constants by their Java field names. */
@Keep
public enum RoadShieldType {
  GenericWhite,
  GenericGreen,
  GenericBlue,
  GenericRed,
  GenericOrange,
  USInterstate,
  USHighway,
  UKHighway,
}
