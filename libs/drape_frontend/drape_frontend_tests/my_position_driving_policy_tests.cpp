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

UNIT_TEST(MyPositionDrivingPolicy_InCarFreeDrivingMotion)
{
  double const below = kFreeDrivingMinSpeedMps - 0.01;
  TEST_EQUAL(kFreeDrivingAutoReturnSeconds, 10.0, ());
  TEST(IsInCarFreeDrivingMotion(true, true, true, kFreeDrivingMinSpeedMps), ());
  TEST(!IsInCarFreeDrivingMotion(true, true, true, below), ());
  TEST(!IsInCarFreeDrivingMotion(true, true, false, -1.0), ());
  TEST(!IsInCarFreeDrivingMotion(true, false, true, kFreeDrivingMinSpeedMps), ());
  TEST(!IsInCarFreeDrivingMotion(false, true, true, kFreeDrivingMinSpeedMps), ());

  TEST(ShouldAutoReturn(false, false, false, true, true, true, kFreeDrivingMinSpeedMps), ());
  TEST(!ShouldAutoReturn(false, false, false, true, true, true, below), ());
}

UNIT_TEST(MyPositionDrivingPolicy_InCarFreeDrivingAutoZoom)
{
  double const below = kFreeDrivingMinSpeedMps - 0.01;
  TEST(ShouldUseAutoZoom(location::FollowAndRotate, false, false, false, true, true, true,
                         kFreeDrivingMinSpeedMps, false),
       ());
  TEST(!ShouldUseAutoZoom(location::FollowAndRotate, false, false, false, true, true, true, below, false), ());
  TEST(!ShouldUseAutoZoom(location::FollowAndRotate, false, false, false, false, true, true,
                          kFreeDrivingMinSpeedMps, false),
       ());
  TEST(!ShouldUseAutoZoom(location::FollowAndRotate, false, false, false, true, false, true,
                          kFreeDrivingMinSpeedMps, false),
       ());
  TEST(!ShouldUseAutoZoom(location::FollowAndRotate, false, false, false, true, true, true,
                          kFreeDrivingMinSpeedMps, true),
       ());
}

UNIT_TEST(MyPositionDrivingPolicy_StationaryHoldOnlyInFreeDriving)
{
  double const below = kStationarySpeedMps - 0.01;
  double const insideHoldRadius = kStationaryHoldRadiusMeters - 0.1;
  TEST(ShouldHoldFreeDrivingCamera(false, true, true, true, below, insideHoldRadius), ());
  TEST(ShouldHoldFreeDrivingCamera(false, true, true, false, -1.0, insideHoldRadius), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, true, true, true, kStationarySpeedMps, insideHoldRadius), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, true, false, true, below, insideHoldRadius), ());
  TEST(!ShouldHoldFreeDrivingCamera(true, true, true, true, below, insideHoldRadius), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, false, true, true, below, insideHoldRadius), ());
}

UNIT_TEST(MyPositionDrivingPolicy_StationaryHoldReleasesOnMeaningfulDisplacement)
{
  double const below = kStationarySpeedMps - 0.01;
  TEST(ShouldHoldFreeDrivingCamera(false, true, true, true, below, kStationaryHoldRadiusMeters - 0.1), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, true, true, true, below, kStationaryHoldRadiusMeters), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, true, true, true, below, 33.0), ());
  TEST(!ShouldHoldFreeDrivingCamera(false, true, true, false, -1.0, 33.0), ());
}
}  // namespace
