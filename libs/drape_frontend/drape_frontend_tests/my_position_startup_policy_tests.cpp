#include "drape_frontend/my_position_startup_policy.hpp"

#include "testing/testing.hpp"

namespace my_position_startup_policy_tests
{
void TestModes(df::MyPositionStartupModes const & modes, location::EMyPositionMode mode,
               location::EMyPositionMode desiredMode)
{
  TEST_EQUAL(static_cast<int>(modes.m_mode), static_cast<int>(mode), ());
  TEST_EQUAL(static_cast<int>(modes.m_desiredMode), static_cast<int>(desiredMode), ());
}

UNIT_TEST(AutoFollowDisabledPreservesStoppedLocation)
{
  TestModes(df::ResolveMyPositionStartupModes(false, false, false, false, false, location::NotFollowNoPosition),
            location::NotFollowNoPosition, location::NotFollowNoPosition);
}

UNIT_TEST(AutoFollowEnabledOverridesStoppedLocation)
{
  TestModes(df::ResolveMyPositionStartupModes(true, false, false, false, false, location::NotFollowNoPosition),
            location::PendingPosition, location::FollowAndRotate);
}

UNIT_TEST(DeepLinkTakesPrecedenceOverAutoFollow)
{
  TestModes(df::ResolveMyPositionStartupModes(true, true, false, false, false, location::NotFollowNoPosition),
            location::PendingPosition, location::NotFollow);
}

UNIT_TEST(DeepLinkPreservesBehaviorWhenAutoFollowDisabled)
{
  TestModes(df::ResolveMyPositionStartupModes(false, true, false, false, false, location::NotFollowNoPosition),
            location::PendingPosition, location::NotFollow);
}

UNIT_TEST(AutoFollowEnabledOverridesDefaultLaunchRules)
{
  TestModes(df::ResolveMyPositionStartupModes(true, false, true, false, false, location::NotFollow),
            location::PendingPosition, location::FollowAndRotate);
  TestModes(df::ResolveMyPositionStartupModes(true, false, false, true, false, location::NotFollow),
            location::PendingPosition, location::FollowAndRotate);
}

UNIT_TEST(AutoFollowDisabledPreservesDefaultLaunchRules)
{
  TestModes(df::ResolveMyPositionStartupModes(false, false, true, false, false, location::NotFollow),
            location::PendingPosition, location::Follow);
  TestModes(df::ResolveMyPositionStartupModes(false, false, false, true, false, location::NotFollow),
            location::PendingPosition, location::Follow);
  TestModes(df::ResolveMyPositionStartupModes(false, false, false, false, true, location::NotFollowNoPosition),
            location::PendingPosition, location::NotFollowNoPosition);
}

UNIT_TEST(AutoFollowRoutingStartupUsesFollowAndRotate)
{
  TestModes(df::ResolveMyPositionStartupModes(true, false, false, false, true, location::NotFollowNoPosition),
            location::PendingPosition, location::FollowAndRotate);
  TestModes(df::ResolveMyPositionStartupModes(false, false, false, false, true, location::FollowAndRotate),
            location::PendingPosition, location::FollowAndRotate);
}

UNIT_TEST(StartupDrivingAreaZoomIsForcedForDrivingAreaMode)
{
  TEST(df::ShouldUseStartupDrivingAreaZoom(true, 16, 10), ());
}

UNIT_TEST(StartupDrivingAreaZoomRepairsPathologicalLastView)
{
  TEST(df::ShouldUseStartupDrivingAreaZoom(false, 3, 10), ());
  TEST(!df::ShouldUseStartupDrivingAreaZoom(false, 10, 10), ());
  TEST(!df::ShouldUseStartupDrivingAreaZoom(false, 16, 10), ());
}
}  // namespace my_position_startup_policy_tests
