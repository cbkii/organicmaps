#include "drape_frontend/my_position_driving_policy.hpp"

#include "testing/testing.hpp"

namespace
{
using namespace df::driving_policy;

UNIT_TEST(MyPositionDrivingPolicy_NavigationStyleCamera)
{
  TEST(!IsNavigationStyleCameraActive(false, false), ());
  TEST(IsNavigationStyleCameraActive(true, false), ());
  TEST(IsNavigationStyleCameraActive(false, true), ());
  TEST(IsNavigationStyleCameraActive(true, true), ());
}

UNIT_TEST(MyPositionDrivingPolicy_AutoReturn)
{
  TEST(ShouldAutoReturn(true, false, false), ());
  TEST(ShouldAutoReturn(true, true, false), ());
  TEST(ShouldAutoReturn(false, true, true), ());
  TEST(!ShouldAutoReturn(false, true, false), ());
  TEST(!ShouldAutoReturn(false, false, true), ());
}

UNIT_TEST(MyPositionDrivingPolicy_AutoZoom)
{
  TEST(ShouldUseAutoZoom(location::FollowAndRotate, true, false, true, false), ());
  TEST(!ShouldUseAutoZoom(location::FollowAndRotate, true, false, false, false), ());
  TEST(ShouldUseAutoZoom(location::FollowAndRotate, false, true, false, false), ());
  TEST(!ShouldUseAutoZoom(location::FollowAndRotate, false, true, false, true), ());
  TEST(!ShouldUseAutoZoom(location::Follow, false, true, false, false), ());
}

UNIT_TEST(MyPositionDrivingPolicy_StationaryHoldOnlyInFreeDriving)
{
  double const below = kStationarySpeedMps - 0.01;
  TEST(ShouldHoldFreeDrivingCamera(false, true, true, true, below), ());
  TEST(ShouldHoldFreeDrivingCamera(false, true, true, false, -1.0), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, true, true, true, kStationarySpeedMps), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, true, false, true, below), ());
  TEST(!ShouldHoldFreeDrivingCamera(true, true, true, true, below), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, false, true, true, below), ());
}
}  // namespace
