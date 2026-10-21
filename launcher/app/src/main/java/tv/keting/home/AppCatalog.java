package tv.keting.home;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 底栏和要藏起来的桌面包。包名和 profile/living-room.json 保持一致。 */
public final class AppCatalog {
    private AppCatalog() {}

    public static final class Slot {
        public final String id;
        public final String label;
        public final String storeName;
        public final String subtitle;
        public final int color;
        public final String[] packages;

        public Slot(String id, String label, String storeName, String subtitle, int color, String[] packages) {
            this.id = id;
            this.label = label;
            this.storeName = storeName;
            this.subtitle = subtitle;
            this.color = color;
            this.packages = packages;
        }
    }

    public static final Slot[] DOCK = new Slot[] {
            new Slot(
                    "bilibili",
                    "哔哩哔哩",
                    "云视听小电视",
                    "电视版。应用商店里搜「云视听小电视」",
                    0xFFFB7299,
                    new String[] {"com.xiaodianshi.tv.yst", "tv.danmaku.bili", "com.bilibili.tv"}),
            new Slot(
                    "youku",
                    "优酷",
                    "酷喵",
                    "电视版叫酷喵",
                    0xFF1CB5E0,
                    new String[] {"com.cibn.tv", "com.youku.tv", "com.youku.phone"}),
            new Slot(
                    "yangshipin",
                    "央视频",
                    "央视频",
                    "电视版。应用商店里搜「央视频」",
                    0xFFE23D3D,
                    new String[] {"com.newtv.cboxtv", "com.cctv.yangshipin", "cn.cntvhd"}),
    };

    public static final String[] STOCK_HOMES = new String[] {
            "com.mitv.tvhome",
            "com.mitv.tvhome.atv",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.xiaomi.mitv.tvhome",
    };

    private static final Set<String> HIDDEN = new HashSet<String>(Arrays.asList(STOCK_HOMES));

    static {
        HIDDEN.add("tv.keting.home");
    }

    public static boolean hidden(String packageName) {
        return HIDDEN.contains(packageName);
    }

    public static Set<String> dockPackages() {
        Set<String> packages = new HashSet<String>();
        for (Slot slot : DOCK) {
            Collections.addAll(packages, slot.packages);
        }
        return packages;
    }
}
