package app.organicmaps.incar;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Independent release signer, verified from ts-theme PR24 signing run 38877410241. */
final class InCarCompactPeer
{
  static final String PACKAGE = "com.cbkii.ts18launcher";
  private static final byte[] CERT = hex("6b12337f70ec56313180a04e602aa0aa5cea3509dfa34d58c4eefb9f6202cda4");

  static boolean trusted(Context context, int uid)
  {
    if (Build.VERSION.SDK_INT < 28 || uid / 100000 != android.os.Process.myUid() / 100000)
      return false;
    try
    {
      final PackageManager pm = context.getPackageManager();
      final String[] packages = pm.getPackagesForUid(uid);
      final PackageInfo info = pm.getPackageInfo(PACKAGE, 0);
      return packages != null && packages.length == 1 && PACKAGE.equals(packages[0])
          && info.applicationInfo != null && info.applicationInfo.uid == uid
          && pm.hasSigningCertificate(PACKAGE, CERT, PackageManager.CERT_INPUT_SHA256);
    }
    catch (PackageManager.NameNotFoundException | RuntimeException ignored)
    {
      return false;
    }
  }

  static boolean ownsProvider(Context context, String authority) {
    android.content.pm.ProviderInfo provider = context.getPackageManager().resolveContentProvider(authority, 0);
    return provider != null && PACKAGE.equals(provider.packageName) && provider.applicationInfo != null
        && trusted(context, provider.applicationInfo.uid);
  }

  static byte[] hex(String text)
  {
    final byte[] bytes = new byte[text.length() / 2];
    for (int i = 0; i < bytes.length; ++i)
      bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
    return bytes;
  }
}
