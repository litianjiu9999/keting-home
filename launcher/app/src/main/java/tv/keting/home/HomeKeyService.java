package tv.keting.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** 旧桌面被系统写死为主页时，窗口一切过去就再拉回这里。这台固件会拦住这次启动。 */
public class HomeKeyService extends AccessibilityService {
    private static final String[] STOCK_HOMES = {
            "com.mitv.tvhome",
            "com.mitv.tvhome.atv",
            "com.xiaomi.mitv.tvhome",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence packageName = event.getPackageName();
        if (packageName == null || !isStockHome(packageName.toString())) return;
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // 系统拒绝后台拉起时，主页键仍会留在旧桌面。
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return super.onKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                startActivity(intent);
            } catch (RuntimeException ignored) {
                // 同上。
            }
        }
        return true;
    }

    private boolean isStockHome(String packageName) {
        for (String candidate : STOCK_HOMES) {
            if (candidate.equals(packageName)) return true;
        }
        return false;
    }
}
