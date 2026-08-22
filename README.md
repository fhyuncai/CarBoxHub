# CarBoxHub 0.1.5

面向 7862 Android 车盒的局域网管理与媒体桥接工具。

## 0.1.5 更新

- Web 管理端重做为响应式布局，优先适配手机，同时保留桌面双栏布局
- 上传、文件管理、媒体状态与系统设置重新设计，移动端操作更紧凑
- Android Launcher 图标资源补全：Legacy PNG + Adaptive Icon
- `android:icon` / `android:roundIcon` 统一指向 `@mipmap/ic_launcher`
- 系统设置、应用列表、桌面启动器应使用同一套 CarBoxHub 图标

## 车机端

- 首屏横屏单页布局
- 左上角齿轮进入独立配置页
- 地址只显示 `http://IP:端口`
- 6 位访问令牌验证码样式
- 二维码仍包含完整 Token，可直接进入管理面板

## GitHub Release 自动发布

- Pull Request：构建 unsigned Release APK 做 CI 验证
- main：读取 GitHub Actions Secrets，构建固定签名 APK，并自动发布 GitHub Release

需要以下仓库 Secrets：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

## 构建参数

- applicationId: `com.carboxhub.app`
- minSdk: 23
- targetSdk: 28
- compileSdk: 34
- versionName: `0.1.5`
- versionCode: `6`
