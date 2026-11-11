#pragma once

#include "indexer/map_style.hpp"

#include <string_view>

namespace df
{
// InCar-only scaling of existing packed camera sprites. No atlas, shader or draw-pass changes.
// Regular Vehicle/Default styles and every non-camera symbol stay at their original size.
inline constexpr float GetInCarSpeedCameraSymbolScale(MapStyle style, std::string_view name)
{
  if (style != MapStyleInCarLight && style != MapStyleInCarDark)
    return 1.0f;

  if (name == "speedcam-s" || name == "speedcam-m" || name == "speedcam-alert-l")
    return 1.2f;

  return 1.0f;
}
}  // namespace df
