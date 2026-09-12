#include "app/organicmaps/sdk/Framework.hpp"

#include "app/organicmaps/sdk/platform/AndroidPlatform.hpp"

#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "routing/free_driving_area_context.hpp"

namespace
{
void ConfigureInCarFreeDrivingRoadSnap()
{
  if (!g_framework || !android::Platform::Instance().IsInCar())
    return;

  auto & routingManager = g_framework->NativeFramework()->GetRoutingManager();
  auto & routingSession = routingManager.RoutingSession();
  auto const * dataSource = &g_framework->NativeFramework()->GetDataSource();

  routingSession.SetFreeDrivingAreaContextProvider([dataSource](m2::PointD const & point)
  { return routing::free_driving_snap::ReadAreaContext(*dataSource, point); });
  routingSession.SetFreeDrivingRoadSnapEnabled(true);
}
}  // namespace

extern "C"
{
// static void nativeSetSettingsDir(String settingsPath);
JNIEXPORT void Java_app_organicmaps_sdk_OrganicMaps_nativeSetSettingsDir(JNIEnv * env, jclass clazz,
                                                                         jstring settingsPath)
{
  android::Platform::Instance().SetSettingsDir(jni::ToNativeString(env, settingsPath));
}

// static void nativeInitPlatform(Context context, String apkPath, String storagePath, String privatePath,
// tmpPath, String flavorName, String buildType, boolean isTablet);
JNIEXPORT void Java_app_organicmaps_sdk_OrganicMaps_nativeInitPlatform(JNIEnv * env, jclass clazz, jobject context,
                                                                       jstring apkPath, jstring writablePath,
                                                                       jstring privatePath, jstring tmpPath,
                                                                       jstring flavorName, jstring buildType,
                                                                       jboolean isTablet)
{
  android::Platform::Instance().Initialize(env, context, apkPath, writablePath, privatePath, tmpPath, flavorName,
                                           buildType, isTablet);
}

// static void nativeInitFramework(@NonNull Runnable onComplete);
JNIEXPORT void Java_app_organicmaps_sdk_OrganicMaps_nativeInitFramework(JNIEnv * env, jclass clazz, jobject onComplete)
{
  if (!g_framework)
  {
    g_framework.Assign(new android::Framework([onComplete = jni::make_global_ref_safe(onComplete)]()
    {
      JNIEnv * env = jni::GetEnv();
      jmethodID const methodId = jni::GetMethodID(env, *onComplete, "run", "()V");
      env->CallVoidMethod(*onComplete, methodId);
    }));
    ConfigureInCarFreeDrivingRoadSnap();
  }
}

// static void nativeAddLocalization(String name, String value);
JNIEXPORT void Java_app_organicmaps_sdk_OrganicMaps_nativeAddLocalization(JNIEnv * env, jclass clazz, jstring name,
                                                                          jstring value)
{
  g_framework->AddString(jni::ToNativeString(env, name), jni::ToNativeString(env, value));
}

JNIEXPORT void Java_app_organicmaps_sdk_OrganicMaps_nativeOnTransit(JNIEnv *, jclass, jboolean foreground)
{
  if (static_cast<bool>(foreground))
    g_framework->NativeFramework()->EnterForeground();
  else
    g_framework->NativeFramework()->EnterBackground();
}
}
