#!/usr/bin/env python3
"""按安装时留下的记录，把客厅的小米电视恢复成原来的主页。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from install import adb_base, connect, run, say, shell
from plan import restore_commands


def stock_home_backup(host, directory=None):
    """handoff 卸掉当前用户的小米桌面后，pm enable 装不回来，只能重装这份备份。"""
    root = Path(directory) if directory else Path.home() / ".keting-tv"
    path = root / f"{host}-tvhome.apk"
    if path.is_file() and path.stat().st_size >= 100000:
        return path
    return None


def main(argv=None):
    parser = argparse.ArgumentParser(description="恢复小米电视原来的主页和组件")
    parser.add_argument("--host", default="", help="电视的 IPv4，必须和安装时用的一样。")
    parser.add_argument("--port", type=int, default=5555)
    parser.add_argument("--backup", default="")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)
    if not args.host:
        say("缺少电视地址。运行：python tools/restore.py --host 电视的IPv4")
        return 2

    path = Path(args.backup) if args.backup else Path.home() / ".keting-tv" / f"{args.host}.json"
    if not path.exists():
        say(f"没有找到 {path}。这台电脑上还没有这台电视的安装记录，没法按记录恢复。")
        say("可以在电视的 设置 → 应用 里，把停用的应用重新启用。")
        return 2

    backup = json.loads(path.read_text(encoding="utf-8"))
    commands = restore_commands(backup)
    apk = stock_home_backup(args.host)
    say(f"按 {path} 恢复 {len(commands)} 条。客厅净桌面还会留在电视上，不想要就在应用列表里卸载。")
    if apk:
        say(f"另外重装备份的小米桌面：{apk}")
    if args.dry_run:
        if apk:
            say(f"  adb install -r -d {apk}")
        for command in commands:
            say("  " + command)
        return 0

    serial = connect(args.host, args.port)
    if apk:
        completed = run(adb_base(serial) + ["install", "-r", "-d", str(apk)], check=False)
        text = ((completed.stdout or "") + (completed.stderr or "")).strip()
        say("  重装小米桌面：" + (text or "无输出"))
    for command in commands:
        say("  " + command)
        try:
            shell(serial, command)
        except RuntimeError as exc:
            say(f"  跳过：{exc}")
    say("恢复命令已经跑完。按主页键看是不是回到原来的小米桌面。")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as exc:
        say(str(exc))
        sys.exit(1)
