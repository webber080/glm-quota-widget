# GLM 额度小组件

显示智谱 GLM Coding Plan「5 小时限额」已用比例的桌面小组件。三个端共享同一官方接口，实时数据。

- **Android 桌面小组件**（荣耀 MagicOS / 各 Android 12+ 桌面通用）：三种尺寸 —— 1×1 圆环、2×1 横条、2×2 明细卡
- **macOS 菜单栏**：常驻菜单栏百分比 + 下拉明细
- **命令行**：单文件 Python 脚本

## 数据接口

```
GET https://open.bigmodel.cn/api/monitor/usage/quota/limit
Authorization: Bearer <你的智谱开放平台 API Key>
```

返回 `data.limits[]`：

| 字段 | 含义 |
|---|---|
| `unit=3, number=5` | 5 小时窗口 |
| `unit=6, number=1` | 每周窗口 |
| `percentage` | 已用百分比 |
| `nextResetTime` | 重置时间戳（毫秒） |
| `data.level` | 套餐档位 lite / pro / max |

> API Key 在智谱开放平台 bigmodel.cn 的「API Key」页获取。Key 只存本机（Android 存 SharedPreferences，macOS 存 `~/.config/glm-quota/key`）。

## Android 小组件

仓库自带 Gradle wrapper，clone 后直接构建：

```bash
cd android
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

安装后打开「GLM 额度」App 粘贴 Key，桌面长按 → 窗口小工具 → GLM 额度，三个尺寸任选。进度条颜色随用量变化：绿 <70% → 黄 70–90% → 红 ≥90%。30 分钟自动刷新，点组件进设置页。

> aarch64 Linux 构建注意：Google 不发布 arm64 的 build-tools，需要先装 [Commit451/android-arm-build-tools](https://github.com/Commit451/android-arm-build-tools) 并在 `gradle.properties` 设置 `android.aapt2FromMavenOverride`（仓库里的配置以 `/home/ubuntu/android-sdk` 为例，按自己路径改）。

## macOS 菜单栏

```bash
cd menubar
./build.sh
mkdir -p ~/.config/glm-quota && echo '你的key' > ~/.config/glm-quota/key
./build/GLMQuota &
```

菜单栏显示当前 5 小时窗口百分比，点击看两个窗口明细，⌘R 手动刷新，每 5 分钟自动刷新。开机自启：系统设置 → 登录项 → 添加 `build/GLMQuota`。

## 命令行

```bash
python3 reference_client.py <你的key>
# 输出示例:
# 14%
# 5小时: 已用 14% | 剩 86% | 0.0h 后重置
# 每周: 已用 17% | 剩 83% | 150.2h 后重置
# 套餐档位: lite
```

## 结构

```
android/            # Android 工程（Java, AppWidgetProvider ×3, minSdk 26）
  app/src/main/java/cn/guan/glmquota/
    BaseQuotaProvider.java   # 共享刷新逻辑
    QuotaWidgetRing.java     # 1×1 圆环
    QuotaWidgetBar.java      # 2×1 横条
    QuotaWidgetCard.java     # 2×2 明细卡
    QuotaApi.java            # 官方接口客户端
    SettingsActivity.java    # Key 配置页
menubar/            # macOS 菜单栏（Swift, 无第三方依赖）
reference_client.py # 命令行版
```

## License

MIT
