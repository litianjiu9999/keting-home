package tv.keting.home;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

public final class TvIntents {
    private TvIntents() {}

    public static boolean openSystemSettings(Context context) {
        Intent[] intents = new Intent[] {
                component("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"),
                component("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.SettingsActivity"),
                component("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
                new Intent(Settings.ACTION_SETTINGS),
        };
        return startFirst(context, intents);
    }

    public static boolean openNetworkSettings(Context context) {
        Intent[] intents = new Intent[] {
                new Intent(Settings.ACTION_WIFI_SETTINGS),
                component("com.android.tv.settings", "com.android.tv.settings.connectivity.NetworkActivity"),
                new Intent(Settings.ACTION_SETTINGS),
        };
        return startFirst(context, intents);
    }

    public static boolean openStore(Context context) {
        String[] stores = new String[] {
                "com.xiaomi.mitv.appstore",
                "com.mitv.appstore",
                "com.xiaomi.market",
        };
        PackageManager manager = context.getPackageManager();
        for (String store : stores) {
            Intent launch = manager.getLeanbackLaunchIntentForPackage(store);
            if (launch == null) launch = manager.getLaunchIntentForPackage(store);
            if (launch != null && start(context, launch)) return true;
        }
        return false;
    }

    public static boolean openPackage(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        Intent launch = manager.getLeanbackLaunchIntentForPackage(packageName);
        if (launch == null) launch = manager.getLaunchIntentForPackage(packageName);
        return launch != null && start(context, launch);
    }

    private static Intent component(String pkg, String cls) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(pkg, cls));
        return intent;
    }

    private static boolean startFirst(Context context, Intent[] intents) {
        for (Intent intent : intents) {
            if (start(context, intent)) return true;
        }
        return false;
    }

    private static boolean start(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException exception) {
            return false;
        } catch (SecurityException exception) {
            return false;
        }
    }
}
