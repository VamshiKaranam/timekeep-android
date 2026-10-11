package io.github.vamshikaranam.timekeep;

import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

/** The two system switches the app depends on. */
final class Perms {
    private Perms() {
    }

    /** Usage access: lets the app read how long each app was used. */
    @SuppressWarnings("deprecation")
    static boolean usage(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Accessibility service: lets the app notice which app is on screen. */
    static boolean accessibility(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(c, WatchService.class);
        for (String part : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    static void openUsageSettings(Context c) {
        open(c, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    static void openAccessibilitySettings(Context c) {
        open(c, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    static void openAppInfo(Context c) {
        open(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + c.getPackageName())));
    }

    private static void open(Context c, Intent i) {
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(c, "Couldn't open that settings page. Look for it in the Settings app.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
