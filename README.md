# CarBoxHub 0.1.3

面向 7862 Android 车盒的局域网管理与媒体桥接工具。

## 0.1.3 更新

- 首屏改为单屏自适应布局，参考车机互联助手风格，不再需要滚动
- 首屏固定展示名称、访问地址、6 位访问令牌、二维码和“配置”按钮
- 点击“配置”进入权限、安装方式、插件与启动设置页面
- 点击访问地址复制完整链接；点击令牌复制 6 位验证码；点击二维码放大
- 保留网易云媒体投送、APK 安装、自更新、root 静默安装与开机自启能力

## GitHub Release 自动发布

- Pull Request：构建 unsigned Release APK 做 CI 验证
- main：读取 GitHub Actions Secrets，构建固定签名 APK，并自动创建/更新对应版本的 GitHub Release

需要以下仓库 Secrets：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

私钥和密码不要提交到公开仓库。

## 首页交互

- 点击访问地址：复制完整 Web 地址
- 点击 6 位令牌：复制令牌
- 点击二维码：放大二维码
- 点击“配置”：进入权限、设置与插件页面

## 构建参数

- applicationId: `com.carboxhub.app`
- minSdk: 23
- targetSdk: 28
- compileSdk: 34
- versionName: `0.1.3`
- versionCode: `4`
