"""Execute production recovery/permission policy and provider code against deterministic Android doubles.

These tests prove registration/lifecycle decisions, not Android runtime or TS18 GNSS performance.
"""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


class LocationAcquisitionTests(unittest.TestCase):
    def test_production_policy_and_provider_boundaries(self):
        java = shutil.which('java')
        if not java:
            self.skipTest('Java unavailable: location policy/provider NOT_RUN')
        stubs = {
            'android/Manifest.java': '''package android; public class Manifest { public static class permission {
              public static final String ACCESS_COARSE_LOCATION="coarse", ACCESS_FINE_LOCATION="fine"; }}''',
            'android/app/PendingIntent.java': 'package android.app; public class PendingIntent {}',
            'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager {
              public static final int PERMISSION_GRANTED=0; }''',
            'android/content/Context.java': '''package android.content; public class Context {
              public static final String LOCATION_SERVICE="location";
              public android.location.LocationManager manager = new android.location.LocationManager();
              public boolean fine=true;
              public Object getSystemService(String name) { return manager; }}''',
            'android/os/Bundle.java': 'package android.os; public class Bundle {}',
            'android/os/Looper.java': '''package android.os; public class Looper {
              public static Looper getMainLooper() { return new Looper(); }}''',
            'android/os/SystemClock.java': '''package android.os; public class SystemClock {
              public static long elapsedRealtimeNanos() { return 200_000_000_000L; }}''',
            'android/location/Location.java': '''package android.location; public class Location {
              public long nanos=199_000_000_000L;
              public Location() {} public Location(Location other) { nanos=other.nanos; }
              public long getElapsedRealtimeNanos(){return nanos;} public double getLatitude(){return -35;}
              public double getLongitude(){return 149;} public float getAccuracy(){return 5;}
              public boolean hasAccuracy(){return true;} }''',
            'android/location/LocationManager.java': '''package android.location; import java.util.*;
              public class LocationManager {
              public static final String GPS_PROVIDER="gps", PASSIVE_PROVIDER="passive";
              public Set<String> failed=new HashSet<>(); public List<String> requested=new ArrayList<>();
              public List<String> enabled=new ArrayList<>(List.of("gps","network","passive"));
              public androidx.core.location.LocationListenerCompat listener;
              public int removes; public List<String> getProviders(boolean enabledOnly){return enabled;}
              public boolean isProviderEnabled(String p){return enabled.contains(p);}
              public Location getLastKnownLocation(String p){return new Location();} }''',
            'androidx/core/content/ContextCompat.java': '''package androidx.core.content; public class ContextCompat {
              public static int checkSelfPermission(android.content.Context c,String p){return c.fine?0:-1;} }''',
            'androidx/core/location/LocationListenerCompat.java': '''package androidx.core.location;
              public interface LocationListenerCompat {
              void onLocationChanged(android.location.Location l); void onProviderDisabled(String p);
              void onProviderEnabled(String p); void onStatusChanged(String p,int s,android.os.Bundle b); }''',
            'androidx/core/location/LocationRequestCompat.java': '''package androidx.core.location;
              public class LocationRequestCompat { public static int QUALITY_HIGH_ACCURACY=100;
              public static class Builder { public Builder(long i){} public Builder setQuality(int q){return this;}
              public LocationRequestCompat build(){return new LocationRequestCompat();} }}''',
            'androidx/core/location/LocationManagerCompat.java': '''package androidx.core.location;
              public class LocationManagerCompat {
              public static void requestLocationUpdates(android.location.LocationManager m,String p,
                LocationRequestCompat r,LocationListenerCompat l,android.os.Looper looper) {
                m.requested.add(p); if(m.failed.contains(p)) throw new SecurityException(); m.listener=l; }
              public static void removeUpdates(android.location.LocationManager m,LocationListenerCompat l){m.removes++;} }''',
            'app/organicmaps/sdk/util/concurrency/UiThread.java': '''package app.organicmaps.sdk.util.concurrency;
              public class UiThread { public static java.util.List<Runnable> queued=new java.util.ArrayList<>();
              public static void runLater(Runnable r) { queued.add(r); }}''',
            'app/organicmaps/sdk/util/log/Logger.java': '''package app.organicmaps.sdk.util.log;
              public class Logger {public static void d(Object... a){} public static void w(Object... a){} }''',
            'app/organicmaps/sdk/location/LocationUtils.java': '''package app.organicmaps.sdk.location;
              public class LocationUtils { public static boolean areLocationServicesTurnedOn(android.content.Context c){return true;} }''',
            'org/junit/Test.java': '''package org.junit; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
              public @interface Test {}''',
            'org/junit/Assert.java': '''package org.junit; public class Assert {
              public static void assertTrue(boolean b){if(!b)throw new AssertionError("expected true");}
              public static void assertFalse(boolean b){assertTrue(!b);}
              public static void assertEquals(Object a,Object b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
              public static void assertEquals(long a,long b){assertTrue(a==b);} }''',
            'Harness.java': '''import app.organicmaps.sdk.location.*; import android.content.*; import android.location.*;
              public class Harness {
              static class Events implements BaseLocationProvider.Listener {
                int fixes,disabled; public void onLocationChanged(Location l){fixes++;}
                public void onLocationDisabled(){disabled++;}
                public void onLocationResolutionRequired(android.app.PendingIntent p){}
                public void onFusedLocationUnsupported(){} }
              static void check(boolean value){if(!value)throw new AssertionError();}
              public static void main(String[] args) throws Exception {
                for(String name: new String[]{"app.organicmaps.sdk.location.LocationAcquisitionPolicyTest",
                    "app.organicmaps.location.InCarLocationPermissionPolicyTest"}) {
                  Class<?> cls=Class.forName(name); Object test=cls.getConstructor().newInstance();
                  for(var method:cls.getMethods()) if(method.isAnnotationPresent(org.junit.Test.class)) method.invoke(test);
                }
                Context context=new Context(); Events events=new Events();
                context.manager.failed.add("gps");
                AndroidNativeProvider provider=new AndroidNativeProvider(context,events,true);
                provider.start(200); check(provider.hasSubscriptions()); check(!provider.hasGpsSubscription());
                check(context.manager.requested.contains("network")); check(!context.manager.requested.contains("passive"));
                var old=context.manager.listener; provider.stop(); provider.stop();
                check(context.manager.removes==1); old.onLocationChanged(new Location()); check(events.fixes==0);
                context.manager.failed.clear(); provider.start(200); check(provider.hasGpsSubscription());
                old.onProviderDisabled("gps"); check(provider.hasGpsSubscription());
                context.manager.listener.onLocationChanged(new Location()); check(events.fixes==1);
                check(provider.getRecentCachedLocation()!=null);
                provider.stop(); context.fine=false; context.manager.requested.clear(); provider.start(3000);
                check(!context.manager.requested.contains("gps")); check(provider.hasSubscriptions());
                provider.stop(); context.manager.failed.add("network"); provider.start(3000);
                check(!provider.hasSubscriptions()); check(events.disabled==0);
                provider.stop(); context.fine=true; context.manager.failed.clear(); provider.start(200);
                check(provider.hasGpsSubscription());
                System.out.println("Production policy tests and partial/failed/coarse/stale-callback provider tests passed");
              }}''',
        }
        stubs.update({
            'android/annotation/SuppressLint.java': 'package android.annotation; public @interface SuppressLint {String[] value();}',
            'android/os/SystemClock.java': """package android.os; public class SystemClock {
              public static long now=200_000; public static long elapsedRealtime(){return now;}
              public static long elapsedRealtimeNanos(){return now*1_000_000;} }""",
            'android/os/Handler.java': """package android.os; import java.util.*; public class Handler {
              public record Task(Runnable action,long time){} public static List<Task> tasks=Collections.synchronizedList(new ArrayList<>());
              public static java.util.concurrent.CountDownLatch posted=new java.util.concurrent.CountDownLatch(1);
              public Handler(Looper l){} public boolean post(Runnable r){boolean ok=postDelayed(r,0);posted.countDown();return ok;}
              public void removeCallbacks(Runnable r){tasks.removeIf(t->t.action()==r);}
              public boolean postDelayed(Runnable r,long delay){tasks.add(new Task(r,SystemClock.now+delay));return true;}
              public static void advance(long ms){long end=SystemClock.now+ms; while(true){
                Task t=tasks.stream().min(Comparator.comparingLong(Task::time)).orElse(null);
                if(t==null||t.time()>end)break; tasks.remove(t);SystemClock.now=t.time();t.action().run();
              }SystemClock.now=end;} }""",
            'app/organicmaps/sdk/Framework.java': 'package app.organicmaps.sdk; public class Framework {public static void nativeRunFirstLaunchAnimation(){}}',
            'app/organicmaps/sdk/Map.java': 'package app.organicmaps.sdk; public class Map {public static boolean isEngineCreated(){return true;}}',
            'app/organicmaps/sdk/bookmarks/data/MapObject.java': """package app.organicmaps.sdk.bookmarks.data;
              public class MapObject {public static int MY_POSITION=1;
              public static MapObject createMapObject(int t,String a,String b,double lat,double lon){return new MapObject();}}""",
            'app/organicmaps/sdk/routing/JunctionInfo.java': 'package app.organicmaps.sdk.routing; public class JunctionInfo {}',
            'app/organicmaps/sdk/routing/RoutingController.java': """package app.organicmaps.sdk.routing;
              public class RoutingController {public static RoutingController get(){return new RoutingController();}
              public boolean isNavigating(){return false;}}""",
            'app/organicmaps/sdk/util/Config.java': """package app.organicmaps.sdk.util; public class Config {
              public static long timestamp; public static long getAgpsTimestamp(){return timestamp;}
              public static void setAgpsTimestamp(long t){timestamp=t;}}""",
            'app/organicmaps/sdk/util/NetworkPolicy.java': """package app.organicmaps.sdk.util;
              public class NetworkPolicy {public static boolean online=false; public static boolean getCurrentNetworkUsageStatus(){return online;}}""",
            'app/organicmaps/sdk/location/SensorHelper.java': """package app.organicmaps.sdk.location;
              public class SensorHelper {public void start(){} public void stop(){}}""",
            'app/organicmaps/sdk/location/LocationProviderFactory.java': """package app.organicmaps.sdk.location;
              public class LocationProviderFactory {
              public BaseLocationProvider getProvider(android.content.Context c,BaseLocationProvider.Listener l){return new AndroidNativeProvider(c,l);}
              public boolean isGmsLocationProviderAvailable(android.content.Context c){return false;} }""",
            'app/organicmaps/sdk/location/TrackRecorder.java': """package app.organicmaps.sdk.location;
              public class TrackRecorder {public static boolean nativeIsTrackRecordingEnabled(){return false;}}""",
            'app/organicmaps/sdk/location/LocationState.java': """package app.organicmaps.sdk.location;
              public class LocationState {public static final String LOCATION_TAG="Location";
              public static final int NOT_FOLLOW_NO_POSITION=0,NOT_FOLLOW=1,FOLLOW=2,FOLLOW_AND_ROTATE=3,PENDING_POSITION=4;
              public static int ERROR_GPS_OFF=1,ERROR_DENIED=2; public static int nativeFixes,resets;
              public static int getMode(){return FOLLOW;} public static void nativeResetFreeDrivingSession(){resets++;}
              public static void nativeOnLocationError(int e){} public static void nativeLocationUpdated(double... args){nativeFixes++;}}""",
            'app/organicmaps/sdk/location/LocationCompatExtractor.java': """package app.organicmaps.sdk.location;
              public class LocationCompatExtractor {
              public record Altitude(double altitude,float accuracy){} public record Speed(float speed,float accuracy){}
              public record Bearing(float bearing,float accuracy){}
              public static Altitude getAltitude(android.location.Location l){return null;}
              public static Speed getSpeed(android.location.Location l){return null;}
              public static Bearing getBearing(android.location.Location l){return null;} }""",
            'app/organicmaps/sdk/location/RouteSimulationProvider.java': """package app.organicmaps.sdk.location;
              public class RouteSimulationProvider extends BaseLocationProvider {
              public RouteSimulationProvider(android.content.Context c,Listener l,app.organicmaps.sdk.routing.JunctionInfo[] p){super(l);}
              public void start(long i){} public void stop(){} }""",
            'androidx/core/location/GnssStatusCompat.java': """package androidx.core.location;
              public class GnssStatusCompat { public int getSatelliteCount(){return 0;} public boolean usedInFix(int i){return false;}
              public static class Callback { public void onStarted(){} public void onStopped(){}
              public void onFirstFix(int t){} public void onSatelliteStatusChanged(GnssStatusCompat s){} }}""",
            'org/chromium/base/ObserverList.java': """package org.chromium.base; import java.util.*;
              public class ObserverList<T> {private List<T> values=new ArrayList<>();
              public void addObserver(T v){values.add(v);} public void removeObserver(T v){values.remove(v);}
              public int size(){return values.size();} public RewindableIterator<T> rewindableIterator(){return new RewindableIterator<>(values);}
              public static class RewindableIterator<T> { private List<T> list; private int index;
              public RewindableIterator(List<T> l){list=l;}public void rewind(){index=0;}
              public boolean hasNext(){return index<list.size();}public T next(){return list.get(index++);} }}""",
        })
        stubs['android/location/Location.java'] = stubs['android/location/Location.java'].replace(
            'public long getElapsedRealtimeNanos()', 'public long getTime(){return 1;} public long getElapsedRealtimeNanos()')
        stubs['android/location/LocationManager.java'] = stubs['android/location/LocationManager.java'].replace(
            'public int removes;', 'public boolean acceptsExtra=true; public int extraCalls; public boolean sendExtraCommand(String p,String c,android.os.Bundle b){extraCalls++;return acceptsExtra;} public int removes;')
        stubs['androidx/core/content/ContextCompat.java'] = stubs['androidx/core/content/ContextCompat.java'].replace(
            'public class ContextCompat {', 'public class ContextCompat {public static java.util.concurrent.Executor getMainExecutor(android.content.Context c){return Runnable::run;}')
        stubs['androidx/core/location/LocationManagerCompat.java'] = stubs['androidx/core/location/LocationManagerCompat.java'].replace(
            'public class LocationManagerCompat {', """public class LocationManagerCompat {
              public static GnssStatusCompat.Callback gnss;
              public static boolean registerGnssStatusCallback(android.location.LocationManager m,java.util.concurrent.Executor e,GnssStatusCompat.Callback c){gnss=c;return true;}
              public static void unregisterGnssStatusCallback(android.location.LocationManager m,GnssStatusCompat.Callback c){gnss=null;}""")
        stubs['app/organicmaps/sdk/util/log/Logger.java'] = stubs['app/organicmaps/sdk/util/log/Logger.java'].replace(
            'public class Logger {', 'public class Logger {public static void i(Object... a){}')
        stubs['app/organicmaps/sdk/location/LocationUtils.java'] = """package app.organicmaps.sdk.location;
              public class LocationUtils {
              public static boolean permission=true, enabled=true;
              public static boolean areLocationServicesTurnedOn(android.content.Context c){return enabled;}
              public static boolean checkFineLocationPermission(android.content.Context c){return c.fine&&permission;}
              public static boolean checkLocationPermission(android.content.Context c){return permission;}
              public static boolean isFromGpsProvider(android.location.Location l){return true;}
              public static boolean isAccuracySatisfied(android.location.Location l){return true;}
              public static boolean isLocationBetterThanLast(android.location.Location n,android.location.Location o){return true;} }"""
        stubs['Harness.java'] = stubs['Harness.java'].replace('System.out.println(', """
                provider.stop(); android.os.Handler.tasks.clear();
                Context hc=new Context(); hc.manager.failed.addAll(java.util.List.of("gps","network"));
                LocationHelper helper=new LocationHelper(hc,new SensorHelper(),true);
                helper.start(); check(!helper.isActive()); check(helper.isAcquisitionRequested());
                check(helper.getAcquisitionState()==LocationHelper.AcquisitionState.RECOVERING);
                hc.manager.failed.clear(); android.os.Handler.advance(30_000);
                check(helper.isActive()); check(hc.manager.removes==1);
                int registered=hc.manager.requested.size();
                android.os.Handler.advance(1_200_000); check(registered==hc.manager.requested.size());
                int nativeBefore=LocationState.nativeFixes;
                Location stale=new Location(); stale.nanos=android.os.SystemClock.elapsedRealtimeNanos()-31_000_000_000L;
                helper.onLocationChanged(stale); check(LocationState.nativeFixes==nativeBefore);
                Location live=new Location(); live.nanos=android.os.SystemClock.elapsedRealtimeNanos();
                helper.onLocationChanged(live); check(LocationState.nativeFixes==nativeBefore+1);
                helper.onLocationChanged(live); check(LocationState.nativeFixes==nativeBefore+1);
                check(helper.getAcquisitionState()==LocationHelper.AcquisitionState.FIX_RECEIVED);
                int resets=LocationState.resets; check(helper.retryAcquisition()); check(LocationState.resets==resets);
                check(!helper.retryAcquisition()); check(helper.getSavedLocation()!=null);
                android.os.Handler.advance(31_000); check(helper.getAcquisitionState()==LocationHelper.AcquisitionState.FIX_STALE);
                check(helper.getMyPosition()==null);
                helper.stop(); check(!helper.isAcquisitionRequested()); check(!helper.retryAcquisition());
                registered=hc.manager.requested.size(); android.os.Handler.advance(600_000);
                check(registered==hc.manager.requested.size());
                helper.start(); LocationUtils.permission=false; android.os.Handler.advance(5_000);
                check(!helper.isAcquisitionRequested()); LocationUtils.permission=true;
                helper.start(); LocationUtils.enabled=false; android.os.Handler.advance(5_000);
                check(!helper.isAcquisitionRequested()); LocationUtils.enabled=true;
                helper.start(); helper.onEnteredIntoFirstRun(); nativeBefore=LocationState.nativeFixes;
                live=new Location(); live.nanos=android.os.SystemClock.elapsedRealtimeNanos();
                helper.onLocationChanged(live); check(LocationState.nativeFixes==nativeBefore);
                android.os.Handler.advance(31_000); helper.onExitFromFirstRun();
                check(LocationState.nativeFixes==nativeBefore); helper.stop();
                app.organicmaps.sdk.util.NetworkPolicy.online=true;
                hc.manager.acceptsExtra=false;
                android.os.Handler.posted=new java.util.concurrent.CountDownLatch(1);
                helper.start(); check(android.os.Handler.posted.await(2,java.util.concurrent.TimeUnit.SECONDS));
                android.os.Handler.advance(0); check(app.organicmaps.sdk.util.Config.timestamp==0);
                int extras=hc.manager.extraCalls; android.os.Handler.advance(119_999); check(extras==hc.manager.extraCalls);
                helper.stop(); android.os.Handler.advance(1); hc.manager.acceptsExtra=true;
                android.os.Handler.posted=new java.util.concurrent.CountDownLatch(1);
                helper.start(); check(android.os.Handler.posted.await(2,java.util.concurrent.TimeUnit.SECONDS));
                android.os.Handler.advance(0); check(app.organicmaps.sdk.util.Config.timestamp>0); helper.stop();
                app.organicmaps.sdk.util.NetworkPolicy.online=false;
                System.out.println(""")
        for annotation in ('NonNull', 'Nullable', 'UiThread'):
            stubs[f'androidx/annotation/{annotation}.java'] = f'package androidx.annotation; public @interface {annotation} {{}}'
        stubs['androidx/annotation/RequiresPermission.java'] = '''package androidx.annotation;
            public @interface RequiresPermission { String[] anyOf() default {}; }'''
        sources = [
            'android/sdk/location/core/src/main/java/app/organicmaps/sdk/location/BaseLocationProvider.java',
            'android/sdk/src/main/java/app/organicmaps/sdk/location/AndroidNativeProvider.java',
            'android/sdk/src/main/java/app/organicmaps/sdk/location/LocationHelper.java',
            'android/sdk/src/main/java/app/organicmaps/sdk/location/LocationListener.java',
            'android/sdk/src/main/java/app/organicmaps/sdk/location/LocationAcquisitionPolicy.java',
            'android/sdk/src/test/java/app/organicmaps/sdk/location/LocationAcquisitionPolicyTest.java',
            'android/app/src/main/java/app/organicmaps/location/InCarLocationPermissionPolicy.java',
            'android/app/src/test/java/app/organicmaps/location/InCarLocationPermissionPolicyTest.java',
        ]
        with tempfile.TemporaryDirectory() as directory:
            tmp = pathlib.Path(directory)
            paths = []
            for name, content in stubs.items():
                target = tmp / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                paths.append(str(target))
            command = [java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(tmp / 'classes')]
            compiled = subprocess.run(command + paths + [str(ROOT / p) for p in sources], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            ran = subprocess.run([java, '-cp', str(tmp / 'classes'), 'Harness'], capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)


if __name__ == '__main__':
    unittest.main()
