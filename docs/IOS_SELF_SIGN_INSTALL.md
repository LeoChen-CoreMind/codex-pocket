# iOS 自签安装

GitHub Release 中的 `CodexPocket-iOS-unsigned-*.ipa` 是未签名安装包，不能直接在 iPhone 上安装。用户必须使用自己的 Apple ID 重新签名；Android 的 `chennb.jks` 证书不能用于 iOS。

> **特别推荐：[Sideloadly](https://sideloadly.io/)**  
> Windows 用户使用这个工具完成 IPA 签名和安装即可，不需要 Xcode。其他签名工具仅作为备选，也不建议使用无法正常获取 Apple `XcodeToken` 的旧版本工具。

## 安装前检查

1. 下载 IPA 和同一 Release 中的 `SHA256SUMS.txt`。
2. 在 Windows PowerShell 中计算 SHA-256：

   ```powershell
   (Get-FileHash .\CodexPocket-iOS-unsigned-2026.07.8.ipa -Algorithm SHA256).Hash.ToLower()
   ```

3. 确认结果与 `SHA256SUMS.txt` 中该 IPA 的值完全一致。

## Windows + Sideloadly

1. 从 [Sideloadly 官方网站](https://sideloadly.io/) 下载并安装最新版。
2. 从 Apple 官网安装非 Microsoft Store 版 iTunes 和 iCloud，并登录 iCloud。
3. 打开 Sideloadly，用数据线连接 iPhone；在手机上选择“信任此电脑”。
4. 把未签名 IPA 拖入 Sideloadly，选择已连接的 iPhone，输入用于自签的 Apple ID 后开始安装。
5. iOS 16 或更高版本需要在“设置 > 隐私与安全性 > 开发者模式”中启用开发者模式并重启。
6. 成功安装并点击一次 Codex Pocket 后，如系统提示未受信任，打开“设置 > 通用 > VPN 与设备管理”，选择该 Apple ID 对应的开发者 App 并信任。签名或安装失败时不会出现这个入口。
7. 回到桌面启动 Codex Pocket。

## Windows + AltStore

1. 从 Apple 官网安装非 Microsoft Store 版 iTunes 和 iCloud，然后安装 AltServer。
2. 用数据线连接 iPhone并信任电脑，通过 AltServer 把 AltStore 安装到手机。
3. 在 iPhone 上启用开发者模式，并在“VPN 与设备管理”中信任 Apple ID 对应的开发者 App。
4. 把 IPA 保存到“文件”，使用分享菜单选择 AltStore，完成签名和安装。

## 免费 Apple ID 限制

- 免费 Apple ID 签名通常只有 7 天有效期，到期前需要用同一工具重新签名安装。
- 免费账号可同时安装的自签 App 数量和可用 App ID 数量受 Apple 限制。
- GitHub Release 不包含 Apple ID、密码、2FA 验证码或 Apple 签名证书；这些信息只应在用户自己的签名工具中使用。
- Sideloadly 和 AltStore 是第三方工具，请从其官方站点下载并自行判断账号安全风险。
