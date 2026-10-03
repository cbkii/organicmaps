#include "Framework.hpp"
#include "map/gps_tracker.hpp"

#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "app/organicmaps/sdk/platform/AndroidPlatform.hpp"

#include "drape_frontend/user_event_stream.hpp"

#include "geometry/mercator.hpp"

#include <chrono>
#include <cmath>

namespace
{
auto constexpr kStartupCameraBridgeLifetime = std::chrono::seconds(10);
auto constexpr kCurrentPositionLifetime = std::chrono::seconds(10);

struct StartupCameraBridgeState
{
  df::DrapeEngine * m_engine = nullptr;
  bool m_forceDrivingArea = false;
  std::chrono::steady_clock::time_point m_armedAt;
};

struct CurrentPositionState
{
  m2::PointD m_position = m2::PointD::Zero();
  std::chrono::steady_clock::time_point m_receivedAt;
  bool m_valid = false;
  bool m_recenterPending = false;
};

StartupCameraBridgeState g_startupCameraBridge;
CurrentPositionState g_currentPosition;

auto GetDrapeEngine()
{
  if (!g_framework || !g_framework->IsDrapeEngineCreated())
    return decltype(g_framework->NativeFramework()->GetDrapeEngine()){};
  return g_framework->NativeFramework()->GetDrapeEngine();
}

void ResetStartupCameraBridge()
{
  g_startupCameraBridge = {};
}

void CancelStartupCameraBridge()
{
  ResetStartupCameraBridge();
}

bool HasCurrentStartupCameraBridge(df::DrapeEngine * engine)
{
  if (engine == nullptr || g_startupCameraBridge.m_engine != engine)
    return false;
  return std::chrono::steady_clock::now() - g_startupCameraBridge.m_armedAt <= kStartupCameraBridgeLifetime;
}

bool IsValidProviderPosition(double lat, double lon)
{
  return std::isfinite(lat) && std::isfinite(lon) && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
}

bool HasFreshCurrentPosition()
{
  return g_currentPosition.m_valid &&
         std::chrono::steady_clock::now() - g_currentPosition.m_receivedAt <= kCurrentPositionLifetime;
}

bool RecenterToCurrentPosition()
{
  auto const drapeEngine = GetDrapeEngine();
  if (drapeEngine == nullptr || !HasFreshCurrentPosition())
    return false;

  // This is deliberately a camera-centre event, not a My Position mode transition. SetCenterEvent
  // preserves the current screen angle and never calls ChangeMyPositionModeMessage/NextMode().
  drapeEngine->SetModelViewCenter(g_currentPosition.m_position, df::kDoNotChangeZoom, true /* isAnim */,
                                  true /* trackVisibleViewport */);
  return true;
}

void ShowLocalArea(double lat, double lon, double radiusMeters)
{
  auto const drapeEngine = GetDrapeEngine();
  if (drapeEngine == nullptr || radiusMeters <= 0.0)
    return;

  if (g_framework->NativeFramework()->GetRoutingManager().IsRoutingActive())
    return;

  auto const rect = mercator::MetersToXY(lon, lat, radiusMeters);
  drapeEngine->SetModelViewRect(rect, false /* applyRotation */, df::kDoNotChangeZoom, false /* isAnim */,
                                true /* useVisibleViewport */);
}
}  // namespace

extern "C"
{
static void LocationStateModeChanged(location::EMyPositionMode mode, std::shared_ptr<jobject> const & listener)
{
  JNIEnv * env = jni::GetEnv();
  env->CallVoidMethod(*listener, jni::GetMethodID(env, *listener.get(), "onMyPositionModeChanged", "(I)V"),
                      static_cast<jint>(mode));
}

//  public static void nativeSwitchToNextMode();
JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSwitchToNextMode(JNIEnv * env, jclass clazz)
{
  g_framework->SwitchMyPositionNextMode();
}

// public static boolean nativeRecenterToCurrentPosition();
JNIEXPORT jboolean Java_app_organicmaps_sdk_location_LocationState_nativeRecenterToCurrentPosition(JNIEnv *, jclass)
{
  // A direct user recenter owns the camera over the bounded startup bridge. If no current fix is
  // available, remember only one request and satisfy it from the next valid provider observation.
  CancelStartupCameraBridge();
  if (RecenterToCurrentPosition())
  {
    g_currentPosition.m_recenterPending = false;
    return JNI_TRUE;
  }

  g_currentPosition.m_recenterPending = true;
  return JNI_FALSE;
}

// private static int nativeGetMode();
JNIEXPORT jint Java_app_organicmaps_sdk_location_LocationState_nativeGetMode(JNIEnv * env, jclass clazz)
{
  // GetMyPositionMode() is initialized only after drape creation.
  // https://github.com/organicmaps/organicmaps/issues/1128#issuecomment-1784435190
  ASSERT(g_framework && g_framework->IsDrapeEngineCreated(), ());
  return g_framework->GetMyPositionMode();
}

//  public static void nativeSetListener(ModeChangeListener listener);
JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSetListener(JNIEnv * env, jclass clazz,
                                                                                 jobject listener)
{
  g_framework->SetMyPositionModeListener(
      std::bind(&LocationStateModeChanged, std::placeholders::_1, jni::make_global_ref(listener)));
}

//  public static void nativeRemoveListener();
JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeRemoveListener(JNIEnv * env, jclass clazz)
{
  g_framework->SetMyPositionModeListener(location::TMyPositionModeChanged());
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeOnLocationError(JNIEnv * env, jclass clazz,
                                                                                     int errorCode)
{
  g_framework->OnLocationError(errorCode);
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeResetFreeDrivingSession(JNIEnv *, jclass)
{
  // Provider teardown can precede framework creation. This bridge must fail open.
  if (g_framework)
    g_framework->NativeFramework()->GetRoutingManager().ResetFreeDrivingLocationSession();
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeLocationUpdated(
    JNIEnv * env, jclass clazz, jlong time, jlong monotonicTimeNanos, jdouble lat, jdouble lon, jfloat accuracyH,
    jdouble altitude, jfloat accuracyV, jfloat speed, jfloat speedAccuracy, jfloat bearing, jfloat bearingAccuracy)
{
  location::GpsInfo info;
  info.m_source = location::EAndroidNative;

  info.m_timestamp = static_cast<double>(time) / 1000.0;
  if (monotonicTimeNanos > 0)
    info.m_monotonicTimestamp = static_cast<double>(monotonicTimeNanos) / 1.0e9;
  info.m_latitude = lat;
  info.m_longitude = lon;

  if (accuracyH > 0)
    info.m_horizontalAccuracy = accuracyH;

  if (accuracyV > 0)
  {
    info.m_altitude = altitude;
    info.m_verticalAccuracy = accuracyV;
  }

  if (bearing >= 0)
    info.m_bearing = bearing;
  if (bearingAccuracy >= 0)
    info.m_bearingAccuracy = bearingAccuracy;

  if (speed >= 0)
    info.m_speed = speed;
  if (speedAccuracy >= 0)
    info.m_speedAccuracy = speedAccuracy;

  auto const drapeEngine = GetDrapeEngine();
  bool hasPendingStartupCamera = drapeEngine != nullptr && HasCurrentStartupCameraBridge(drapeEngine.get());
  if (!hasPendingStartupCamera && g_startupCameraBridge.m_engine != nullptr)
  {
    CancelStartupCameraBridge();
    hasPendingStartupCamera = false;
  }

  // If launch happened before a live fix, frame the requested driving area immediately before the GPS message.
  // Both operations use the render thread's normal-priority queue, so follow-and-rotate inherits this sane scale.
  if (hasPendingStartupCamera && g_startupCameraBridge.m_forceDrivingArea)
    ShowLocalArea(lat, lon, 5000.0);

  g_framework->OnLocationUpdated(info);
  GpsTracker::Instance().OnLocationUpdated(info);

  if (IsValidProviderPosition(lat, lon))
  {
    g_currentPosition.m_position = mercator::FromLatLon(lat, lon);
    g_currentPosition.m_receivedAt = std::chrono::steady_clock::now();
    g_currentPosition.m_valid = true;
    if (g_currentPosition.m_recenterPending && RecenterToCurrentPosition())
      g_currentPosition.m_recenterPending = false;
  }

  if (hasPendingStartupCamera || g_startupCameraBridge.m_engine != nullptr)
    ResetStartupCameraBridge();
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSetDrivingViewEnabled(JNIEnv * env, jclass clazz,
                                                                                           jboolean enabled,
                                                                                           jboolean autoReturn,
                                                                                           jboolean recenter)
{
  // A recentering app-side Driving View action explicitly takes camera ownership from the bounded launcher bridge.
  // Cancel first so a stale bridge timeout/fix cannot later disable the newly selected manual/persistent state.
  if (recenter)
    CancelStartupCameraBridge();

  auto const drapeEngine = GetDrapeEngine();
  if (drapeEngine != nullptr)
    drapeEngine->SetDrivingView(enabled, autoReturn, recenter);
}

JNIEXPORT void Java_app_organicmaps_incar_InCarStartupCameraNative_nativeShowLocalArea(JNIEnv * env, jclass clazz,
                                                                                       jdouble lat, jdouble lon,
                                                                                       jdouble radiusMeters)
{
  ShowLocalArea(lat, lon, radiusMeters);
}

JNIEXPORT void Java_app_organicmaps_incar_InCarStartupCameraNative_nativeRequestFollowAndRotate(
    JNIEnv * env, jclass clazz, jboolean forceDrivingArea)
{
  auto const drapeEngine = GetDrapeEngine();
  if (drapeEngine == nullptr || g_framework->NativeFramework()->GetRoutingManager().IsRoutingActive())
  {
    CancelStartupCameraBridge();
    return;
  }

  CancelStartupCameraBridge();

  // The controller already enabled persistent Driving View. This bounded bridge only frames the
  // first live location if it arrived after the launch and before a useful pre-fix viewport existed.
  if (forceDrivingArea)
  {
    g_startupCameraBridge.m_engine = drapeEngine.get();
    g_startupCameraBridge.m_forceDrivingArea = forceDrivingArea;
    g_startupCameraBridge.m_armedAt = std::chrono::steady_clock::now();
  }
}

JNIEXPORT void Java_app_organicmaps_incar_InCarStartupCameraNative_nativeCancelPending(JNIEnv * env, jclass clazz)
{
  CancelStartupCameraBridge();
}
}  // extern "C"
