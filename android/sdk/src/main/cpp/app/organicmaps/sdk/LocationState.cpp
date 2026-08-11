#include "Framework.hpp"
#include "map/gps_tracker.hpp"

#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "app/organicmaps/sdk/platform/AndroidPlatform.hpp"

extern "C"
{
static void LocationStateModeChanged(location::EMyPositionMode mode, std::shared_ptr<jobject> const & listener)
{
  JNIEnv * env = jni::GetEnv();
  env->CallVoidMethod(*listener, jni::GetMethodID(env, *listener.get(), "onMyPositionModeChanged", "(I)V"),
                      static_cast<jint>(mode));
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSwitchToNextMode(JNIEnv * env, jclass clazz)
{
  g_framework->SwitchMyPositionNextMode();
}

JNIEXPORT jint Java_app_organicmaps_sdk_location_LocationState_nativeGetMode(JNIEnv * env, jclass clazz)
{
  ASSERT(g_framework && g_framework->IsDrapeEngineCreated(), ());
  return g_framework->GetMyPositionMode();
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSetListener(JNIEnv * env, jclass clazz,
                                                                                 jobject listener)
{
  g_framework->SetMyPositionModeListener(
      std::bind(&LocationStateModeChanged, std::placeholders::_1, jni::make_global_ref(listener)));
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeRemoveListener(JNIEnv * env, jclass clazz)
{
  g_framework->SetMyPositionModeListener(location::TMyPositionModeChanged());
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeOnLocationError(JNIEnv * env, jclass clazz,
                                                                                     int errorCode)
{
  g_framework->OnLocationError(errorCode);
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeLocationUpdated(JNIEnv * env, jclass clazz,
                                                                                     jlong time, jdouble lat,
                                                                                     jdouble lon, jfloat accuracyH,
                                                                                     jdouble altitude, jfloat accuracyV,
                                                                                     jfloat speed, jfloat bearing)
{
  location::GpsInfo info;
  info.m_source = location::EAndroidNative;

  info.m_timestamp = static_cast<double>(time) / 1000.0;
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

  if (speed >= 0)
    info.m_speed = speed;

  g_framework->OnLocationUpdated(info);
  GpsTracker::Instance().OnLocationUpdated(info);
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSetDrivingViewEnabled(
    JNIEnv * env, jclass clazz, jboolean enabled, jboolean autoReturn, jboolean recenter)
{
  if (g_framework == nullptr || !g_framework->IsDrapeEngineCreated())
    return;

  auto const drapeEngine = g_framework->NativeFramework()->GetDrapeEngine();
  if (drapeEngine != nullptr)
    drapeEngine->SetDrivingView(enabled, autoReturn, recenter);
}
}  // extern "C"
