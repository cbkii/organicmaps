"""Execute the production peer authenticator against controlled Android identities."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "android/app/src/main/java/app/organicmaps/incar/InCarCompactPeer.java"
PACKAGE = "app.organicmaps.incar"
CLASS = "InCarCompactPeer"

def has_compiler():
    try:
        result = subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-version"],
                                capture_output=True, text=True, timeout=10)
        return result.returncode == 0 and int(result.stdout.split()[1].split(".")[0]) >= 17
    except (OSError, ValueError, IndexError, subprocess.TimeoutExpired):
        return False

class CompactPeerTests(unittest.TestCase):
    @unittest.skipUnless(has_compiler(), "Java 17 compiler unavailable")
    def test_peer_requires_package_uid_user_and_release_certificate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = {
                "android/os/Build.java": "package android.os; public class Build {public static class VERSION {public static int SDK_INT=29;}}",
                "android/os/Process.java": "package android.os; public class Process {public static int myUid(){return 23000;}}",
                "android/content/Context.java": "package android.content; public class Context {public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}}",
                "android/content/pm/ProviderInfo.java": "package android.content.pm; public class ProviderInfo {public String packageName=PackageManager.providerPackage;public ApplicationInfo applicationInfo=new ApplicationInfo();}",
                "android/content/pm/PackageInfo.java": "package android.content.pm; public class PackageInfo {public ApplicationInfo applicationInfo=new ApplicationInfo();}",
                "android/content/pm/ApplicationInfo.java": "package android.content.pm; public class ApplicationInfo {public int uid=PackageManager.uid;}",
                "android/content/pm/PackageManager.java": """package android.content.pm; public class PackageManager {
public static class NameNotFoundException extends Exception {}
public static final int CERT_INPUT_SHA256=1;
public static int queriedUid; public static String providerPackage; public ProviderInfo resolveContentProvider(String a,int f){return new ProviderInfo();}
public static boolean cert=true,missing=false;public static int uid=24000;public static String[] owners;
public String[] getPackagesForUid(int u){queriedUid=u;return owners;}
public PackageInfo getPackageInfo(String p,int flags)throws NameNotFoundException {if(!p.equals("com.cbkii.ts18launcher")||flags!=0)throw new AssertionError("wrong package lookup");if(missing)throw new NameNotFoundException();return new PackageInfo();}
public boolean hasSigningCertificate(String p,byte[] c,int type){if(!p.equals("com.cbkii.ts18launcher")||c.length!=32||type!=1||!java.util.Arrays.equals(c,java.util.HexFormat.of().parseHex("6b12337f70ec56313180a04e602aa0aa5cea3509dfa34d58c4eefb9f6202cda4")))throw new AssertionError("wrong pinned certificate");return cert;}
}""",
                "Harness.java": """package app.organicmaps.incar;
import android.content.pm.PackageManager;
public class Harness {
static void check(boolean value){if(!value)throw new AssertionError();}
public static void main(String[] a){var c=new android.content.Context();
PackageManager.owners=new String[]{InCarCompactPeer.PACKAGE};check(InCarCompactPeer.trusted(c,24000));check(PackageManager.queriedUid==24000);
PackageManager.providerPackage=InCarCompactPeer.PACKAGE;check(InCarCompactPeer.ownsProvider(c,"authority"));
PackageManager.providerPackage="impostor";check(!InCarCompactPeer.ownsProvider(c,"authority"));
PackageManager.cert=false;check(!InCarCompactPeer.trusted(c,24000));PackageManager.cert=true;
check(!InCarCompactPeer.trusted(c,24001));check(PackageManager.queriedUid==24001);check(!InCarCompactPeer.trusted(c,124000));
PackageManager.owners=new String[]{InCarCompactPeer.PACKAGE,"untrusted.shared"};check(!InCarCompactPeer.trusted(c,24000));
PackageManager.owners=new String[]{"impostor"};check(!InCarCompactPeer.trusted(c,24000));
PackageManager.owners=new String[]{InCarCompactPeer.PACKAGE};PackageManager.missing=true;check(!InCarCompactPeer.trusted(c,24000));
PackageManager.missing=false;android.os.Build.VERSION.SDK_INT=27;check(!InCarCompactPeer.trusted(c,24000));
System.out.println("PASS production peer package/UID/user/certificate/shared-UID gates");}}
""",
            }
            files["InCarCompactPeer.java"] = SOURCE.read_text()
            for name, source in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(root),
                            *map(str, root.rglob("*.java"))], check=True, timeout=30)
            subprocess.run(["java", "-cp", str(root), PACKAGE + ".Harness"], check=True, timeout=10)


class CompactRendererTests(unittest.TestCase):
    @unittest.skipUnless(has_compiler(), "Java 17 compiler unavailable")
    def test_actual_state_tests_cover_resize_replacement_and_stale_callbacks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = {
                'InCarCompactStartupState.java': (ROOT / 'android/app/src/main/java/app/organicmaps/incar/InCarCompactStartupState.java').read_text(),
                'InCarCompactStartupStateTest.java': (ROOT / 'android/app/src/test/java/app/organicmaps/incar/InCarCompactStartupStateTest.java').read_text(),
                'org/junit/Test.java': 'package org.junit; public @interface Test {}',
                'org/junit/Assert.java': 'package org.junit; public class Assert {public static void assertTrue(boolean b){if(!b)throw new AssertionError();}public static void assertFalse(boolean b){assertTrue(!b);}}',
                'Harness.java': '''class Harness {public static void main(String[] a){var t=new app.organicmaps.incar.InCarCompactStartupStateTest();t.taskAndNativeEngineAloneDoNotProveUsableRendering();t.staleCallbacksCannotInvalidateOrReadyReplacement();System.out.println("PASS actual renderer resize/replacement/stale-callback tests");}}''',
            }
            for name, source in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root),
                            *map(str, root.rglob('*.java'))], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(root), 'Harness'], check=True, timeout=10)

if __name__ == "__main__":
    unittest.main()
