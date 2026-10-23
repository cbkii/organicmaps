"""Execute the production scheduler with deterministic clock/native/platform boundaries."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


class AppearanceScheduleTests(unittest.TestCase):
    def test_production_controller(self):
        java = shutil.which('java')
        if not java:
            self.skipTest('Java 17 required')
        stubs = {
            'android/location/Location.java': '''package android.location; public class Location {
                public double getLatitude(){return -35;} public double getLongitude(){return 149;} }''',
            'androidx/annotation/NonNull.java': 'package androidx.annotation; public @interface NonNull {}',
            'androidx/annotation/UiThread.java': 'package androidx.annotation; public @interface UiThread {}',
            'app/organicmaps/sdk/util/Config.java': '''package app.organicmaps.sdk.util;
                public class Config { public enum UiTheme {LIGHT,DARK,SYSTEM,SCHEDULED} }''',
            'app/organicmaps/sdk/location/LocationListener.java': '''package app.organicmaps.sdk.location;
                public interface LocationListener { void onLocationUpdated(android.location.Location location); }''',
            'app/organicmaps/sdk/location/LocationHelper.java': '''package app.organicmaps.sdk.location;
                public class LocationHelper { public android.location.Location location; public LocationListener listener;
                public android.location.Location getSavedLocation(){return location;}
                public void addListener(LocationListener l){if(listener!=null)throw new AssertionError();listener=l;}
                public void removeListener(LocationListener l){if(listener==l)listener=null;} }''',
            'app/organicmaps/sdk/Framework.java': '''package app.organicmaps.sdk;
                public class Framework { public @interface DayTimeType {} public static final int
                DAY_TIME_TYPE_POLAR_DAY=2,DAY_TIME_TYPE_POLAR_NIGHT=3; public static int type;
                public static long sunrise,sunset,next,reference,lastReference;
                public static int nativeGetDayTimeType(long t,double a,double b){return type;}
                public static long nativeGetSunriseTime(long t,double a,double b){lastReference=t;return t>reference+43200?next:sunrise;}
                public static long nativeGetSunsetTime(long t,double a,double b){return sunset;} }''',
            'app/organicmaps/sdk/util/concurrency/UiThread.java': '''package app.organicmaps.sdk.util.concurrency;
                public class UiThread {public static Runnable task; public static long delay;
                public static void cancelDelayedTasks(Runnable r){if(task==r)task=null;}
                public static void run(Runnable r){r.run();}
                public static void runLater(Runnable r,long d){if(task!=null)throw new AssertionError("duplicate timer");task=r;delay=d;} }''',
            'app/organicmaps/util/Harness.java': '''package app.organicmaps.util;
                import java.time.*; import java.util.*; import app.organicmaps.sdk.*;
                import app.organicmaps.sdk.location.*; import app.organicmaps.sdk.util.*;
                import app.organicmaps.sdk.util.concurrency.UiThread;
                public class Harness {
                static void check(boolean b){if(!b)throw new AssertionError();}
                public static void main(String[] args){
                ZonedDateTime[] now={ZonedDateTime.parse("2026-10-20T06:59:00+11:00[Australia/Sydney]")};
                LocationHelper helper=new LocationHelper(); List<Config.UiTheme> themes=new ArrayList<>();
                var controller=new SynchronizedThemeController(helper,themes::add,()->now[0]);
                controller.synchronizeTimeDependentTheme();
                check(themes.get(themes.size()-1)==Config.UiTheme.DARK); check(UiThread.delay==60000); check(helper.listener!=null);
                now[0]=now[0].withHour(7).withMinute(0); UiThread.task.run();
                check(themes.get(themes.size()-1)==Config.UiTheme.LIGHT);
                now[0]=now[0].withHour(19); UiThread.task.run(); check(themes.get(themes.size()-1)==Config.UiTheme.DARK);
                helper.location=new android.location.Location(); now[0]=now[0].withHour(12);
                Framework.reference=now[0].toEpochSecond(); Framework.sunrise=now[0].withHour(6).toEpochSecond();
                Framework.sunset=now[0].withHour(18).toEpochSecond(); Framework.next=Framework.sunrise+86400;
                helper.listener.onLocationUpdated(helper.location); check(helper.listener==null);
                check(themes.get(themes.size()-1)==Config.UiTheme.LIGHT); check(UiThread.delay==900000);
                now[0]=now[0].withHour(17).withMinute(59).withSecond(59); UiThread.task.run(); check(UiThread.delay==1000);
                now[0]=now[0].plusSeconds(1); UiThread.task.run(); check(themes.get(themes.size()-1)==Config.UiTheme.DARK);
                now[0]=now[0].withHour(6); UiThread.task.run(); check(themes.get(themes.size()-1)==Config.UiTheme.LIGHT);
                check(Framework.lastReference==now[0].toLocalDate().atTime(12,0).toEpochSecond(ZoneOffset.UTC));
                now[0]=now[0].withHour(12); Framework.sunrise=0; UiThread.task.run();
                check(themes.get(themes.size()-1)==Config.UiTheme.LIGHT); check(UiThread.delay==60000);
                Framework.sunrise=Framework.sunset+1; UiThread.task.run(); check(UiThread.delay==60000);
                Framework.sunrise=now[0].withHour(6).toEpochSecond(); Framework.next=0;
                now[0]=now[0].withHour(20); UiThread.task.run(); check(themes.get(themes.size()-1)==Config.UiTheme.DARK);
                check(UiThread.delay==60000);
                Framework.type=Framework.DAY_TIME_TYPE_POLAR_DAY; UiThread.task.run();
                check(themes.get(themes.size()-1)==Config.UiTheme.LIGHT); check(UiThread.delay==900000);
                Framework.type=Framework.DAY_TIME_TYPE_POLAR_NIGHT; UiThread.task.run();
                check(themes.get(themes.size()-1)==Config.UiTheme.DARK);
                Runnable stale=UiThread.task; controller.stop(); int count=themes.size(); stale.run();
                check(themes.size()==count); check(UiThread.task==null); check(helper.listener==null);
                Framework.type=0; helper.location=null; controller.synchronizeTimeDependentTheme();
                var listener=helper.listener; controller.stop(); count=themes.size(); listener.onLocationUpdated(new android.location.Location());
                check(themes.size()==count); check(UiThread.task==null); check(helper.listener==null);
                now[0]=ZonedDateTime.parse("2026-10-04T07:00:00+11:00[Australia/Sydney]");
                controller.synchronizeTimeDependentTheme(); check(themes.get(themes.size()-1)==Config.UiTheme.LIGHT);
                controller.stop(); System.out.println("PASS binary theme/fallback/polar/cancellation/DST boundaries");
                } }''',
        }
        with tempfile.TemporaryDirectory() as directory:
            tmp = pathlib.Path(directory)
            paths = []
            for name, content in stubs.items():
                target = tmp / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                paths.append(str(target))
            source = ROOT / 'android/app/src/main/java/app/organicmaps/util/SynchronizedThemeController.java'
            compiled = subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(tmp / 'classes'), *paths, str(source)], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            ran = subprocess.run([java, '-cp', str(tmp / 'classes'), 'app.organicmaps.util.Harness'], capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)

    def test_real_native_solar_date_at_canberra_dawn(self):
        import re
        compiler = shutil.which('c++')
        if not compiler:
            self.skipTest('C++ compiler required')
        def body(path):
            return re.sub(r'^#include[^\n]*\n|^#pragma once\n', '', (ROOT / path).read_text(), flags=re.M)
        source = r'''
#include <cassert>
#include <cmath>
#include <ctime>
#include <string>
#include <stdexcept>
#include <utility>
#define ASSERT_GREATER_OR_EQUAL(a,b,msg) assert((a)>=(b))
#define ASSERT_LESS_OR_EQUAL(a,b,msg) assert((a)<=(b))
#define ASSERT(a,msg) assert(a)
#define MYTHROW(type,msg) throw std::runtime_error("native date failure")
namespace math { constexpr double pi=3.14159265358979323846;
 double DegToRad(double v){return v*pi/180;} double RadToDeg(double v){return v*180/pi;} }
namespace base { time_t SecondsSinceEpochToTimeT(int s){return static_cast<time_t>(s);} }
''' + body('libs/base/timegm.hpp') + body('libs/base/timegm.cpp') + body('libs/base/sunrise_sunset.hpp') + body('libs/base/sunrise_sunset.cpp') + r'''
int main(){
 const double lat=-35.2809,lon=149.1300;
 const time_t now=base::TimeGM(2026,10,19,19,30,0); // 06:30 AEDT, 20 October.
 const time_t reference=base::TimeGM(2026,10,20,12,0,0);
 const auto dawn=GetSunriseTime(reference,lat,lon), dusk=GetSunsetTime(reference,lat,lon);
 assert(dawn<now && now<dusk); assert(GetDayTime(now,lat,lon)==DayTimeType::Day);
 // Passing the instant itself selects the preceding solar day's events.
 assert(GetSunsetTime(now,lat,lon)<now);
 assert(GetSunriseTime(now+86400,lat,lon)<=now);
 assert(GetDayTime(dawn-1,lat,lon)==DayTimeType::Night);
 assert(GetDayTime(dawn,lat,lon)==DayTimeType::Day);
}
'''
        with tempfile.TemporaryDirectory() as directory:
            tmp = pathlib.Path(directory)
            src = tmp / 'calendar.cpp'
            src.write_text(source)
            compiled = subprocess.run([compiler, '-std=c++17', str(src), '-o', str(tmp / 'calendar')], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            ran = subprocess.run([str(tmp / 'calendar')], capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)

    def test_location_status_has_no_automatic_floating_ui(self):
        source = (ROOT / 'android/app/src/main/java/app/organicmaps/location/InCarLocationUi.java').read_text()
        activity = (ROOT / 'android/app/src/main/java/app/organicmaps/MwmActivity.java').read_text()
        self.assertNotIn('Toast', source)
        self.assertNotIn('postDelayed', source)
        self.assertNotIn('InCarLocationUi.install', activity)
        self.assertIn('showDetails', source)


if __name__ == '__main__':
    unittest.main()
