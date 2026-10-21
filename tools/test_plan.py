import json
import tempfile
import unittest
from pathlib import Path

from plan import (
    DeviceState,
    Options,
    PlanError,
    build_plan,
    demo_state,
    load_profile,
    merge_backup,
    restore_commands,
    validate_profile,
)


ROOT = Path(__file__).resolve().parents[1]


class PlanTests(unittest.TestCase):
    def setUp(self):
        self.profile, self.state = demo_state()

    def test_profile_loads(self):
        profile = load_profile(ROOT / "profile" / "living-room.json")
        self.assertEqual(profile["device"]["name"], "客厅的小米电视")
        self.assertEqual(profile["launcher"]["package"], "tv.keting.home")

    def test_demo_disables_ads_and_keeps_store(self):
        steps, _warnings = build_plan(self.profile, self.state, Options())
        commands = [step.command for step in steps if step.command]
        self.assertIn("pm disable-user --user 0 com.xiaomi.mitv.advertise", commands)
        self.assertNotIn("pm disable-user --user 0 com.xiaomi.mitv.appstore", commands)
        self.assertTrue(all(command is None or "uninstall" not in command for command in commands))

    def test_stock_home_is_after_launcher_start(self):
        steps, _warnings = build_plan(self.profile, self.state, Options())
        kinds = [step.kind for step in steps]
        self.assertLess(kinds.index("start_launcher"), kinds.index("disable_home"))
        early = [step.command for step in steps if step.kind == "disable"]
        self.assertNotIn("pm disable-user --user 0 com.mitv.tvhome", early)
        self.assertNotIn("pm disable-user --user 0 com.google.android.tvlauncher", early)

    def test_youku_ready_and_others_go_to_the_store(self):
        steps, _warnings = build_plan(self.profile, self.state, Options())
        details = [step.detail for step in steps]
        self.assertTrue(any("优酷 已经装好" in detail for detail in details))
        self.assertTrue(any("云视听小电视" in detail for detail in details))
        self.assertTrue(any("央视频" in detail for detail in details))

    def test_low_space_skips_apk(self):
        self.state.free_kb = 100_000
        steps, warnings = build_plan(
            self.profile,
            self.state,
            Options(apk_exists={"bilibili": True, "youku": True, "yangshipin": True}),
        )
        self.assertFalse(any(step.kind == "install_apk" and "哔哩哔哩" in step.detail for step in steps))
        self.assertTrue(any("空间不够" in warning for warning in warnings))

    def test_skip_and_also(self):
        self.state.enabled.add("com.netflix.ninja")
        steps, _warnings = build_plan(
            self.profile,
            self.state,
            Options(skip=["com.xiaomi.mitv.advertise"], also=["com.netflix.ninja"]),
        )
        commands = [step.command or "" for step in steps]
        self.assertFalse(any("com.xiaomi.mitv.advertise" in command for command in commands))
        self.assertTrue(any("com.netflix.ninja" in command for command in commands))

    def test_absent_package_is_skipped(self):
        self.state.enabled.discard("com.xiaomi.mitv.advertise")
        steps, _warnings = build_plan(self.profile, self.state, Options())
        skipped = [step.detail for step in steps if step.kind == "skip"]
        self.assertTrue(any("广告服务" in detail for detail in skipped))

    def test_phone_is_refused(self):
        self.state.properties["ro.build.characteristics"] = "nosdcard"
        with self.assertRaises(PlanError):
            build_plan(self.profile, self.state, Options())

    def test_keep_overlap_is_refused(self):
        broken = json.loads(json.dumps(self.profile))
        broken["disable"].append(
            {
                "package": "com.xiaomi.mitv.appstore",
                "name": "商店",
                "group": "ads",
                "reason": "不该出现",
                "default": True,
            }
        )
        with self.assertRaises(PlanError):
            validate_profile(broken)

    def test_gitv_prefix_is_refused(self):
        broken = json.loads(json.dumps(self.profile))
        broken["disable"].append(
            {
                "package": "com.gitv.tv.extra",
                "name": "银河",
                "group": "ads",
                "reason": "不该出现",
                "default": True,
            }
        )
        with self.assertRaises(PlanError):
            validate_profile(broken)

    def test_disabled_dock_app_is_enabled(self):
        self.state.enabled.discard("com.cibn.tv")
        self.state.disabled.add("com.cibn.tv")
        steps, _warnings = build_plan(self.profile, self.state, Options())
        self.assertTrue(any(step.command == "pm enable --user 0 com.cibn.tv" for step in steps))

    def test_backup_keeps_the_first_record(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "tv.json"
            state = DeviceState(
                properties={"ro.build.characteristics": "tv"},
                enabled={"com.xiaomi.mitv.advertise"},
                disabled=set(),
                home="com.mitv.tvhome/.MainActivity",
                animations={"window_animation_scale": "1.0"},
                free_kb=None,
            )
            merge_backup(
                path,
                ["com.xiaomi.mitv.advertise"],
                state,
                state.properties,
                state.home,
                state.animations,
            )
            state.enabled.discard("com.xiaomi.mitv.advertise")
            state.disabled.add("com.xiaomi.mitv.advertise")
            data = merge_backup(
                path,
                ["com.xiaomi.mitv.advertise", "com.xiaomi.statistic"],
                state,
                state.properties,
                state.home,
                state.animations,
            )
            self.assertEqual(data["packages"]["com.xiaomi.mitv.advertise"], "enabled")
            self.assertEqual(data["packages"]["com.xiaomi.statistic"], "absent")
            commands = restore_commands(data)
            self.assertIn("pm enable --user 0 com.xiaomi.mitv.advertise", commands)
            self.assertIn("cmd package set-home-activity com.mitv.tvhome/.MainActivity", commands)

    def test_catalog_matches_the_android_app(self):
        java = (ROOT / "launcher/app/src/main/java/tv/keting/home/AppCatalog.java").read_text(encoding="utf-8")
        gradle = (ROOT / "launcher/app/build.gradle").read_text(encoding="utf-8")
        self.assertIn(self.profile["launcher"]["package"], gradle)
        for app in self.profile["dock"]:
            for package in app["packages"]:
                self.assertIn(package, java)
        for package in self.profile["stockHomes"]:
            self.assertIn(package, java)


class InstallParseTests(unittest.TestCase):
    def test_apk_path_from_pm_output(self):
        from install import apk_path_from_pm_output

        self.assertEqual(
            apk_path_from_pm_output("package:/data/app/com.mitv.tvhome-1/base.apk\n"),
            "/data/app/com.mitv.tvhome-1/base.apk",
        )
        self.assertIsNone(apk_path_from_pm_output(""))

    def test_stock_home_backup_ignores_a_short_file(self):
        from restore import stock_home_backup

        with tempfile.TemporaryDirectory() as tmp:
            short = Path(tmp) / "192.168.0.103-tvhome.apk"
            short.write_bytes(b"x" * 100)
            self.assertIsNone(stock_home_backup("192.168.0.103", tmp))
            short.write_bytes(b"x" * 100000)
            self.assertEqual(stock_home_backup("192.168.0.103", tmp), short)


if __name__ == "__main__":
    unittest.main()
