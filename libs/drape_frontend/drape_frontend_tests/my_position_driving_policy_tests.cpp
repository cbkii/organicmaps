#include "drape_frontend/my_position_driving_policy.hpp"

#include "testing/testing.hpp"

#include <limits>

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

UNIT_TEST(MyPositionDrivingPolicy_FastAcquisitionCourseGate)
{
  TEST(HasReliableDrivingCourse(false, true, true, kStationarySpeedMps, true), ());
  TEST(!HasReliableDrivingCourse(false, true, true, kStationarySpeedMps - 0.01, true), ());
  TEST(!HasReliableDrivingCourse(false, true, false, -1.0, true), ());
  TEST(!HasReliableDrivingCourse(false, true, true, kStationarySpeedMps, false), ());
  TEST(!HasReliableDrivingCourse(true, true, true, kStationarySpeedMps, true), ());
  TEST(!HasReliableDrivingCourse(false, false, true, kStationarySpeedMps, true), ());
  TEST(kFastAcquisitionDurationSec > 0.0 && kFastAcquisitionDurationSec < 1.0, ());
}

UNIT_TEST(MyPositionDrivingPolicy_CourseAccuracyAndFiniteEvidence)
{
  auto const course = [](double speed, double bearing, double speedAccuracy, double bearingAccuracy)
  { return HasReliableDrivingCourse(false, true, true, speed, true, bearing, speedAccuracy, bearingAccuracy); };
  TEST(course(5.0, 90.0, 0.5, 15.0), ());
  TEST(course(2.0, 90.0, -1.0, -1.0), ("OEM metadata absent"));
  TEST(!course(2.0, 90.0, 1.0, 15.0), ("speed indistinguishable from stationary"));
  TEST(!course(5.0, 90.0, 0.5, 31.0), ());
  TEST(!course(5.0, 360.0, 0.5, 15.0), ());
  TEST(!course(5.0, -1.0, 0.5, 15.0), ());
  double const nan = std::numeric_limits<double>::quiet_NaN();
  double const inf = std::numeric_limits<double>::infinity();
  TEST(!course(nan, 90.0, -1.0, -1.0), ());
  TEST(!course(inf, 90.0, -1.0, -1.0), ());
  TEST(!course(5.0, nan, -1.0, -1.0), ());
  TEST(!course(5.0, 90.0, nan, -1.0), ());
  TEST(!course(5.0, 90.0, -1.0, inf), ());
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
