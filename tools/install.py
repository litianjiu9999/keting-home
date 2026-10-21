#!/usr/bin/env python3
"""把客厅净桌面装到同一 Wi-Fi 里的小米电视上。只停用，不卸载。"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import time
from pathlib import Path

from plan import (
    DeviceState,
    Options,
    PlanError,
    build_plan,
    demo_state,
    load_profile,
    merge_backup,
    render_text,
    selected_items,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PROFILE = ROOT / "profile" / "living-room.json"


def say(message):
    print(message, flush=True)


def run(command, check=True):
    completed = subprocess.run(command, text=True, capture_output=True)
    if check and completed.returncode != 0:
        detail = (completed.stderr or completed.stdout or "").strip()
        raise RuntimeError(detail or "命令失败：" + " ".join(command))
    return completed


def adb_base(serial):
    adb = shutil.which("adb")
    if not adb:
        raise RuntimeError("电脑上没有 adb。Debian/Ubuntu 用 sudo apt install adb，macOS 用 brew install android-platform-tools。")
    return [adb, "-s", serial]


def connect(host, port):
    adb = shutil.which("adb")
    if not adb:
        raise RuntimeError("电脑上没有 adb。Debian/Ubuntu 用 sudo apt install adb，macOS 用 brew install android-platform-tools。")
    serial = f"{host}:{port}"
    run([adb, "connect", serial])
    probe = run([adb, "-s", serial, "shell", "echo", "ok"], check=False)
    output = (probe.stdout or "") + (probe.stderr or "")
    if probe.returncode != 0 or "ok" not in probe.stdout:
        if "unauthorized" in output.lower() or "failed" in output.lower():
            raise RuntimeError("电视拒绝了调试。用遥控器在「允许 USB 调试吗」上选允许，并勾上始终允许，然后重新运行。")
        raise RuntimeError("连不上电视。确认电脑和电视在同一个 Wi-Fi，IP 没写错，开发者选项里的网络调试已经打开。\n" + output.strip())
    return serial


def shell(serial, command):
    completed = run(adb_base(serial) + ["shell", command], check=False)
    output = ((completed.stdout or "") + "\n" + (completed.stderr or "")).strip()
    failed = completed.returncode != 0 or "SecurityException" in output or output.startswith("Error:") or "\nError:" in output
    if failed:
        raise RuntimeError(output or command)
    return (completed.stdout or "").strip()


def properties(serial):
    keys = [
        "ro.product.model",
        "ro.product.device",
        "ro.product.manufacturer",
        "ro.build.version.release",
        "ro.build.display.id",
        "ro.build.characteristics",
    ]
    found = {}
    for key in keys:
        found[key] = shell(serial, f"getprop {key}")
    return found


def package_sets(serial):
    enabled, disabled = set(), set()

    def absorb(text, target):
        for line in text.splitlines():
            line = line.strip()
            if line.startswith("package:"):
                target.add(line.split(":", 1)[1])

    absorb(shell(serial, "pm list packages -e"), enabled)
    absorb(shell(serial, "pm list packages -d"), disabled)
    return enabled, disabled


def current_home(serial):
    completed = run(
        adb_base(serial) + [
            "shell",
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        ],
        check=False,
    )
    text = ((completed.stdout or "") + "\n" + (completed.stderr or "")).strip()
    if completed.returncode != 0 or "not found" in text.lower():
        return None
    for line in reversed([line.strip() for line in text.splitlines() if line.strip()]):
        if "/" in line and " " not in line:
            return line
    return None


def stock_homes_cleared(serial, profile):
    """Android 6 没有 cmd。其他桌面都停掉之后，主页键只会进这一个桌面。"""
    enabled, _disabled = package_sets(serial)
    launcher = profile["launcher"]["package"]
    if launcher not in enabled:
        return False
    return all(package not in enabled for package in profile.get("stockHomes", []))


def animations(serial):
    found = {}
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        found[key] = shell(serial, f"settings get global {key}")
    return found


def launcher_running(serial):
    completed = run(adb_base(serial) + ["shell", "pidof tv.keting.home"], check=False)
    if completed.returncode == 0 and (completed.stdout or "").strip():
        return True
    ps = run(adb_base(serial) + ["shell", "ps"], check=False)
    text = (ps.stdout or "") + (ps.stderr or "")
    return "tv.keting.home" in text


def free_kb(serial):
    text = shell(serial, "df -k /data")
    for line in text.splitlines():
        if "/data" not in line:
            continue
        parts = line.split()
        if len(parts) >= 4 and parts[3].isdigit():
            return int(parts[3])
    return None


def read_state(serial):
    enabled, disabled = package_sets(serial)
    return DeviceState(
        properties=properties(serial),
        enabled=enabled,
        disabled=disabled,
        home=current_home(serial),
        animations=animations(serial),
        free_kb=free_kb(serial),
    )


def launcher_apk():
    candidates = [
        ROOT / "dist" / "keting-home.apk",
        ROOT / "launcher" / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk",
        ROOT / "launcher" / "app" / "build" / "outputs" / "apk" / "release" / "app-release-unsigned.apk",
    ]
    for path in candidates:
        if path.exists():
            return path
    return None


def apply_device_command(serial, command, dry_run):
    say("  " + command)
    if dry_run:
        return ""
    return shell(serial, command)


def install_apk(serial, path, dry_run):
    say(f"  adb install -r {path}")
    if dry_run:
        return
    run(adb_base(serial) + ["install", "-r", "-d", str(path)])


def main(argv=None):
    parser = argparse.ArgumentParser(description="把客厅净桌面装到小米电视上")
    parser.add_argument("--host", default="", help="电视的 IPv4。先在电视「网络信息」里看，不要猜。")
    parser.add_argument("--port", type=int, default=5555)
    parser.add_argument("--profile", default=str(DEFAULT_PROFILE))
    parser.add_argument("--skip", default="", help="逗号分隔，这些包这次不停")
    parser.add_argument("--also", default="", help="逗号分隔，额外停用可选名单里的包")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--demo", action="store_true", help="不连接电视，用一份示例状态打印计划")
    parser.add_argument("--no-replace-home", action="store_true")
    parser.add_argument(
        "--handoff-home",
        action="store_true",
        help="人正看着电视时才加。停用旧桌面失败后，先备份，再卸掉当前用户的小米桌面。",
    )
    parser.add_argument("--force", action="store_true", help="设备不像电视时仍然继续")
    parser.add_argument("--backup-dir", default=str(Path.home() / ".keting-tv"))
    args = parser.parse_args(argv)

    profile = load_profile(args.profile)
    skip = [item for item in args.skip.split(",") if item]
    also = [item for item in args.also.split(",") if item]

    if args.demo:
        demo_profile, state = demo_state()
        profile = demo_profile
        options = Options(skip=skip, also=also, replace_home=not args.no_replace_home, launcher_apk=True)
        steps, warnings = build_plan(profile, state, options)
        say(render_text(steps, warnings))
        return 0

    if not args.host:
        say("缺少电视地址。先按 README 在电视上查出 IPv4，再运行：")
        say("  python tools/install.py --host 电视的IPv4")
        return 2

    serial = connect(args.host, args.port)
    state = read_state(serial)
    say(
        "电视：{model} / Android {release} / {characteristics}".format(
            model=state.properties.get("ro.product.model") or "未知型号",
            release=state.properties.get("ro.build.version.release") or "未知",
            characteristics=state.properties.get("ro.build.characteristics") or "未知",
        )
    )
    if "tv" not in state.properties.get("ro.build.characteristics", "").lower() and not args.force:
        say("这台设备的特性里没有 tv。为了避免把手机上的包停掉，这次停止。确认 IP 之后可以加 --force。")
        return 2

    apk_exists = {}
    for app in profile["dock"]:
        apk_exists[app["id"]] = (ROOT / app["apk"]).exists()
    apk = launcher_apk()
    options = Options(
        skip=skip,
        also=also,
        replace_home=not args.no_replace_home,
        apk_exists=apk_exists,
        launcher_apk=apk is not None,
    )
    try:
        steps, warnings = build_plan(profile, state, options)
    except PlanError as exc:
        say(str(exc))
        return 2

    if apk is None and not args.dry_run:
        say("没有找到桌面安装包。在项目目录运行 tools/build_launcher.sh，然后再安装。这次没有改电视。")
        return 2

    intended = [item["package"] for item in selected_items(profile, options)]
    backup_path = Path(args.backup_dir) / f"{args.host}.json"
    if not args.dry_run:
        merge_backup(
            backup_path,
            intended,
            state,
            state.properties,
            state.home,
            state.animations,
        )
        say(f"状态记在 {backup_path}")
    else:
        say(f"演练，不会改电视。真的安装之后，状态会记在 {backup_path}")

    for warning in warnings:
        say("注意：" + warning)

    disabled_homes = []
    for step in steps:
        say(step.detail)
        if step.kind in {"disable", "enable", "animation"} and step.command:
            try:
                apply_device_command(serial, step.command, args.dry_run)
            except RuntimeError as exc:
                package = step.command.rsplit(" ", 1)[-1]
                if step.kind == "disable" and not args.dry_run and try_uninstall(serial, package):
                    say(f"  停用被拒绝，已卸载 {package}")
                elif step.kind == "disable":
                    say(f"  系统不允许停用 {package}")
                else:
                    raise RuntimeError(str(exc)) from exc
        elif step.kind == "install_apk" and not args.dry_run:
            install_apk(serial, ROOT / step.command, dry_run=False)
        elif step.kind == "install_apk":
            say(f"  adb install -r {ROOT / step.command}")
        elif step.kind == "install_launcher":
            if apk is None:
                say("没有找到桌面安装包。在项目目录运行 tools/build_launcher.sh。")
                return 2
            install_apk(serial, apk, args.dry_run)
        elif step.kind == "start_launcher" and step.command:
            apply_device_command(serial, step.command, args.dry_run)
            if not args.dry_run and not launcher_running(serial):
                say("新桌面没有跑起来，原来的小米桌面保持原样。")
                return 2
        elif step.kind == "disable_home" and step.command:
            package = step.command.rsplit(" ", 1)[-1]
            try:
                apply_device_command(serial, step.command, args.dry_run)
                disabled_homes.append(package)
            except RuntimeError:
                say("  系统不允许停用旧桌面，主页键改走无障碍服务")
        elif step.kind == "set_home" and step.command:
            if args.dry_run:
                say("  " + step.command)
                continue
            try:
                apply_device_command(serial, step.command, dry_run=False)
            except RuntimeError as exc:
                say("设置主页的命令不被这版系统接受：" + str(exc))
            home = current_home(serial) or ""
            launcher_package = profile["launcher"]["package"]
            if launcher_package not in home and stock_homes_cleared(serial, profile):
                say("这台系统不能用命令指定主页。其他桌面已经停用，按主页键会打开客厅净桌面。")
            elif launcher_package not in home and disabled_homes:
                say("主页没有交到客厅净桌面。正在把刚才停掉的旧桌面打开。")
                for package in disabled_homes:
                    try:
                        shell(serial, f"pm enable --user 0 {package}")
                    except RuntimeError as exc:
                        say(str(exc))
                say("旧桌面已重新打开。主页键仍会尝试交回客厅净桌面。")
            elif launcher_package not in home and args.handoff_home:
                if handoff_stock_home(serial, profile):
                    say("已卸下当前用户的小米桌面，主页键回到客厅净桌面。")
                else:
                    say("交接没有完成。旧桌面若已被卸下，立刻运行 restore.py 把备份装回去。")
            elif launcher_package not in home:
                say("这台系统不能用命令指定默认主页，旧桌面没有动。")
                say("应用库里的「客厅净桌面」现在就能打开。")
                say("要让遥控器主页键也回到新桌面：人看着电视屏幕，再运行同一次安装并加上 --handoff-home。")
        elif step.kind == "missing_launcher":
            say("没有找到桌面安装包。在项目目录运行 tools/build_launcher.sh。")
            return 2

    if not args.dry_run:
        remove_known_junk(serial)
        enable_home_key(serial, profile)
    if args.handoff_home:
        say("做完了。请人在电视上按主页键，确认是客厅净桌面，再把网络调试关掉。")
    else:
        say("做完了。应用库里可以打开「客厅净桌面」。主页键若仍是旧桌面，让人看着屏幕后再加 --handoff-home。")
    return 0


REMOVABLE = (
    "com.xiaomi.tv.gallery",
    "com.miui.systemAdSolution",
    "com.xm.webcontent",
    "com.mitv.shoplugin",
    "com.mitv.care",
    "com.miui.tv.analytics",
    "com.xiaomi.gamecenter.sdk.service.mibox",
    "com.xiaomi.mitv.handbook",
)


def try_uninstall(serial, package):
    if package not in REMOVABLE:
        return False
    completed = run(adb_base(serial) + ["shell", "pm", "uninstall", package], check=False)
    text = ((completed.stdout or "") + (completed.stderr or "")).strip()
    return "Success" in text


def remove_known_junk(serial):
    say("开机广告开关关掉，并卸载这台电视允许删除的广告和预装")
    apply_device_command(serial, "settings put system boot_ad 0", dry_run=False)
    apply_device_command(serial, "settings put system boot_ad_switch_show 0", dry_run=False)
    for package in REMOVABLE:
        completed = run(adb_base(serial) + ["shell", "pm", "uninstall", package], check=False)
        text = ((completed.stdout or "") + (completed.stderr or "")).strip() or "无输出"
        if "Success" in text:
            say(f"  已卸载 {package}")
        elif "not installed" in text.lower() or "Unknown package" in text or "DELETE_FAILED" in text:
            continue
        else:
            say(f"  {package}：{text.splitlines()[-1]}")


def apk_path_from_pm_output(text):
    for line in (text or "").splitlines():
        line = line.strip()
        if line.startswith("package:"):
            return line.split(":", 1)[1].strip()
    return None


def resumed_activity(serial):
    completed = run(adb_base(serial) + ["shell", "dumpsys", "activity", "activities"], check=False)
    text = (completed.stdout or "") + (completed.stderr or "")
    for line in text.splitlines():
        if "mResumedActivity" in line:
            return line.strip()
    return ""


def handoff_stock_home(serial, profile):
    """停用被拒绝时，卸掉当前用户的小米桌面。先备份，主页键没交过来就装回去。"""
    package = "com.mitv.tvhome"
    launcher = profile["launcher"]["package"]
    if not launcher_running(serial):
        say("新桌面不在运行，不动小米桌面。")
        return False
    try:
        remote = apk_path_from_pm_output(shell(serial, f"pm path {package}"))
    except RuntimeError as exc:
        say("读不到小米桌面路径：" + str(exc).splitlines()[0])
        return False
    if not remote:
        say("没有找到小米桌面的安装包，跳过。")
        return False
    backup = Path.home() / ".keting-tv" / f"{serial.split(':')[0]}-tvhome.apk"
    backup.parent.mkdir(parents=True, exist_ok=True)
    run(adb_base(serial) + ["pull", remote, str(backup)], check=False)
    if not backup.exists() or backup.stat().st_size < 100000:
        say("小米桌面没有备份成功，不卸载。")
        return False
    say(f"小米桌面已备份到 {backup}")
    completed = run(adb_base(serial) + ["shell", "pm", "uninstall", "--user", "0", package], check=False)
    text = ((completed.stdout or "") + (completed.stderr or "")).strip()
    say("  pm uninstall --user 0 " + package + " -> " + (text or "无输出"))
    if "Success" not in text:
        return False
    run(adb_base(serial) + ["shell", "input", "keyevent", "3"], check=False)
    time.sleep(2)
    if launcher in resumed_activity(serial):
        return True
    say("主页键没有回到客厅净桌面，正在把小米桌面装回去。")
    restored = run(adb_base(serial) + ["install", "-r", "-d", str(backup)], check=False)
    restore_text = ((restored.stdout or "") + (restored.stderr or "")).strip()
    say("  恢复：" + (restore_text or "无输出"))
    return False


def enable_home_key(serial, profile):
    component = profile["launcher"]["package"] + "/" + profile["launcher"]["package"] + ".HomeKeyService"
    current = shell(serial, "settings get secure enabled_accessibility_services")
    parts = [part for part in current.split(":") if part and part != "null"]
    if component not in parts:
        parts.append(component)
    apply_device_command(serial, "settings put secure enabled_accessibility_services " + ":".join(parts), dry_run=False)
    apply_device_command(serial, "settings put secure accessibility_enabled 0", dry_run=False)
    apply_device_command(serial, "settings put secure accessibility_enabled 1", dry_run=False)
    say("主页键已交给客厅净桌面。")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except PlanError as exc:
        say(str(exc))
        sys.exit(2)
    except RuntimeError as exc:
        say(str(exc))
        sys.exit(1)
