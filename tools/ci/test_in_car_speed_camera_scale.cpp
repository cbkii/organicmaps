// Deterministic native contract for the header-only InCar camera symbol policy.
#include "drape_frontend/speed_camera_symbol_scale.hpp"

#include <array>
#include <cstdio>
#include <string_view>

int main()
{
  std::array constexpr inCarStyles = {MapStyleInCarLight, MapStyleInCarDark};
  std::array constexpr otherStyles = {MapStyleDefaultLight, MapStyleDefaultDark,   MapStyleVehicleLight,
                                      MapStyleVehicleDark,  MapStyleOutdoorsLight, MapStyleOutdoorsDark,
                                      MapStyleMerged};
  std::array<std::string_view, 3> constexpr cameras = {"speedcam-s", "speedcam-m", "speedcam-alert-l"};
  std::array<std::string_view, 5> constexpr otherSymbols = {"fuel-m", "speedcam", "speedcam-bg-l", "speedcam-alert",
                                                            "parking-m"};

  for (auto const style : inCarStyles)
  {
    for (auto const name : cameras)
      if (df::GetInCarSpeedCameraSymbolScale(style, name) != 1.2f)
        return 1;
    for (auto const name : otherSymbols)
      if (df::GetInCarSpeedCameraSymbolScale(style, name) != 1.0f)
        return 2;
  }
  for (auto const style : otherStyles)
  {
    for (auto const name : cameras)
      if (df::GetInCarSpeedCameraSymbolScale(style, name) != 1.0f)
        return 3;
  }
  std::puts("PASS: InCar camera sprites scale 1.20x; other styles/symbols remain 1.00x");
}
