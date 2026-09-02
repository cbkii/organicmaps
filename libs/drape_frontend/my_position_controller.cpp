#include "drape_frontend/my_position_controller.hpp"

#include "drape_frontend/animation/arrow_animation.hpp"
#include "drape_frontend/animation_system.hpp"
#include "drape_frontend/animation_utils.hpp"
#include "drape_frontend/drape_notifier.hpp"
#include "drape_frontend/my_position_driving_policy.hpp"
#include "drape_frontend/my_position_startup_policy.hpp"
#include "drape_frontend/user_event_stream.hpp"
#include "drape_frontend/visual_params.hpp"

#include "geometry/mercator.hpp"

#include "platform/measurement_utils.hpp"
#include "platform/settings.hpp"

#include "base/math.hpp"

#include <algorithm>
#include <array>
#include <chrono>
#include <string_view>

namespace df
{
namespace
{
int const kPositionRoutingOffsetY = 104;

// https://t.me/OrganicMapsRu/88317
double const kMinSpeedThresholdMps = 0.7;  // for the pedestrian mode 2.5 km/h
/// @todo Should depend on the _previous_ avg speed (say for the last 5 minutes).
/// Bigger for cars (up to 30 seconds is ok, IMO) and lower for pedestrians.
double const kGpsBearingLifetimeSec = 3.0;

double const kMaxTimeInBackgroundSec = 60.0 * 60 * 30;  // 30 hours before starting detecting position again
double const kMaxNotFollowRoutingTimeSec = 20.0;
double const kMaxUpdateLocationInvervalSec = 30.0;
double const kMaxBlockAutoZoomTimeSec = 10.0;

std::string_view constexpr kAutoStartLocationFollowAndRotate = "AutoStartLocationFollowAndRotate";
std::string_view constexpr kInCarFreeDrivingAutoZoom = "InCarFreeDrivingAutoZoom";

int const kZoomThreshold = 10;
int const kMaxScaleZoomLevel = 16;
int const kDefaultAutoZoom = 16;
double const kUnknownAutoZoom = -1.0;

inline int GetZoomLevel(ScreenBase const & screen)
{
  return static_cast<int>(df::GetZoomLevel(screen.GetScale()));
}

int GetZoomLevel(ScreenBase const & screen, m2::PointD const & position, double errorRadius)
{
  ScreenBase s = screen;
  m2::PointD const size(errorRadius, errorRadius);
  s.SetFromRect(
      m2::AnyRectD(position, ang::Angle<double>(screen.GetAngle()), m2::RectD(position - size, position + size)));
  return GetZoomLevel(s);
}

inline double GetVisualScale()
{
  return df::VisualParams::Instance().GetVisualScale();
}

// Calculate zoom value in meters per pixel.
double CalculateZoomBySpeed(double speedMpS, bool isPerspectiveAllowed)
{
  using TSpeedScale = std::pair<double, double>;
  static std::array<TSpeedScale, 6> const scales3d = {{
      {20.0, 0.25},
      {40.0, 0.75},
      {60.0, 1.50},
      {75.0, 2.50},
      {85.0, 3.75},
      {95.0, 6.00},
  }};

  static std::array<TSpeedScale, 6> const scales2d = {{
      {20.0, 0.70},
      {40.0, 1.25},
      {60.0, 2.25},
      {75.0, 3.00},
      {85.0, 3.75},
      {95.0, 6.00},
  }};

  std::array<TSpeedScale, 6> const & scales = isPerspectiveAllowed ? scales3d : scales2d;

  double constexpr kDefaultSpeedKmpH = 80.0;
  double const speedKmpH = speedMpS >= 0 ? measurement_utils::MpsToKmph(speedMpS) : kDefaultSpeedKmpH;

  size_t i = 0;
  for (size_t sz = scales.size(); i < sz; ++i)
    if (scales[i].first >= speedKmpH)
      break;

  double const vs = GetVisualScale();

  if (i == 0)
    return scales.front().second / vs;
  if (i == scales.size())
    return scales.back().second / vs;

  double const minSpeed = scales[i - 1].first;
  double const maxSpeed = scales[i].first;
  double const k = (speedKmpH - minSpeed) / (maxSpeed - minSpeed);

  double const minScale = scales[i - 1].second;
  double const maxScale = scales[i].second;
  double const zoom = minScale + k * (maxScale - minScale);

  return zoom / vs;
}

void ResetNotification(uint64_t & notifyId)
{
  notifyId = DrapeNotifier::kInvalidId;
}

bool IsModeChangeViewport(location::EMyPositionMode mode)
{
  return mode == location::Follow || mode == location::FollowAndRotate;
}
}  // namespace

MyPositionController::MyPositionController(Params && params, ref_ptr<DrapeNotifier> notifier)
  : m_notifier(notifier)
  , m_modeChangeCallback(std::move(params.m_myPositionModeCallback))
  , m_hints(params.m_hints)
  , m_isInRouting(params.m_isRoutingActive)
  , m_needBlockAnimation(false)
  , m_wasRotationInScaling(false)
  , m_errorRadius(0.0)
  , m_horizontalAccuracy(0.0)
  , m_position(m2::PointD::Zero())
  , m_drawDirection(0.0)
  , m_oldPosition(m2::PointD::Zero())
  , m_oldDrawDirection(0.0)
  , m_enablePerspectiveInRouting(false)
  , m_enableAutoZoomInRouting(params.m_isAutozoomEnabled)
  , m_autoScale2d(GetScreenScale(kDefaultAutoZoom))
  , m_autoScale3d(m_autoScale2d)
  , m_lastGPSBearingTimer(false)
  , m_lastLocationTimestamp(0.0)
  , m_positionRoutingOffsetY(kPositionRoutingOffsetY * GetVisualScale())
  , m_isDirtyViewport(false)
  , m_isDirtyAutoZoom(false)
  , m_isPendingAnimation(false)
  , m_isPositionAssigned(false)
  , m_isDirectionAssigned(false)
  , m_isCompassAvailable(false)
  , m_positionIsObsolete(false)
  , m_needBlockAutoZoom(false)
  , m_routingNotFollowNotifyId(DrapeNotifier::kInvalidId)
  , m_blockAutoZoomNotifyId(DrapeNotifier::kInvalidId)
  , m_updateLocationNotifyId(DrapeNotifier::kInvalidId)
{
  RefreshFreeDrivingSettings();
  auto const startupModes = ResolveMyPositionStartupModes(
      m_autoStartFollowAndRotate, m_hints.m_isLaunchByDeepLink, m_hints.m_isFirstLaunch,
      params.m_timeInBackground >= kMaxTimeInBackgroundSec, m_isInRouting, params.m_initMode);
  m_mode = startupModes.m_mode;
  m_desiredInitMode = startupModes.m_desiredMode;

  if (m_modeChangeCallback)
    m_modeChangeCallback(m_mode, m_isInRouting);
}

void MyPositionController::UpdatePosition()
{
  UpdateViewport(kDoNotChangeZoom);
}

void MyPositionController::OnUpdateScreen(ScreenBase const & screen)
{
  m_pixelRect = screen.PixelRectIn3d();
  if (m_visiblePixelRect.IsEmptyInterior())
    SetVisibleViewport(m_pixelRect);
}

void MyPositionController::SetVisibleViewport(m2::RectD const & rect)
{
  m_visiblePixelRect = rect;
}

void MyPositionController::SetListener(ref_ptr<MyPositionController::Listener> listener)
{
  m_listener = listener;
}

m2::PointD const & MyPositionController::Position() const
{
  return m_position;
}

double MyPositionController::GetErrorRadius() const
{
  return m_errorRadius;
}

double MyPositionController::GetHorizontalAccuracy() const
{
  return m_horizontalAccuracy;
}

bool MyPositionController::IsModeChangeViewport() const
{
  return df::IsModeChangeViewport(m_mode);
}

bool MyPositionController::IsModeHasPosition() const
{
  return m_mode != location::PendingPosition && m_mode != location::NotFollowNoPosition;
}

bool MyPositionController::IsNavigationStyleCameraActive() const
{
  return driving_policy::IsNavigationStyleCameraActive(m_isInRouting, m_isDrivingView);
}

void MyPositionController::DragStarted()
{
  m_needBlockAnimation = true;
}

void MyPositionController::DragEnded(m2::PointD const & distance)
{
  float const kBindingDistance = 0.1f;
  m_needBlockAnimation = false;
  if (distance.Length() > kBindingDistance * std::min(m_pixelRect.SizeX(), m_pixelRect.SizeY()))
    StopLocationFollow();

  UpdateViewport(kDoNotChangeZoom);
}

void MyPositionController::ScaleStarted()
{
  m_needBlockAnimation = true;
  ResetBlockAutoZoomTimer();
}

void MyPositionController::ScaleEnded()
{
  m_needBlockAnimation = false;
  ResetBlockAutoZoomTimer();
  if (m_wasRotationInScaling)
  {
    m_wasRotationInScaling = false;
    StopLocationFollow();
  }

  UpdateViewport(kDoNotChangeZoom);
}

void MyPositionController::Rotated()
{
  if (m_mode == location::FollowAndRotate)
    m_wasRotationInScaling = true;
}

void MyPositionController::Scrolled(m2::PointD const & distance)
{
  if (m_mode == location::PendingPosition)
    return;

  if (distance.Length() > 0)
    StopLocationFollow();

  UpdateViewport(kDoNotChangeZoom);
}

void MyPositionController::ResetRoutingNotFollowTimer(bool blockTimer)
{
  RefreshFreeDrivingSettings();
  if (driving_policy::ShouldAutoReturn(m_isInRouting, m_isDrivingView, m_autoReturnDrivingView, m_isInCarFreeDriving,
                                       m_autoStartFollowAndRotate, m_hasLocationSpeed, m_locationSpeedMps))
  {
    m_routingNotFollowTimer.Reset();
    m_blockRoutingNotFollowTimer = blockTimer;
    ResetNotification(m_routingNotFollowNotifyId);
  }
  else
  {
    m_blockRoutingNotFollowTimer = false;
    ResetNotification(m_routingNotFollowNotifyId);
  }
}

void MyPositionController::ResetBlockAutoZoomTimer()
{
  RefreshFreeDrivingSettings();
  bool const freeDrivingAutoZoom = !m_isInRouting && !m_isDrivingView && m_isInCarFreeDriving &&
                                   m_enableFreeDrivingAutoZoom && m_autoStartFollowAndRotate;
  bool const autoZoomActive =
      (m_isInRouting && m_enableAutoZoomInRouting) || (!m_isInRouting && m_isDrivingView) || freeDrivingAutoZoom;
  if (autoZoomActive)
  {
    m_needBlockAutoZoom = true;
    m_blockAutoZoomTimer.Reset();
    ResetNotification(m_blockAutoZoomNotifyId);
  }
  else
  {
    m_needBlockAutoZoom = false;
    ResetNotification(m_blockAutoZoomNotifyId);
  }
}

void MyPositionController::CorrectScalePoint(m2::PointD & pt) const
{
  if (IsModeChangeViewport())
    pt = GetRotationPixelCenter();
}

void MyPositionController::CorrectScalePoint(m2::PointD & pt1, m2::PointD & pt2) const
{
  if (IsModeChangeViewport())
  {
    m2::PointD const oldPt1(pt1);
    pt1 = GetRotationPixelCenter();
    pt2 = pt2 - oldPt1 + pt1;
  }
}

void MyPositionController::CorrectGlobalScalePoint(m2::PointD & pt) const
{
  if (IsModeChangeViewport())
    pt = m_position;
}

void MyPositionController::SetRenderShape(ref_ptr<dp::GraphicsContext> context, ref_ptr<dp::TextureManager> texMng,
                                          drape_ptr<MyPosition> && shape, Arrow3d::PreloadedData && preloadedData)
{
  m_shape = std::move(shape);
  if (!m_shape->InitArrow(context, texMng, std::move(preloadedData)))
  {
    m_shape.reset();
    LOG(LERROR, ("Invalid Arrow3D mesh."));
  }
}

void MyPositionController::ResetRenderShape()
{
  m_shape.reset();
}

void MyPositionController::NextMode(ScreenBase const & screen)
{
  if (IsWaitingForLocation())
  {
    m_desiredInitMode = location::Follow;
    ChangeMode(location::NotFollowNoPosition);
    return;
  }

  if (m_mode == location::NotFollowNoPosition)
  {
    ChangeMode(location::PendingPosition);

    if (!m_isPositionAssigned)
      m_desiredInitMode = location::Follow;
    return;
  }

  int const currentZoom = GetZoomLevel(screen);
  int preferredZoomLevel = kDoNotChangeZoom;
  if (currentZoom < kZoomThreshold)
    preferredZoomLevel = std::min(GetZoomLevel(screen, m_position, m_errorRadius), kMaxScaleZoomLevel);

  if (m_mode == location::NotFollow)
  {
    ChangeMode(IsNavigationStyleCameraActive() ? location::FollowAndRotate : location::Follow);
    UpdateViewport(preferredZoomLevel);
    return;
  }

  if (m_mode == location::Follow)
  {
    if (IsRotationAvailable() || IsNavigationStyleCameraActive())
    {
      ChangeMode(location::FollowAndRotate);
      UpdateViewport(preferredZoomLevel);
    }
    return;
  }

  if (m_mode == location::FollowAndRotate)
  {
    if (IsNavigationStyleCameraActive() && screen.isPerspective())
      preferredZoomLevel = static_cast<int>(GetZoomLevel(ScreenBase::GetStartPerspectiveScale() * 1.1));
    ChangeMode(location::Follow);
    ChangeModelView(m_position, 0.0, m_visiblePixelRect.Center(), preferredZoomLevel);
  }
}

void MyPositionController::OnLocationUpdate(location::GpsInfo const & info, bool isNavigable, ScreenBase const & screen)
{
  bool const wasFreeDrivingMotion = driving_policy::IsInCarFreeDrivingMotion(
      m_isInCarFreeDriving, m_autoStartFollowAndRotate, m_hasLocationSpeed, m_locationSpeedMps);
  RefreshFreeDrivingSettings();
  m_hasLocationSpeed = info.HasSpeed() && info.m_speed >= 0.0;
  m_locationSpeedMps = m_hasLocationSpeed ? info.m_speed : -1.0;
  bool const isFreeDrivingMotion = driving_policy::IsInCarFreeDrivingMotion(
      m_isInCarFreeDriving, m_autoStartFollowAndRotate, m_hasLocationSpeed, m_locationSpeedMps);
  bool const canArmFreeDrivingReturn = m_mode == location::NotFollow || m_mode == location::NotFollowNoPosition;
  if (!m_isInRouting && !m_isDrivingView && canArmFreeDrivingReturn && wasFreeDrivingMotion != isFreeDrivingMotion)
    ResetRoutingNotFollowTimer();

  m2::PointD const newPosition = mercator::FromLatLon(info.m_latitude, info.m_longitude);
  double const displacementMeters = m_isPositionAssigned ? mercator::DistanceOnEarth(m_position, newPosition) : 0.0;
  if (driving_policy::ShouldHoldFreeDrivingCamera(m_isInRouting, m_isDrivingView, m_isPositionAssigned, info.HasSpeed(),
                                                  info.m_speed, displacementMeters))
  {
    // Framework/routing/search already received the raw fix. Keep nearby low-speed jitter out of the camera, but
    // retain the newest raw position for a later meaningful move, Driving View exit or route start.
    m_lastHeldDrivingPosition = newPosition;
    m_hasLastHeldDrivingPosition = true;
    RefreshLocationFreshness(info);
    return;
  }

  m_hasLastHeldDrivingPosition = false;
  m2::PointD const oldPos = GetDrawablePosition();
  double const oldAzimut = GetDrawableAzimut();

  m2::RectD const rect = mercator::MetersToXY(info.m_longitude, info.m_latitude, info.m_horizontalAccuracy);
  m_position = newPosition;
  m_errorRadius = rect.SizeX() * 0.5;
  m_horizontalAccuracy = info.m_horizontalAccuracy;

  if (info.m_speed > 0.0)
  {
    double const mercatorPerMeter = m_errorRadius / info.m_horizontalAccuracy;
    m_autoScale2d = mercatorPerMeter * CalculateZoomBySpeed(info.m_speed, false /* isPerspectiveAllowed */);
    m_autoScale3d = mercatorPerMeter * CalculateZoomBySpeed(info.m_speed, true /* isPerspectiveAllowed */);
  }
  else
  {
    m_autoScale2d = m_autoScale3d = kUnknownAutoZoom;
  }

  bool const isMovingFast = info.HasSpeed() && info.m_speed > kMinSpeedThresholdMps;
  bool const glueArrowInRouting = isNavigable && m_isArrowGluedInRouting;
  bool const isReliableFreeDrivingCourse =
      m_isDrivingView && !m_isInRouting && info.HasSpeed() && info.m_speed >= driving_policy::kStationarySpeedMps;

  if ((!m_isCompassAvailable || glueArrowInRouting || isMovingFast || isReliableFreeDrivingCourse) && info.HasBearing())
  {
    SetDirection(math::DegToRad(info.m_bearing));
    m_lastGPSBearingTimer.Reset();
  }

  if (m_isPositionAssigned && (!AlmostCurrentPosition(oldPos) || !AlmostCurrentAzimut(oldAzimut)))
  {
    CreateAnim(oldPos, oldAzimut, screen);
    m_isDirtyViewport = true;
  }

  m_positionIsObsolete = false;

  if (!m_isPositionAssigned)
  {
    location::EMyPositionMode newMode = m_desiredInitMode;
    ChangeMode(newMode);

    if (!m_hints.m_isFirstLaunch || !AnimationSystem::Instance().AnimationExists(Animation::Object::MapPlane))
    {
      if (m_mode == location::Follow)
      {
        ChangeModelView(m_position, kDoNotChangeZoom);
      }
      else if (m_mode == location::FollowAndRotate)
      {
        ChangeModelView(m_position, m_drawDirection,
                        IsNavigationStyleCameraActive() ? GetRoutingRotationPixelCenter() : m_visiblePixelRect.Center(),
                        kDoNotChangeZoom);
      }
    }
  }
  else if (m_mode == location::PendingPosition)
  {
    if (IsNavigationStyleCameraActive())
    {
      ChangeMode(location::FollowAndRotate);
      UpdateViewport(kMaxScaleZoomLevel);
    }
    else
    {
      ChangeMode(location::Follow);
      if (m_hints.m_isFirstLaunch)
      {
        if (!AnimationSystem::Instance().AnimationExists(Animation::Object::MapPlane))
          ChangeModelView(m_position, kDoNotChangeZoom);
      }
      else if (GetZoomLevel(screen, m_position, m_errorRadius) <= kMaxScaleZoomLevel)
      {
        m2::PointD const size(m_errorRadius, m_errorRadius);
        ChangeModelView(m2::RectD(m_position - size, m_position + size));
      }
      else
      {
        ChangeModelView(m_position, kMaxScaleZoomLevel);
      }
    }
  }
  else if (m_mode == location::NotFollowNoPosition)
  {
    if (IsNavigationStyleCameraActive())
    {
      ChangeMode(location::FollowAndRotate);
      UpdateViewport(kMaxScaleZoomLevel);
    }
    else
    {
      ChangeMode(location::NotFollow);
    }
  }

  m_isPositionAssigned = true;

  if (m_listener != nullptr)
    m_listener->PositionChanged(Position(), IsModeHasPosition());

  RefreshLocationFreshness(info);
}

void MyPositionController::RefreshLocationFreshness(location::GpsInfo const & info)
{
  m_positionIsObsolete = false;
  if (fabs(m_lastLocationTimestamp - info.m_timestamp) > 1.0E-5)
  {
    m_lastLocationTimestamp = info.m_timestamp;
    m_updateLocationTimer.Reset();
    ResetNotification(m_updateLocationNotifyId);
  }
}

void MyPositionController::RefreshFreeDrivingSettings()
{
  bool autoStartFollowAndRotate = false;
  (void)settings::Get(kAutoStartLocationFollowAndRotate, autoStartFollowAndRotate);

  bool freeDrivingAutoZoom = false;
  bool const isInCarFreeDriving = settings::Get(kInCarFreeDrivingAutoZoom, freeDrivingAutoZoom);

  m_autoStartFollowAndRotate = autoStartFollowAndRotate;
  m_isInCarFreeDriving = isInCarFreeDriving;
  m_enableFreeDrivingAutoZoom = isInCarFreeDriving && freeDrivingAutoZoom;
}

void MyPositionController::LoseLocation()
{
  m_hasLocationSpeed = false;
  m_locationSpeedMps = -1.0;
  if (!m_isInRouting && !m_isDrivingView)
    ResetRoutingNotFollowTimer();

  if (m_mode == location::NotFollowNoPosition)
    return;
  else if (m_mode == location::Follow || m_mode == location::FollowAndRotate)
    ChangeMode(location::PendingPosition);
  else
    ChangeMode(location::NotFollowNoPosition);

  if (m_listener != nullptr)
    m_listener->PositionChanged(Position(), false /* hasPosition */);
}

void MyPositionController::OnCompassUpdate(location::CompassInfo const & info, ScreenBase const & screen)
{
  m_isCompassAvailable = true;

  if (m_isDrivingView && !m_isInRouting)
    return;

  if (m_isArrowGluedInRouting && IsInRouting())
    return;

  if (m_lastGPSBearingTimer.ElapsedSeconds() < kGpsBearingLifetimeSec)
    return;

  double const oldAzimut = GetDrawableAzimut();

  SetDirection(info.m_bearing);

  if (m_isPositionAssigned && m_mode == location::FollowAndRotate && !AlmostCurrentAzimut(oldAzimut))
  {
    CreateAnim(GetDrawablePosition(), oldAzimut, screen);
    m_isDirtyViewport = true;
  }
}

bool MyPositionController::UpdateViewportWithAutoZoom()
{
  bool const useAutoZoom = driving_policy::ShouldUseAutoZoom(
      m_mode, m_isInRouting, m_isDrivingView, m_enableAutoZoomInRouting, m_enableFreeDrivingAutoZoom,
      m_autoStartFollowAndRotate, m_hasLocationSpeed, m_locationSpeedMps, m_needBlockAutoZoom);
  if (!useAutoZoom)
    return false;

  bool const usePerspectiveScale = m_isInRouting ? m_enablePerspectiveInRouting : m_isDrivingView;
  double const autoScale = usePerspectiveScale ? m_autoScale3d : m_autoScale2d;
  if (autoScale <= 0.0)
    return false;

  m2::PointD const pixelCenter =
      IsNavigationStyleCameraActive() ? GetRoutingRotationPixelCenter() : m_visiblePixelRect.Center();
  ChangeModelView(autoScale, m_position, m_drawDirection, pixelCenter);
  return true;
}

void MyPositionController::Render(ref_ptr<dp::GraphicsContext> context, ref_ptr<gpu::ProgramManager> mng,
                                  ScreenBase const & screen, int zoomLevel, FrameValues const & frameValues)
{
  CheckNotFollowRouting();

  if (m_shape != nullptr && IsModeHasPosition())
  {
    CheckBlockAutoZoom();
    CheckUpdateLocation();

    if ((m_isDirtyViewport || m_isDirtyAutoZoom) && !m_needBlockAnimation)
    {
      if (!UpdateViewportWithAutoZoom() && m_isDirtyViewport)
        UpdateViewport(kDoNotChangeZoom);
      m_isDirtyViewport = false;
      m_isDirtyAutoZoom = false;
    }

    if (!IsModeChangeViewport())
      m_isPendingAnimation = false;

    m_shape->SetPositionObsolete(m_positionIsObsolete);
    m_shape->SetPosition(m2::PointF(GetDrawablePosition()));
    m_shape->SetAzimuth(static_cast<float>(GetDrawableAzimut()));
    m_shape->SetIsValidAzimuth(IsRotationAvailable());
    m_shape->SetAccuracy(static_cast<float>(m_errorRadius));
    m_shape->SetRoutingMode(IsInRouting());

    if (!m_hints.m_screenshotMode)
    {
      m_shape->RenderAccuracy(context, mng, screen, zoomLevel, frameValues);
      m_shape->RenderMyPosition(context, mng, screen, zoomLevel, frameValues);
    }
  }
}

bool MyPositionController::IsRouteFollowingActive() const
{
  return IsInRouting() && m_mode == location::FollowAndRotate;
}

bool MyPositionController::AlmostCurrentPosition(m2::PointD const & pos) const
{
  double constexpr kPositionEqualityDelta = 1e-5;
  return pos.EqualDxDy(m_position, kPositionEqualityDelta);
}

bool MyPositionController::AlmostCurrentAzimut(double azimut) const
{
  double constexpr kDirectionEqualityDelta = 1e-3;
  return AlmostEqualAbs(azimut, m_drawDirection, kDirectionEqualityDelta);
}

void MyPositionController::SetDirection(double bearing)
{
  m_drawDirection = bearing;
  m_isDirectionAssigned = true;
}

void MyPositionController::ChangeMode(location::EMyPositionMode newMode)
{
  if (IsNavigationStyleCameraActive() && (m_mode != newMode) && (newMode == location::FollowAndRotate))
    ResetBlockAutoZoomTimer();

  m_mode = newMode;
  if (m_modeChangeCallback)
    m_modeChangeCallback(m_mode, m_isInRouting);
}

bool MyPositionController::IsWaitingForLocation() const
{
  if (m_mode == location::NotFollowNoPosition)
    return false;

  if (!m_isPositionAssigned)
    return true;

  return m_mode == location::PendingPosition;
}

void MyPositionController::StopLocationFollow()
{
  if (m_mode == location::Follow || m_mode == location::FollowAndRotate)
    ChangeMode(location::NotFollow);
  m_desiredInitMode = location::NotFollow;

  ResetRoutingNotFollowTimer();
}

void MyPositionController::OnEnterForeground(double backgroundTime)
{
  if (backgroundTime >= kMaxTimeInBackgroundSec)
  {
    if (m_mode == location::NotFollow)
    {
      ChangeMode(IsNavigationStyleCameraActive() ? location::FollowAndRotate : location::Follow);
      UpdateViewport(kDoNotChangeZoom);
    }
    else if (m_mode == location::NotFollowNoPosition)
    {
      ChangeMode(location::PendingPosition);
    }
  }
}

void MyPositionController::OnEnterBackground() {}

void MyPositionController::OnCompassTapped()
{
  if (m_mode == location::FollowAndRotate)
  {
    ChangeMode(location::Follow);
    ChangeModelView(m_position, 0.0, m_visiblePixelRect.Center(), kDoNotChangeZoom);
  }
  else
  {
    ChangeModelView(0.0);
  }
}

void MyPositionController::ChangeModelView(m2::PointD const & center, int zoomLevel)
{
  if (m_listener)
    m_listener->ChangeModelView(center, zoomLevel, m_animCreator);
  m_animCreator = nullptr;
}

void MyPositionController::ChangeModelView(double azimuth)
{
  if (m_listener)
    m_listener->ChangeModelView(azimuth, m_animCreator);
  m_animCreator = nullptr;
}

void MyPositionController::ChangeModelView(m2::RectD const & rect)
{
  if (m_listener)
    m_listener->ChangeModelView(rect, m_animCreator);
  m_animCreator = nullptr;
}

void MyPositionController::ChangeModelView(m2::PointD const & userPos, double azimuth, m2::PointD const & pxZero,
                                           int zoomLevel, Animation::TAction const & onFinishAction)
{
  if (m_listener)
    m_listener->ChangeModelView(userPos, azimuth, pxZero, zoomLevel, onFinishAction, m_animCreator);
  m_animCreator = nullptr;
}

void MyPositionController::ChangeModelView(double autoScale, m2::PointD const & userPos, double azimuth,
                                           m2::PointD const & pxZero)
{
  if (m_listener)
    m_listener->ChangeModelView(autoScale, userPos, azimuth, pxZero, m_animCreator);
  m_animCreator = nullptr;
}

void MyPositionController::UpdateViewport(int zoomLevel)
{
  if (IsWaitingForLocation())
    return;

  if (m_mode == location::Follow)
  {
    ChangeModelView(m_position, zoomLevel);
  }
  else if (m_mode == location::FollowAndRotate)
  {
    ChangeModelView(m_position, m_drawDirection,
                    IsNavigationStyleCameraActive() ? GetRoutingRotationPixelCenter() : m_visiblePixelRect.Center(),
                    zoomLevel);
  }
}

m2::PointD MyPositionController::GetRotationPixelCenter() const
{
  if (m_mode == location::Follow)
    return m_visiblePixelRect.Center();

  if (m_mode == location::FollowAndRotate)
    return IsNavigationStyleCameraActive() ? GetRoutingRotationPixelCenter() : m_visiblePixelRect.Center();

  return m2::PointD::Zero();
}

m2::PointD MyPositionController::GetRoutingRotationPixelCenter() const
{
  return {m_visiblePixelRect.Center().x, m_visiblePixelRect.maxY() - m_positionRoutingOffsetY};
}

void MyPositionController::UpdateRoutingOffsetY(bool useDefault, int offsetY)
{
  double const vs = GetVisualScale();
  m_positionRoutingOffsetY = useDefault ? kPositionRoutingOffsetY * vs : offsetY + Arrow3d::GetMaxBottomSize() * vs;
}

m2::PointD MyPositionController::GetDrawablePosition()
{
  m2::PointD position;
  if (AnimationSystem::Instance().GetArrowPosition(position))
  {
    m_isPendingAnimation = false;
    return position;
  }

  if (m_isPendingAnimation)
    return m_oldPosition;

  return m_position;
}

double MyPositionController::GetDrawableAzimut()
{
  double angle;
  if (AnimationSystem::Instance().GetArrowAngle(angle))
  {
    m_isPendingAnimation = false;
    return angle;
  }

  if (m_isPendingAnimation)
    return m_oldDrawDirection;

  return m_drawDirection;
}

void MyPositionController::CreateAnim(m2::PointD const & oldPos, double oldAzimut, ScreenBase const & screen)
{
  double const moveDuration = PositionInterpolator::GetMoveDuration(oldPos, m_position, screen);
  double const rotateDuration = AngleInterpolator::GetRotateDuration(oldAzimut, m_drawDirection);
  if (df::IsAnimationAllowed(std::max(moveDuration, rotateDuration), screen))
  {
    if (IsModeChangeViewport())
    {
      m_animCreator = [this, moveDuration](ref_ptr<Animation> syncAnim) -> drape_ptr<Animation>
      {
        drape_ptr<Animation> anim = make_unique_dp<ArrowAnimation>(
            GetDrawablePosition(), m_position, syncAnim == nullptr ? moveDuration : syncAnim->GetDuration(),
            GetDrawableAzimut(), m_drawDirection);
        if (syncAnim != nullptr)
        {
          anim->SetMaxDuration(syncAnim->GetMaxDuration());
          anim->SetMinDuration(syncAnim->GetMinDuration());
        }
        return anim;
      };
      m_oldPosition = oldPos;
      m_oldDrawDirection = oldAzimut;
      m_isPendingAnimation = true;
    }
    else
    {
      AnimationSystem::Instance().CombineAnimation(
          make_unique_dp<ArrowAnimation>(oldPos, m_position, moveDuration, oldAzimut, m_drawDirection));
    }
  }
}

void MyPositionController::EnablePerspectiveInRouting(bool enablePerspective)
{
  m_enablePerspectiveInRouting = enablePerspective;
}

void MyPositionController::EnableAutoZoomInRouting(bool enableAutoZoom)
{
  m_enableAutoZoomInRouting = enableAutoZoom;
  ResetBlockAutoZoomTimer();
}

void MyPositionController::SetDrivingView(bool enabled, bool autoReturn, bool recenter)
{
  bool const wasDrivingView = m_isDrivingView;
  if (enabled && !wasDrivingView)
  {
    m_preDrivingViewDesiredInitMode = m_desiredInitMode;
    m_hasPreDrivingViewDesiredInitMode = true;
  }
  else if (!enabled && wasDrivingView && m_hasPreDrivingViewDesiredInitMode)
  {
    m_desiredInitMode = m_preDrivingViewDesiredInitMode;
    m_hasPreDrivingViewDesiredInitMode = false;
  }

  m_isDrivingView = enabled;
  m_autoReturnDrivingView = autoReturn;

  if (m_isInRouting)
    return;

  ResetNotification(m_routingNotFollowNotifyId);
  ResetNotification(m_blockAutoZoomNotifyId);
  m_needBlockAutoZoom = false;

  if (!enabled)
  {
    if (m_hasLastHeldDrivingPosition)
    {
      m_position = m_lastHeldDrivingPosition;
      m_hasLastHeldDrivingPosition = false;
      if (m_listener != nullptr)
        m_listener->PositionChanged(Position(), IsModeHasPosition());
    }
    if (recenter && m_isPositionAssigned)
    {
      ChangeMode(location::FollowAndRotate);
      ChangeModelView(m_position, m_drawDirection, m_visiblePixelRect.Center(), kDoNotChangeZoom);
    }
    return;
  }

  m_desiredInitMode = location::FollowAndRotate;
  if (!m_isPositionAssigned)
  {
    if (m_mode == location::NotFollowNoPosition)
      ChangeMode(location::PendingPosition);
    return;
  }

  if (recenter || m_mode != location::FollowAndRotate)
  {
    ChangeMode(location::FollowAndRotate);
    ChangeModelView(m_position, m_isDirectionAssigned ? m_drawDirection : 0.0, GetRoutingRotationPixelCenter(),
                    kDoNotChangeZoom);
  }
  else
  {
    UpdateViewport(kDoNotChangeZoom);
  }
  ResetRoutingNotFollowTimer();
  ResetBlockAutoZoomTimer();
}

void MyPositionController::ActivateRouting(int zoomLevel, bool enableAutoZoom, bool isArrowGlued)
{
  if (!m_isInRouting)
  {
    if (m_hasLastHeldDrivingPosition)
    {
      m_position = m_lastHeldDrivingPosition;
      m_hasLastHeldDrivingPosition = false;
      if (m_listener != nullptr)
        m_listener->PositionChanged(Position(), IsModeHasPosition());
    }

    m_isInRouting = true;
    m_isArrowGluedInRouting = isArrowGlued;
    m_enableAutoZoomInRouting = enableAutoZoom;

    ChangeMode(location::FollowAndRotate);
    ChangeModelView(m_position, m_isDirectionAssigned ? m_drawDirection : 0.0, GetRoutingRotationPixelCenter(),
                    zoomLevel, [this](ref_ptr<Animation> anim) { UpdateViewport(kDoNotChangeZoom); });
    ResetRoutingNotFollowTimer();
  }
}

void MyPositionController::DeactivateRouting()
{
  if (!m_isInRouting)
    return;

  m_isInRouting = false;
  m_isArrowGluedInRouting = false;

  if (m_isDrivingView)
  {
    ChangeMode(location::FollowAndRotate);
    ChangeModelView(m_position, m_isDirectionAssigned ? m_drawDirection : 0.0, GetRoutingRotationPixelCenter(),
                    kDoNotChangeZoom);
    ResetRoutingNotFollowTimer();
    ResetBlockAutoZoomTimer();
    return;
  }

  m_isDirectionAssigned = m_isCompassAvailable && m_isDirectionAssigned;
  ChangeMode(location::Follow);
  ChangeModelView(m_position, 0.0, m_visiblePixelRect.Center(), kDoNotChangeZoom);
}

#define CHECK_ON_TIMEOUT(id, timeout, checkFunction)                                                               \
  if (id == DrapeNotifier::kInvalidId)                                                                             \
  {                                                                                                                \
    id = m_notifier->Notify(ThreadsCommutator::RenderThread, std::chrono::seconds(static_cast<uint32_t>(timeout)), \
                            false /* repeating */, [this](uint64_t notifyId)                                       \
    {                                                                                                              \
      if (notifyId != id)                                                                                          \
        return;                                                                                                    \
      checkFunction();                                                                                             \
      id = DrapeNotifier::kInvalidId;                                                                              \
    });                                                                                                            \
  }

void MyPositionController::CheckNotFollowRouting()
{
  RefreshFreeDrivingSettings();
  if (!m_blockRoutingNotFollowTimer &&
      driving_policy::ShouldAutoReturn(m_isInRouting, m_isDrivingView, m_autoReturnDrivingView, m_isInCarFreeDriving,
                                       m_autoStartFollowAndRotate, m_hasLocationSpeed, m_locationSpeedMps) &&
      m_mode == location::NotFollow)
  {
    double const timeout = !m_isInRouting && !m_isDrivingView ? driving_policy::kFreeDrivingAutoReturnSeconds
                                                              : kMaxNotFollowRoutingTimeSec;
    CHECK_ON_TIMEOUT(m_routingNotFollowNotifyId, timeout, CheckNotFollowRouting);
    if (m_routingNotFollowTimer.ElapsedSeconds() >= timeout)
    {
      ChangeMode(location::FollowAndRotate);
      UpdateViewport(kDoNotChangeZoom);
    }
  }
}

void MyPositionController::CheckBlockAutoZoom()
{
  if (m_needBlockAutoZoom)
  {
    CHECK_ON_TIMEOUT(m_blockAutoZoomNotifyId, kMaxBlockAutoZoomTimeSec, CheckBlockAutoZoom);
    if (m_blockAutoZoomTimer.ElapsedSeconds() >= kMaxBlockAutoZoomTimeSec)
    {
      m_needBlockAutoZoom = false;
      m_isDirtyAutoZoom = true;
    }
  }
}

void MyPositionController::CheckUpdateLocation()
{
  if (!m_positionIsObsolete)
  {
    CHECK_ON_TIMEOUT(m_updateLocationNotifyId, kMaxUpdateLocationInvervalSec, CheckUpdateLocation);
    if (m_updateLocationTimer.ElapsedSeconds() >= kMaxUpdateLocationInvervalSec)
    {
      m_positionIsObsolete = true;
      m_autoScale2d = m_autoScale3d = kUnknownAutoZoom;
      m_hasLocationSpeed = false;
      m_locationSpeedMps = -1.0;
      if (!m_isInRouting && !m_isDrivingView)
        ResetRoutingNotFollowTimer();
    }
  }
}

#undef CHECK_ON_TIMEOUT
}  // namespace df
