# CarBoxHub 0.1.2

面向 7862 Android 车盒的局域网管理与媒体桥接工具。

## 0.1.2 更新

- 首页重做为极简样式，只显示名称、访问地址、6 位访问令牌、二维码
- 访问令牌改为 6 位数字，并以验证码样式展示
- 点击地址复制完整链接，点击令牌复制数字，点击二维码放大
- 长按首页标题 `CarBoxHub` 打开隐藏控制面板
- 新增 `design/app-icon.svg`，并接入 Android VectorDrawable 应用图标
- GitHub Actions 支持 tag 自动发布到 GitHub Releases

## GitHub Release 自动发布

普通 push / PR 会构建 unsigned Release APK 做 CI 验证；推送 `v*` 标签时会读取 GitHub Actions Secrets，构建固定签名 APK，并自动创建 GitHub Release。

需要配置以下仓库 Secrets：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

其中 `RELEASE_KEYSTORE_BASE64` 是固定 `CarBoxHub-release.jks` 的 Base64 内容。私钥和密码不要提交到公开仓库。

配置完成后，发布新版本只需推送标签，例如：

```bash
git tag v0.1.2
git push origin v0.1.2
```

Actions 会自动把 `CarBoxHub-v0.1.2.apk` 上传到 Releases。

## 首页交互

- 点击访问地址：复制完整 Web 地址
- 点击 6 位令牌：复制令牌
- 点击二维码：放大二维码
- 长按 `CarBoxHub`：进入隐藏控制面板

隐藏控制面板保留通知使用权、未知来源安装、公共下载目录、网易云媒体插件、root 静默安装、开机启动和重置令牌等功能。

## 构建参数

- applicationId: `com.carboxhub.app`
- minSdk: 23
- targetSdk: 28
- compileSdk: 34
- versionName: `0.1.2`
- versionCode: `3`
