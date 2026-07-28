# Codex Pocket Mobile

这是 Codex Pocket 的 Kotlin Multiplatform 移动客户端，目前主要发布 Android 版本。完整介绍、架构和构建步骤见仓库根目录的 [`README.md`](../README.md)。

本目录基于 [LibreChat Mobile](https://github.com/garfiec/Librechat-Mobile) 的 MIT 许可源码开发。原始许可保留在 [`LICENSE`](LICENSE)，详细致谢与来源见根目录 [`NOTICE`](../NOTICE)。

Android 调试构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。
