# 客厅净桌面

给小米电视用的主页。全屏壁纸，左上角时钟，底下是固定的应用。没有推荐流，没有开机广告开关能被脚本关掉的那些广告位。不刷机，不解锁，不删除银河牌照、播放器、投屏、账号、设置和商店。

包名 `tv.keting.home`，当前版本 `1.2.5`。做法参照 [CleanYourTV](https://github.com/Gathin23/cleanyourtv)（MIT），这一份按国内小米电视重做。

把这个仓库交给 Workbuddy、Cursor 或 Claude Code 时，直接说：按这个 README 装到我家电视上。人只做下面「人要先做完的事」。代理从「代理按这个顺序做」开始，不要跳步。

## 人要先做完的事

下面几步只能在电视上用手点。做完告诉代理：调试已经打开，电视的 IPv4 是多少。其余命令不用人敲。

### 1. 确认电视系统

客厅净桌面要求 **Android 5.0 及以上**（安装包的 `minSdk` 是 21）。脚本在 **Android 6.0.1 的小米电视 MiTV4-ANSM0（国内版，状态栏是银河 GITV）** 上实际装过。

在电视上看版本：

1. 按遥控器 **菜单** 或 **设置**，打开系统设置。
2. 进入 **关于**。
3. 看 **Android 版本** 或 **系统版本**。显示 5.0、6.0、7、8、9 或更高就可以继续。低于 5.0 装不上，停在这里。

国内小米电视的关于页里，产品型号经常只写 `MiTV`。这不影响安装，不要因此去找固件或恢复出厂设置。

### 2. 确认电视的 IP

电脑和电视必须在同一个无线网，路由器不要开「访客网络」或「AP 隔离」。

在电视上：

1. 设置 → **网络** → 当前已连接的无线网 → **网络信息**（有的系统叫网络详情）。
2. 抄下 **IPv4**。样子是 `192.168.x.x` 或 `10.x.x.x`。
3. 不要用网上搜到的地址，也不要用本仓库示例里的地址。每家路由器分到的都不一样。

电脑上可以再对一下：Windows 打开命令提示符，运行 `ping 刚才抄下的地址`。有回复就说明电脑能找到电视。没有回复时，先看是不是同一个 Wi-Fi，不要继续装。

### 3. 打开开发者模式

1. 设置 → **关于**。
2. 把焦点移到 **产品型号**，没有这一项就移到 **系统版本** 或 **Android 版本**。
3. 连按遥控器 **确定** 7 次。
4. 屏幕出现「您已处于开发者模式」或「开发者选项已启用」才算成功。

### 4. 打开 ADB（网络调试）

1. 返回上一级，进入 **开发者选项**。有的系统把它放在设置 → 账号与安全，或设置 → 系统。
2. 打开 **USB 调试**。
3. 打开 **网络调试**。有的系统写成 **ADB 调试**、**网络 ADB** 或 **无线调试**。打开后若显示端口，一般是 `5555`。
4. 电脑第一次连接时，电视会弹出「允许 USB 调试吗」。选 **允许**，并勾上 **一律允许** 或 **始终允许**。没人点这一下，后面的命令都会失败。

网络调试经常在重启电视、关机或闲置之后自己关掉。代理说连不上 `5555` 时，回到这一步重新打开，并再点一次允许。

做完这四步就可以把剩下的交给代理。调试用完后，再把网络调试关掉。

## 代理按这个顺序做

在项目根目录执行。下面的 `电视IP` 换成上一节抄下来的 IPv4。不要使用仓库里出现过的任何示例地址，除非那就是人刚抄下来的地址。

开始前先确认：

- 人已经做完上一节，并且告诉了你电视的 IPv4。
- 当前目录是本仓库根目录，能看到 `tools/install.py`。不要在用户主目录里运行，那里没有这个文件。
- 不要恢复出厂设置，不要 `adb reboot`，不要关机，不要用网络唤醒把电视打开。
- 不要卸载或停用：银河 `com.gitvdemo.video`、直播 `com.gitv.dvb.live`、播放器、乐播投屏、小米账号、支付、应用商店、电视设置、输入源。名单写在 `profile/living-room.json` 的保留项里，脚本会拒绝停用它们。
- 不要把第三方视频 APK、电视截图、`launcher/local.properties`、`launcher/debug.keystore` 提交进 git。

### 0. 先看计划，不改电视

```powershell
python tools\install.py --demo
python tools\install.py --host 电视IP --dry-run
```

macOS 或 Linux 把 `python` 换成 `python3`，反斜杠换成斜杠。

`--demo` 不连电视。`--dry-run` 会连接电视并列出将要执行的命令，不写入。连接失败就停，让人重做第 4 步。

### 1. 准备电脑上的工具

需要 Python 3、`adb`、JDK 17、Android SDK（platform `android-34`，build-tools `34.0.0`）。

- Windows 上 `adb` 一般在 `%ANDROID_HOME%\platform-tools\adb.exe`。把这个目录加进 `PATH`，或在命令前写完整路径。
- 设置 `JAVA_HOME` 指向 JDK 17，设置 `ANDROID_HOME` 指向 SDK 根目录。
- 仓库不带编译结果。第一次编译会由 Gradle Wrapper 下载 Gradle 8.11.1，这是预期行为。依赖仓库优先走阿里云镜像，写在 `launcher/settings.gradle`。

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File tools\build_launcher.ps1
```

macOS 或 Linux：

```bash
export ANDROID_HOME="$HOME/android-sdk"
tools/build_launcher.sh
```

成功时当前目录出现 `dist/keting-home.apk`。签名用本机新生成的 `launcher/debug.keystore`，口令 `android`，别名 `androiddebugkey`。这个文件不要提交。同一台电视以后升级，必须继续用这一把钥匙，否则要先卸载旧的客厅净桌面再装。

### 2. 装上，先不动旧桌面

```powershell
python tools\install.py --host 电视IP
```

这一步会：

- `adb connect 电视IP:5555`
- 安装 `dist/keting-home.apk`
- 关掉开机广告开关（`boot_ad`、`boot_ad_switch_show`）
- 停用名单里允许停用的预装项
- 打开一个无障碍服务，用来识别主页键

它 **不会** 卸掉小米桌面。安卓 6 没有 `cmd package set-home-activity`，普通安装不能把主页键改过来。装完后，旧桌面的应用库里会出现一个叫 **客厅净桌面** 的图标，点它就能进新桌面。

三个视频应用没装时，脚本只打印商店里的搜索词，不会从网上抓 APK。人可以自己到小米应用商店搜索：

| 底栏 | 商店里搜 | 包名 |
| --- | --- | --- |
| 哔哩哔哩 | 云视听小电视 | `com.xiaodianshi.tv.yst` |
| 优酷 | 酷喵 | `com.cibn.tv` |
| 央视频 | 央视频 TV 版 | `com.newtv.cboxtv` |

已经下载了官方安装包的话，放到下面三个路径，空间够时脚本会装上：

```text
apks/bilibili.apk
apks/youku.apk
apks/yangshipin.apk
```

不要代人点应用里的「同意」或隐私弹窗。

### 3. 让人看屏幕，再交主页键

只有人看着电视、并且明确希望遥控器 **主页键** 回到客厅净桌面时，才运行：

```powershell
python tools\install.py --host 电视IP --handoff-home
```

这一步会先把 `com.mitv.tvhome` 拉到本机 `~/.keting-tv/电视IP-tvhome.apk`，再执行 `pm uninstall --user 0 com.mitv.tvhome`。卸的是当前用户的一份，不是刷掉系统分区。安卓 6 上这是主页键能换过来的办法。

交接时可能出现这几种情况，按这个处理：

- 命令打印 `Success`，接着按主页键，画面是雪山湖壁纸和「客厅净桌面」：成功。让人再从设置里按一次主页键确认。
- 卸掉之后 `adb` 变成 `offline`，或 `5555` 拒绝连接：不要重启电视。让人把 USB 调试和网络调试关一次再打开，并点允许。然后只运行 `adb connect 电视IP:5555`，用截图或 `dumpsys activity activities` 看前台是不是 `tv.keting.home`。是，就停。不是，立刻执行下一节的恢复。
- 画面停在开机标志、黑屏，或按主页键没有任何桌面：不要恢复出厂设置。执行下一节的恢复。

没有 `--handoff-home` 时，代理不得自己加这个参数。

### 4. 怎么算做完

人在电视上确认这几件事：

- 应用库里能看到「客厅净桌面」。
- 底栏图标和名字完整，选中时字和图标仍然看得清。
- 遥控器上键到上面一排，下键停在最下一排。
- 若执行过 `--handoff-home`：从任意应用按主页键，回到的是这张壁纸桌面。
- 若没执行：主页键仍是原来的小米桌面，这是预期结果，不是失败。

确认之后让人关掉开发者选项里的网络调试。

## 恢复

安装记录在 `~/.keting-tv/电视IP.json`。卸过小米桌面时，备份在 `~/.keting-tv/电视IP-tvhome.apk`。

```powershell
python tools\restore.py --host 电视IP
```

有备份时会先 `adb install -r -d` 装回小米桌面，再按记录重新启用被停用的组件。客厅净桌面还在应用列表里，不想留就在电视的应用设置里卸载。

恢复命令连不上电视时，先让人重新打开网络调试。不要在恢复完成之前重启或断电。

## 电视上会看到什么

- 壁纸铺满屏幕。第一次开机是「雪山湖」，之后每次真正的重新开机换下一张。顶部 **换壁纸** 会切到下一张，并改成固定当前这一张。
- 左上角是时钟、日期和问候。右上角是输入源、当前网络、设置、换壁纸。
- **设置** 短按打开小米系统设置。长按打开本桌面的设置：开机换一张或固定当前、壁纸选择、底栏固定哪些应用、动画减弱或完整。默认减弱。
- 底栏是固定的应用（最多 6 个），然后是输入源，最后是全部应用。默认希望固定哔哩哔哩、优酷、央视频，没安装的不会占位。
- 全部应用是一个盖在桌面上的网格，返回键关掉。
- 图标画成同样大小的圆角方块。

壁纸照片来自 Unsplash。

## 编译和测试

测试不需要电视：

```powershell
python -m unittest discover -s tools -t tools -v
```

编译见上面「准备电脑上的工具」。安装包不进 git。本机已经编译过、只想重装时：

```powershell
adb connect 电视IP:5555
adb -s 电视IP:5555 install -r -d dist\keting-home.apk
```

## 许可

代码使用 MIT，见 `LICENSE`。壁纸照片来自 Unsplash，按 Unsplash 许可使用。不要把别人的视频应用安装包放进这个仓库。
