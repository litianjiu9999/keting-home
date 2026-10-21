"""Pure planning for the living-room Xiaomi TV. No device calls live here."""

from __future__ import annotations

import json
from pathlib import Path


LAUNCHER_PACKAGE = "tv.keting.home"
HOME_GROUP = "home"
KEEP_PREFIXES = ("com.gitv.",)
MIN_FREE_KB = 350_000
APK_ESTIMATE_KB = 180_000


class PlanError(Exception):
    pass


class DeviceState:
    def __init__(self, properties, enabled, disabled, home, animations, free_kb):
        self.properties = properties
        self.enabled = set(enabled)
        self.disabled = set(disabled)
        self.home = home
        self.animations = dict(animations)
        self.free_kb = free_kb

    def installed(self):
        return self.enabled | self.disabled


class Options:
    def __init__(self, skip=(), also=(), replace_home=True, apk_exists=None, launcher_apk=True):
        self.skip = frozenset(skip)
        self.also = frozenset(also)
        self.replace_home = replace_home
        self.apk_exists = dict(apk_exists or {})
        self.launcher_apk = launcher_apk


class Step:
    def __init__(self, kind, detail, command=None):
        self.kind = kind
        self.detail = detail
        self.command = command

    def __repr__(self):
        return f"Step({self.kind!r}, {self.detail!r}, {self.command!r})"


def load_profile(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    validate_profile(data)
    return data


def validate_profile(profile):
    disable = [item["package"] for item in profile["disable"]]
    keep = {item["package"] for item in profile["keep"]}
    dock = {pkg for app in profile["dock"] for pkg in app["packages"]}
    overlap = sorted(set(disable) & keep)
    if overlap:
        raise PlanError("停用名单碰到了保留包：" + ", ".join(overlap))
    dock_hit = sorted(set(disable) & dock)
    if dock_hit:
        raise PlanError("停用名单碰到了要预装的应用：" + ", ".join(dock_hit))
    for package in disable:
        if package.startswith(KEEP_PREFIXES):
            raise PlanError("银河牌照相关的包不能停用：" + package)
    if len(disable) != len(set(disable)):
        raise PlanError("停用名单里有重复包名")


def selected_items(profile, options):
    chosen = []
    for item in profile["disable"]:
        package = item["package"]
        if package in options.skip:
            continue
        if item["default"] or package in options.also:
            chosen.append(item)
    return chosen


def package_status(state, package):
    if package in state.enabled:
        return "enabled"
    if package in state.disabled:
        return "disabled"
    return "absent"


def home_package(home):
    if not home or "/" not in home:
        return None
    return home.split("/", 1)[0]


def looks_like_tv(properties):
    characteristics = properties.get("ro.build.characteristics", "")
    return "tv" in characteristics.lower()


def build_plan(profile, state, options):
    validate_profile(profile)
    steps = []
    warnings = []

    if not looks_like_tv(state.properties):
        raise PlanError("这台设备不像电视。ro.build.characteristics 里没有 tv。确认 IP 是客厅的小米电视。")

    chosen = selected_items(profile, options)
    early = [item for item in chosen if item["group"] != HOME_GROUP]
    deferred = [item for item in chosen if item["group"] == HOME_GROUP]

    steps.append(Step("backup", "记下当前主页、动画速度和这些包的开关"))

    for item in early:
        status = package_status(state, item["package"])
        if status == "absent":
            steps.append(Step("skip", f"{item['name']} 没装，跳过"))
            continue
        if status == "disabled":
            steps.append(Step("skip", f"{item['name']} 已经关着"))
            continue
        steps.append(
            Step(
                "disable",
                f"停用{item['name']}",
                f"pm disable-user --user 0 {item['package']}",
            )
        )

    free_kb = state.free_kb
    for app in profile["dock"]:
        installed = next((pkg for pkg in app["packages"] if pkg in state.installed()), None)
        if installed and installed in state.disabled:
            steps.append(
                Step(
                    "enable",
                    f"重新打开{app['label']}",
                    f"pm enable --user 0 {installed}",
                )
            )
            continue
        if installed:
            steps.append(Step("ready", f"{app['label']} 已经装好（{installed}）"))
            continue
        if options.apk_exists.get(app["id"]):
            if free_kb is not None and free_kb < MIN_FREE_KB:
                warnings.append(f"剩余空间不够安装{app['label']}，先在电视上卸掉不用的用户应用")
                steps.append(Step("space", f"空间不够，跳过安装{app['label']}"))
                continue
            steps.append(Step("install_apk", f"安装{app['label']}", app["apk"]))
            if free_kb is not None:
                free_kb -= APK_ESTIMATE_KB
            continue
        steps.append(
            Step(
                "store",
                f"在小米应用商店搜索「{app['storeName']}」来安装{app['label']}",
            )
        )

    if options.launcher_apk:
        steps.append(Step("install_launcher", "安装客厅净桌面", profile["launcher"]["package"]))
        steps.append(
            Step(
                "start_launcher",
                "先打开新桌面，确认进程起来",
                f"am start -n {profile['launcher']['activity']}",
            )
        )
    else:
        warnings.append("还没有编译出桌面安装包")
        steps.append(Step("missing_launcher", "先编译 launcher，再装到电视上"))

    if options.replace_home and options.launcher_apk:
        owner = home_package(state.home)
        for item in deferred:
            if package_status(state, item["package"]) != "enabled":
                continue
            steps.append(
                Step(
                    "disable_home",
                    f"新桌面确认打开之后，停用{item['name']}",
                    f"pm disable-user --user 0 {item['package']}",
                )
            )
        if owner and owner in profile["stockHomes"] and owner not in {item["package"] for item in deferred}:
            steps.append(
                Step(
                    "disable_home",
                    "新桌面确认打开之后，停用当前主页",
                    f"pm disable-user --user 0 {owner}",
                )
            )
        steps.append(
            Step(
                "set_home",
                "把主页交给客厅净桌面",
                f"cmd package set-home-activity {profile['launcher']['activity']}",
            )
        )
    elif not options.replace_home:
        steps.append(Step("keep_home", "按你的选择，原来的主页先留着"))

    scale = profile.get("animationScale", "0.5")
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        steps.append(
            Step(
                "animation",
                "把动画调轻一点",
                f"settings put global {key} {scale}",
            )
        )

    for step in steps:
        if step.command and "uninstall" in step.command:
            raise PlanError("计划里出现了卸载命令")
    return steps, warnings


def render_text(steps, warnings):
    lines = []
    for step in steps:
        if step.command:
            lines.append(f"{step.detail}：{step.command}")
        else:
            lines.append(step.detail)
    for warning in warnings:
        lines.append("注意：" + warning)
    return "\n".join(lines)


def demo_state():
    root = Path(__file__).resolve().parents[1]
    profile = load_profile(root / "profile" / "living-room.json")
    enabled = {item["package"] for item in profile["disable"] if item["default"]}
    enabled |= {item["package"] for item in profile["keep"]}
    enabled.add("com.cibn.tv")
    return profile, DeviceState(
        properties={
            "ro.product.model": "MiTV",
            "ro.product.manufacturer": "Xiaomi",
            "ro.build.characteristics": "nosdcard,tv",
            "ro.build.version.release": "9",
        },
        enabled=enabled,
        disabled=set(),
        home="com.mitv.tvhome/.MainActivity",
        animations={
            "window_animation_scale": "1.0",
            "transition_animation_scale": "1.0",
            "animator_duration_scale": "1.0",
        },
        free_kb=1_200_000,
    )


def merge_backup(path, packages, state, properties, home, animations):
    path = Path(path)
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
    else:
        data = {
            "properties": properties,
            "home": home,
            "animations": animations,
            "packages": {},
        }
    recorded = data.setdefault("packages", {})
    for package in packages:
        if package not in recorded:
            recorded[package] = package_status(state, package)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return data


def restore_commands(backup):
    commands = []
    for package, status in backup.get("packages", {}).items():
        if status == "enabled":
            commands.append(f"pm enable --user 0 {package}")
        elif status == "disabled":
            commands.append(f"pm disable-user --user 0 {package}")
    home = backup.get("home")
    owner = home_package(home)
    if owner:
        commands.append(f"pm enable --user 0 {owner}")
        if "/" in home:
            commands.append(f"cmd package set-home-activity {home}")
    for key, value in backup.get("animations", {}).items():
        if value and value != "null":
            commands.append(f"settings put global {key} {value}")
    return commands
