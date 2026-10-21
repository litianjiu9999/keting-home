package tv.keting.home;

/** 内置壁纸。照片来自 Unsplash，自然风光和动物各一半。 */
public final class Wallpapers {
    private Wallpapers() {}

    public static final class Item {
        public final String id;
        public final String asset;
        public final String label;

        public Item(String id, String asset, String label) {
            this.id = id;
            this.asset = asset;
            this.label = label;
        }
    }

    public static final Item[] ALL = new Item[] {
            new Item("alpine", "wallpapers/alpine.jpg", "雪山湖"),
            new Item("forest", "wallpapers/forest.jpg", "雾林"),
            new Item("coast", "wallpapers/coast.jpg", "海岸"),
            new Item("lake", "wallpapers/lake.jpg", "湖面"),
            new Item("hills", "wallpapers/hills.jpg", "山野"),
            new Item("mist", "wallpapers/mist.jpg", "云海"),
            new Item("valley", "wallpapers/valley.jpg", "谷地"),
            new Item("woods", "wallpapers/woods.jpg", "林间"),
            new Item("cat", "wallpapers/cat.jpg", "猫咪"),
            new Item("dogs", "wallpapers/dogs.jpg", "小狗"),
            new Item("puppy", "wallpapers/puppy.jpg", "狗狗"),
            new Item("kitten", "wallpapers/kitten.jpg", "小猫"),
            new Item("panda", "wallpapers/panda.jpg", "熊猫"),
            new Item("rabbit", "wallpapers/rabbit.jpg", "兔子"),
            new Item("fox", "wallpapers/fox.jpg", "狐狸"),
            new Item("kitten2", "wallpapers/kitten2.jpg", "奶猫"),
    };

    public static int nextIndex(int current, int count) {
        if (count <= 0) return 0;
        int safe = current;
        if (safe < 0 || safe >= count) safe = 0;
        return (safe + 1) % count;
    }

    /** bootMark 用当前时间减去开机后的毫秒数，重启后会变。 */
    public static boolean newBoot(long savedBootMark, long bootMark) {
        if (savedBootMark == Long.MIN_VALUE) return true;
        return Math.abs(bootMark - savedBootMark) > 20_000L;
    }
}
