# CarBoxHub 0.1.1

面向 7862 Android 车盒的局域网管理与媒体桥接工具。

## 功能

- 局域网 Web 管理面板，默认端口 `8899`
- 车盒首页显示管理二维码，包含当前 `IP + 端口 + Token`
- 手机/电脑浏览器上传普通文件和 APK，支持下载、删除
- APK 上传并安装；上传 `com.carboxhub.app` 时识别为自身更新
- root 环境可优先使用 `pm install -r` 静默安装；无 root 使用系统 `PackageInstaller`
- 公共存储可用时保存到 `Download/CarBoxHub`
- 开机自动启动与更新后恢复服务
- 内置插件框架 `Plugin` / `PluginRegistry`
- 网易云媒体插件，目标包：
  - `com.netease.cloudmusic.iot`
  - `com.netease.cloudmusic`
- 通过 `NotificationListenerService + MediaSessionManager` 获取媒体信息
- 通过代理 `MediaSession` 同步歌名、歌手、专辑、播放状态，并转发播放/暂停、上一曲、下一曲和 Seek

## Android / 构建

- applicationId: `com.carboxhub.app`
- minSdk: 23
- targetSdk: 28
- compileSdk: 34
- Android Gradle Plugin: 8.2.2
- JDK: 17
- 当前版本：`0.1.1` / versionCode `2`

GitHub Actions 会执行 unsigned Release 构建。由于仓库是公开仓库，Release 私钥 `.jks` 和密码**不会提交到 GitHub**。正式交付 APK 使用项目固定 Release JKS 在受控环境完成签名，确保后续版本可覆盖自更新。

## 第一次安装

1. 打开 CarBoxHub。
2. 开启“通知使用权”。
3. 开启“安装未知应用”。
4. 如需保存到公共下载目录，授予存储权限。
5. 如果盒子有 root，可开启 root 静默安装并给 `su` 授权。
6. 手机与车盒连接同一局域网，扫描首页二维码进入 Web 管理面板。

## 关于 CarPlay / 原车机媒体信息

当前投送层使用标准 Android MediaSession 代理。若嘟嘟梁山车盒的 CarPlay 桥接服务会读取 Android 当前 MediaSession，媒体信息即可继续映射到原车机；若厂商桥接服务只接受私有 Binder、广播或 Provider，则后续只需增加一个厂商投送适配器，无需重写 Web 管理或插件框架。

## 安全

Web API 使用每台设备随机生成的 Token 鉴权。二维码会包含完整 Token，因此不要公开分享二维码截图。
