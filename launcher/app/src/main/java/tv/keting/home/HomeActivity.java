package tv.keting.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends Activity {
    private static final String PREFS = "keting";
    private static final String KEY_MOTION = "motion";
    private static final String KEY_WALLPAPER = "wallpaper";
    private static final String KEY_WALLPAPER_AUTO = "wallpaper_auto";
    private static final String KEY_BOOT = "wallpaper_boot";
    private static final String KEY_PINS = "pins";
    private static final int MAX_PINS = 6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView wallpaperView;
    private TextView dateView;
    private TextView clockView;
    private TextView networkPill;
    private LinearLayout dock;
    private LinearLayout apps;
    private View settingsScrim;
    private View allAppsScrim;
    private LinearLayout allAppsGrid;
    private View allAppsButton;
    private TextView wallpaperMode;
    private TextView autoOn;
    private TextView autoOff;
    private LinearLayout wallpaperChoices;
    private LinearLayout pinList;
    private TextView motionOff;
    private TextView motionOn;
    private TextView homeStatus;
    private View firstDock;
    private boolean askedFocus;
    private int wallpaperToken;
    private final List<View> dockTiles = new ArrayList<View>();
    private final List<View> appTiles = new ArrayList<View>();
    private final List<View> allAppTiles = new ArrayList<View>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            bindClock();
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        wallpaperView = findViewById(R.id.wallpaper);
        dateView = findViewById(R.id.date);
        clockView = findViewById(R.id.clock);
        networkPill = findViewById(R.id.pill_network);
        dock = findViewById(R.id.dock);
        apps = findViewById(R.id.apps);
        settingsScrim = findViewById(R.id.settings_scrim);
        allAppsScrim = findViewById(R.id.all_apps_scrim);
        allAppsGrid = findViewById(R.id.all_apps_grid);
        wallpaperMode = findViewById(R.id.wallpaper_mode);
        autoOn = findViewById(R.id.auto_on);
        autoOff = findViewById(R.id.auto_off);
        wallpaperChoices = findViewById(R.id.wallpaper_choices);
        pinList = findViewById(R.id.pin_list);
        motionOff = findViewById(R.id.motion_off);
        motionOn = findViewById(R.id.motion_on);
        homeStatus = findViewById(R.id.home_status);
        bindClock();
        networkPill.setText(networkLabel());
        chooseWallpaperForThisStart();
        wirePills();
        wireSettings();
        fillDock();
        fillApps();
        wireFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(tick);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (firstDock != null) firstDock.requestFocus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !askedFocus && firstDock != null
                && settingsScrim.getVisibility() != View.VISIBLE
                && allAppsScrim.getVisibility() != View.VISIBLE) {
            askedFocus = true;
            firstDock.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (allAppsScrim.getVisibility() == View.VISIBLE) {
            hideAllApps();
            return;
        }
        if (settingsScrim.getVisibility() == View.VISIBLE) {
            hideSettings();
            return;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && allAppsScrim.getVisibility() == View.VISIBLE) {
            hideAllApps();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && settingsScrim.getVisibility() == View.VISIBLE) {
            hideSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void wirePills() {
        TextView inputs = findViewById(R.id.pill_inputs);
        TextView settings = findViewById(R.id.pill_settings);
        inputs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputs();
            }
        });
        networkPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TvIntents.openNetworkSettings(HomeActivity.this)) {
                    toast("打不开网络设置");
                }
            }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TvIntents.openSystemSettings(HomeActivity.this)) {
                    toast("打不开系统设置");
                }
            }
        });
        findViewById(R.id.pill_wallpaper).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleWallpaper();
            }
        });
        settings.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showLauncherSettings();
                return true;
            }
        });
    }

    private void wireSettings() {
        settingsScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSettings();
            }
        });
        autoOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs().edit().putBoolean(KEY_WALLPAPER_AUTO, true).apply();
                refreshSettings();
                toast("下次开机换一张");
            }
        });
        autoOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs().edit().putBoolean(KEY_WALLPAPER_AUTO, false).apply();
                refreshSettings();
                toast("已固定当前壁纸");
            }
        });
        motionOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs().edit().putBoolean(KEY_MOTION, false).apply();
                refreshSettings();
            }
        });
        motionOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs().edit().putBoolean(KEY_MOTION, true).apply();
                refreshSettings();
            }
        });
        findViewById(R.id.add_pin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddPin();
            }
        });
        findViewById(R.id.settings_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSettings();
            }
        });
        rebuildWallpaperChoices();
    }

    private void chooseWallpaperForThisStart() {
        SharedPreferences prefs = prefs();
        int index = prefs.getInt(KEY_WALLPAPER, 0);
        if (index < 0 || index >= Wallpapers.ALL.length) index = 0;
        boolean auto = prefs.getBoolean(KEY_WALLPAPER_AUTO, true);
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long saved = prefs.getLong(KEY_BOOT, Long.MIN_VALUE);
        if (Wallpapers.newBoot(saved, boot)) {
            if (auto && saved != Long.MIN_VALUE) {
                index = Wallpapers.nextIndex(index, Wallpapers.ALL.length);
            }
            prefs.edit().putInt(KEY_WALLPAPER, index).putLong(KEY_BOOT, boot).apply();
        }
        loadWallpaper(index);
    }

    private void cycleWallpaper() {
        int index = Wallpapers.nextIndex(prefs().getInt(KEY_WALLPAPER, 0), Wallpapers.ALL.length);
        prefs().edit()
                .putInt(KEY_WALLPAPER, index)
                .putBoolean(KEY_WALLPAPER_AUTO, false)
                .apply();
        loadWallpaper(index);
        if (settingsScrim.getVisibility() == View.VISIBLE) refreshSettings();
        toast("已换成" + Wallpapers.ALL[index].label);
    }

    private void setTileIcon(View tile, int drawableId) {
        if (!(tile instanceof LinearLayout) || ((LinearLayout) tile).getChildCount() == 0) return;
        View child = ((LinearLayout) tile).getChildAt(0);
        if (!(child instanceof ImageView)) return;
        ImageView icon = (ImageView) child;
        icon.setImageDrawable(roundedIcon(getDrawable(drawableId)));
        if (icon.getLayoutParams() != null) icon.getLayoutParams().width = icon.getLayoutParams().height;
    }

    private void loadWallpaper(final int index) {
        final int token = ++wallpaperToken;
        final String asset = Wallpapers.ALL[index].asset;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = decodeAsset(asset);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (token != wallpaperToken || bitmap == null) return;
                        wallpaperView.setImageBitmap(bitmap);
                    }
                });
            }
        }, "wallpaper").start();
    }

    private Bitmap decodeAsset(String asset) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream probe = getAssets().open(asset);
            BitmapFactory.decodeStream(probe, null, bounds);
            probe.close();
            int target = Math.max(getResources().getDisplayMetrics().widthPixels, 1280);
            target = Math.min(target, 2560);
            int sample = 1;
            while (bounds.outWidth / sample > target) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample / 2);
            InputStream stream = getAssets().open(asset);
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, opts);
            stream.close();
            return bitmap;
        } catch (IOException exception) {
            return null;
        }
    }

    private void showLauncherSettings() {
        refreshSettings();
        settingsScrim.setVisibility(View.VISIBLE);
        autoOn.requestFocus();
    }

    private void hideSettings() {
        settingsScrim.setVisibility(View.GONE);
        if (firstDock != null) firstDock.requestFocus();
    }

    private void showAllApps() {
        allAppsGrid.removeAllViews();
        allAppTiles.clear();
        List<ResolveInfo> rows = launchableApps();
        final int columns = 4;
        LinearLayout row = null;
        for (int i = 0; i < rows.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                allAppsGrid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            ResolveInfo info = rows.get(i);
            final String packageName = info.activityInfo.packageName;
            View tile = makeTile(labelOf(info), "", 0xFF8E8A84, packageName, true);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!TvIntents.openPackage(HomeActivity.this, packageName)) {
                        toast("打不开这个应用");
                    }
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.leftMargin = dp(6);
            params.rightMargin = dp(6);
            params.bottomMargin = dp(10);
            row.addView(tile, params);
            allAppTiles.add(tile);
        }
        if (row != null) {
            while (row.getChildCount() < columns) {
                View spacer = new View(this);
                spacer.setFocusable(false);
                row.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
            }
        }
        wireAllAppsFocus();
        allAppsScrim.setVisibility(View.VISIBLE);
        if (!allAppTiles.isEmpty()) allAppTiles.get(0).requestFocus();
    }

    private void hideAllApps() {
        allAppsScrim.setVisibility(View.GONE);
        if (allAppsButton != null) allAppsButton.requestFocus();
    }

    private void wireAllAppsFocus() {
        int columns = 4;
        int count = allAppTiles.size();
        for (int i = 0; i < count; i++) {
            View tile = allAppTiles.get(i);
            if (tile.getId() == View.NO_ID) tile.setId(View.generateViewId());
            boolean rowStart = i % columns == 0;
            boolean rowEnd = i % columns == columns - 1 || i == count - 1;
            tile.setNextFocusLeftId(rowStart ? tile.getId() : allAppTiles.get(i - 1).getId());
            tile.setNextFocusRightId(rowEnd ? tile.getId() : allAppTiles.get(i + 1).getId());
            tile.setNextFocusUpId(i < columns ? tile.getId() : allAppTiles.get(i - columns).getId());
            tile.setNextFocusDownId(i + columns < count ? allAppTiles.get(i + columns).getId() : tile.getId());
        }
    }

    private void refreshSettings() {
        SharedPreferences prefs = prefs();
        boolean auto = prefs.getBoolean(KEY_WALLPAPER_AUTO, true);
        int index = prefs.getInt(KEY_WALLPAPER, 0);
        if (index < 0 || index >= Wallpapers.ALL.length) index = 0;
        autoOn.setSelected(auto);
        autoOff.setSelected(!auto);
        wallpaperMode.setText(auto
                ? "每次开机换一张。现在是「" + Wallpapers.ALL[index].label + "」。左右选中一张会固定下来。"
                : "已固定为「" + Wallpapers.ALL[index].label + "」。打开「开机换一张」后，下次开机继续换。");
        for (int i = 0; i < wallpaperChoices.getChildCount(); i++) {
            wallpaperChoices.getChildAt(i).setSelected(i == index);
        }
        boolean motion = prefs.getBoolean(KEY_MOTION, false);
        motionOff.setSelected(!motion);
        motionOn.setSelected(motion);
        homeStatus.setText(isDefaultHome()
                ? "当前主页是客厅净桌面。"
                : "当前主页还不是这个桌面。安装时会再设一次。");
        rebuildPins();
    }

    private void rebuildWallpaperChoices() {
        wallpaperChoices.removeAllViews();
        for (int i = 0; i < Wallpapers.ALL.length; i++) {
            final int index = i;
            TextView chip = chip(Wallpapers.ALL[i].label);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.leftMargin = dp(8);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs().edit()
                            .putInt(KEY_WALLPAPER, index)
                            .putBoolean(KEY_WALLPAPER_AUTO, false)
                            .apply();
                    loadWallpaper(index);
                    refreshSettings();
                    toast("已固定为" + Wallpapers.ALL[index].label);
                }
            });
            wallpaperChoices.addView(chip, params);
        }
    }

    private void rebuildPins() {
        pinList.removeAllViews();
        List<String> pins = currentPinPackages();
        if (pins.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有固定应用。下面可以添加，输入源一直留在最右边。");
            empty.setTextColor(0xB8FFFFFF);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pinList.addView(empty);
            return;
        }
        for (int i = 0; i < pins.size(); i++) {
            final String packageName = pins.get(i);
            TextView row = chip(pinLabel(packageName) + "  ·  移除");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.topMargin = dp(8);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removePin(packageName);
                }
            });
            pinList.addView(row, params);
        }
    }

    private void removePin(String packageName) {
        List<String> pins = new ArrayList<String>(currentPinPackages());
        pins.remove(packageName);
        savePins(pins);
        fillDock();
        fillApps();
        wireFocus();
        refreshSettings();
    }

    private void showAddPin() {
        final List<String> pins = currentPinPackages();
        if (pins.size() >= MAX_PINS) {
            toast("主页最多放 " + MAX_PINS + " 个应用");
            return;
        }
        final List<ResolveInfo> choices = launchableApps();
        List<String> labels = new ArrayList<String>();
        final List<String> packages = new ArrayList<String>();
        for (ResolveInfo info : choices) {
            String packageName = info.activityInfo.packageName;
            if (pins.contains(packageName) || coveredByPin(packageName, pins)) continue;
            labels.add(labelOf(info));
            packages.add(packageName);
        }
        if (packages.isEmpty()) {
            toast("没有可以再添加的应用");
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("添加到主页")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    List<String> next = new ArrayList<String>(currentPinPackages());
                    String chosen = packages.get(which);
                    if (!next.contains(chosen) && next.size() < MAX_PINS) next.add(chosen);
                    savePins(next);
                    fillDock();
                    fillApps();
                    wireFocus();
                    refreshSettings();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private boolean coveredByPin(String packageName, List<String> pins) {
        AppCatalog.Slot slot = slotForPackage(packageName);
        if (slot == null) return false;
        for (String pin : pins) {
            if (slotForPackage(pin) == slot) return true;
        }
        return false;
    }

    private String pinLabel(String packageName) {
        AppCatalog.Slot slot = slotForPackage(packageName);
        if (slot != null) return slot.label;
        try {
            CharSequence label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0));
            if (label != null && label.length() > 0) return label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            // 用包名。
        }
        return packageName;
    }

    private List<String> currentPinPackages() {
        SharedPreferences prefs = prefs();
        if (!prefs.contains(KEY_PINS)) {
            List<String> defaults = new ArrayList<String>();
            for (AppCatalog.Slot slot : AppCatalog.DOCK) {
                String installed = installedPackage(slot.packages);
                defaults.add(installed != null ? installed : slot.packages[0]);
            }
            return defaults;
        }
        String raw = prefs.getString(KEY_PINS, "");
        List<String> pins = new ArrayList<String>();
        if (raw == null || raw.length() == 0) return pins;
        for (String packageName : raw.split("\n")) {
            if (packageName.length() > 0 && !pins.contains(packageName)) pins.add(packageName);
        }
        return pins;
    }

    private void savePins(List<String> packages) {
        prefs().edit().putString(KEY_PINS, TextUtils.join("\n", packages)).apply();
    }

    private boolean isDefaultHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null
                && info.activityInfo != null
                && getPackageName().equals(info.activityInfo.packageName);
    }

    private String networkLabel() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi == null || wifi.getConnectionInfo() == null) return getString(R.string.network);
            String ssid = wifi.getConnectionInfo().getSSID();
            if (ssid == null) return getString(R.string.network);
            ssid = ssid.replace("\"", "");
            if (ssid.length() == 0 || "<unknown ssid>".equals(ssid) || "0x".equals(ssid)) {
                return getString(R.string.network);
            }
            return ssid;
        } catch (RuntimeException exception) {
            return getString(R.string.network);
        }
    }

    private void fillDock() {
        dock.removeAllViews();
        dockTiles.clear();
        int shown = 0;
        for (String packageName : currentPinPackages()) {
            if (shown >= MAX_PINS) break;
            AppCatalog.Slot slot = slotForPackage(packageName);
            View tile;
            if (slot != null) {
                String installed = installedPackage(slot.packages);
                tile = makeTile(slot.label, "", slot.color, installed != null ? installed : packageName, true);
                final AppCatalog.Slot chosen = slot;
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openSlot(chosen);
                    }
                });
            } else {
                tile = makeTile(pinLabel(packageName), "", 0xFF8E8A84, packageName, true);
                final String chosen = packageName;
                tile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!TvIntents.openPackage(HomeActivity.this, chosen)) {
                            toast("打不开这个应用");
                        }
                    }
                });
            }
            dock.addView(tile, dockParams());
            dockTiles.add(tile);
            shown++;
        }
        View hdmi = makeTile("输入源", "HDMI / AV", 0xFF8E8A84, null, true);
        setTileIcon(hdmi, R.drawable.ic_input);
        hdmi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputs();
            }
        });
        dock.addView(hdmi, dockParams());
        dockTiles.add(hdmi);
        View allApps = makeTile(getString(R.string.all_apps), "", 0xFF8E8A84, null, true);
        setTileIcon(allApps, R.drawable.ic_apps);
        allAppsButton = allApps;
        allApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAllApps();
            }
        });
        dock.addView(allApps, dockParams());
        dockTiles.add(allApps);
        firstDock = dockTiles.isEmpty() ? null : dockTiles.get(0);
    }

    private void fillApps() {
        apps.removeAllViews();
        appTiles.clear();
        List<String> pins = currentPinPackages();
        PackageManager manager = getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<String, ResolveInfo>();
        collect(manager, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), found);
        collect(manager, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), found);
        List<ResolveInfo> rows = new ArrayList<ResolveInfo>();
        for (Map.Entry<String, ResolveInfo> entry : found.entrySet()) {
            String packageName = entry.getKey();
            if (AppCatalog.hidden(packageName)) continue;
            if (pins.contains(packageName) || coveredByPin(packageName, pins)) continue;
            rows.add(entry.getValue());
        }
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(rows, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo left, ResolveInfo right) {
                return collator.compare(labelOf(left), labelOf(right));
            }
        });
        for (int i = 0; i < rows.size(); i++) {
            ResolveInfo info = rows.get(i);
            final String packageName = info.activityInfo.packageName;
            View tile = makeTile(labelOf(info), "", 0xFF8E8A84, packageName, false);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!TvIntents.openPackage(HomeActivity.this, packageName)) {
                        toast("打不开这个应用");
                    }
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(168), LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.leftMargin = dp(8);
            apps.addView(tile, params);
            appTiles.add(tile);
        }
    }

    private List<ResolveInfo> launchableApps() {
        PackageManager manager = getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<String, ResolveInfo>();
        collect(manager, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), found);
        collect(manager, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), found);
        List<ResolveInfo> rows = new ArrayList<ResolveInfo>();
        for (Map.Entry<String, ResolveInfo> entry : found.entrySet()) {
            if (AppCatalog.hidden(entry.getKey())) continue;
            rows.add(entry.getValue());
        }
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(rows, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo left, ResolveInfo right) {
                return collator.compare(labelOf(left), labelOf(right));
            }
        });
        return rows;
    }

    private void wireFocus() {
        View inputs = findViewById(R.id.pill_inputs);
        View network = findViewById(R.id.pill_network);
        View settings = findViewById(R.id.pill_settings);
        View wallpaper = findViewById(R.id.pill_wallpaper);
        View[] pills = new View[] {inputs, network, settings, wallpaper};
        chain(pills);
        chain(dockTiles);
        chain(appTiles);
        for (int i = 0; i < pills.length; i++) {
            View below = firstOf(appTiles, dockTiles, i);
            if (below != null) pills[i].setNextFocusDownId(below.getId());
            pills[i].setNextFocusUpId(pills[i].getId());
        }
        for (int i = 0; i < appTiles.size(); i++) {
            View tile = appTiles.get(i);
            tile.setNextFocusUpId(pills[Math.min(i, pills.length - 1)].getId());
            if (!dockTiles.isEmpty()) {
                tile.setNextFocusDownId(dockTiles.get(Math.min(i, dockTiles.size() - 1)).getId());
            } else {
                tile.setNextFocusDownId(tile.getId());
            }
        }
        for (int i = 0; i < dockTiles.size(); i++) {
            View tile = dockTiles.get(i);
            View above = appTiles.isEmpty() ? pills[Math.min(i, pills.length - 1)] : appTiles.get(Math.min(i, appTiles.size() - 1));
            tile.setNextFocusUpId(above.getId());
            tile.setNextFocusDownId(tile.getId());
        }
    }

    private View firstOf(List<View> preferred, List<View> fallback, int index) {
        if (!preferred.isEmpty()) return preferred.get(Math.min(index, preferred.size() - 1));
        if (!fallback.isEmpty()) return fallback.get(Math.min(index, fallback.size() - 1));
        return null;
    }

    private void chain(View[] views) {
        List<View> list = new ArrayList<View>();
        Collections.addAll(list, views);
        chain(list);
    }

    private void chain(List<View> views) {
        for (int i = 0; i < views.size(); i++) {
            View view = views.get(i);
            if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
            view.setNextFocusLeftId(i == 0 ? view.getId() : views.get(i - 1).getId());
            view.setNextFocusRightId(i == views.size() - 1 ? view.getId() : views.get(i + 1).getId());
        }
    }

    private void openSlot(AppCatalog.Slot slot) {
        String installed = installedPackage(slot.packages);
        if (installed != null && TvIntents.openPackage(this, installed)) return;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("还没安装" + slot.label)
                .setMessage("在小米应用商店搜索「" + slot.storeName + "」。这是电视版，遥控器好用。")
                .setPositiveButton("打开应用商店", (d, which) -> {
                    if (!TvIntents.openStore(HomeActivity.this)) {
                        toast("没有找到小米应用商店");
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showInputs() {
        final List<TvInputInfo> inputs = passthroughInputs();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("输入源");
        if (inputs.isEmpty()) {
            builder.setMessage("没有读到 HDMI。遥控器上的信号源键也可以切换。");
            builder.setPositiveButton("关闭", null);
            builder.show();
            return;
        }
        String[] labels = new String[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            CharSequence label = inputs.get(i).loadLabel(this);
            labels[i] = label == null ? "输入源" : label.toString();
        }
        builder.setItems(labels, (dialog, which) -> openInput(inputs.get(which)));
        builder.show();
    }

    private List<TvInputInfo> passthroughInputs() {
        List<TvInputInfo> result = new ArrayList<TvInputInfo>();
        TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (manager == null) return result;
        List<TvInputInfo> all = manager.getTvInputList();
        if (all == null) return result;
        for (TvInputInfo info : all) {
            if (info != null && info.isPassthroughInput()) result.add(info);
        }
        return result;
    }

    private void openInput(TvInputInfo info) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(TvContract.buildChannelUriForPassthroughInput(info.getId()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception exception) {
            toast("这个输入源打不开");
        }
    }

    private AppCatalog.Slot slotForPackage(String packageName) {
        for (AppCatalog.Slot slot : AppCatalog.DOCK) {
            for (String candidate : slot.packages) {
                if (candidate.equals(packageName)) return slot;
            }
        }
        return null;
    }

    private void collect(PackageManager manager, Intent intent, Map<String, ResolveInfo> into) {
        List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
        if (infos == null) return;
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (!into.containsKey(packageName)) into.put(packageName, info);
        }
    }

    private String labelOf(ResolveInfo info) {
        CharSequence label = info.loadLabel(getPackageManager());
        if (label == null || label.length() == 0) return info.activityInfo.packageName;
        return label.toString();
    }

    private void bindClock() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String hello;
        if (hour < 5) hello = "夜深了";
        else if (hour < 11) hello = "早上好";
        else if (hour < 14) hello = "中午好";
        else if (hour < 18) hello = "下午好";
        else hello = "晚上好";
        clockView.setText(String.format(Locale.CHINA, "%02d:%02d", hour, calendar.get(Calendar.MINUTE)));
        String[] weeks = {"日", "一", "二", "三", "四", "五", "六"};
        String week = "周" + weeks[calendar.get(Calendar.DAY_OF_WEEK) - 1];
        dateView.setText((calendar.get(Calendar.MONTH) + 1) + "月" + calendar.get(Calendar.DAY_OF_MONTH) + "日 " + week + "  ·  " + hello);
    }

    private View makeTile(String label, String subtitle, int color, String packageName, boolean large) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackgroundResource(R.drawable.tile_bg);
        int pad = large ? dp(12) : dp(8);
        tile.setPadding(pad, pad, pad, pad);
        if (tile.getId() == View.NO_ID) tile.setId(View.generateViewId());

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(roundedIcon(iconFor(packageName, color)));
        int iconSize = dp(40);
        tile.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(10);
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(getResources().getColorStateList(R.color.tile_text));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 18 : 14);
        name.setMaxLines(2);
        text.addView(name);
        if (subtitle != null && subtitle.length() > 0) {
            TextView line = new TextView(this);
            line.setText(subtitle);
            line.setTextColor(getResources().getColorStateList(R.color.tile_text));
            line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            line.setMaxLines(1);
            line.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(line);
        }
        tile.addView(text, textParams);
        tile.setContentDescription(label);
        if (prefs().getBoolean(KEY_MOTION, false)) {
            tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean hasFocus) {
                    float scale = hasFocus ? 1.04f : 1f;
                    view.animate().scaleX(scale).scaleY(scale).setDuration(140).start();
                }
            });
        }
        return tile;
    }

    private TextView chip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextColor(getResources().getColorStateList(R.color.tile_text));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setBackgroundResource(R.drawable.chip_bg);
        chip.setFocusable(true);
        chip.setClickable(true);
        int padH = dp(14);
        int padV = dp(8);
        chip.setPadding(padH, padV, padH, padV);
        if (chip.getId() == View.NO_ID) chip.setId(View.generateViewId());
        return chip;
    }

    private Drawable iconFor(String packageName, int color) {
        if (packageName != null) {
            try {
                Drawable icon = getPackageManager().getApplicationIcon(packageName);
                if (icon.getIntrinsicWidth() > 0 && icon.getIntrinsicHeight() > 0) return icon;
            } catch (PackageManager.NameNotFoundException ignored) {
                // 没装的应用用色块，名字写在旁边。
            }
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private Drawable roundedIcon(Drawable source) {
        int size = dp(40);
        float radius = dp(16);
        Drawable copy = source.getConstantState() != null
                ? source.getConstantState().newDrawable(getResources()).mutate()
                : source.mutate();
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Path path = new Path();
        path.addRoundRect(new RectF(0, 0, size, size), radius, radius, Path.Direction.CW);
        canvas.clipPath(path);
        int width = Math.max(copy.getIntrinsicWidth(), 1);
        int height = Math.max(copy.getIntrinsicHeight(), 1);
        float scale = Math.max(size / (float) width, size / (float) height);
        int drawnWidth = Math.round(width * scale);
        int drawnHeight = Math.round(height * scale);
        int left = (size - drawnWidth) / 2;
        int top = (size - drawnHeight) / 2;
        copy.setBounds(left, top, left + drawnWidth, top + drawnHeight);
        copy.draw(canvas);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private String installedPackage(String[] packages) {
        PackageManager manager = getPackageManager();
        for (String packageName : packages) {
            try {
                manager.getPackageInfo(packageName, 0);
                return packageName;
            } catch (PackageManager.NameNotFoundException ignored) {
                // 试下一个包名。
            }
        }
        return null;
    }

    private LinearLayout.LayoutParams dockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
