# Codex Pocket

<p align="center">
  <img src="mobile/docs/brand/codex-pocket-icon.svg" width="128" alt="Codex Pocket icon">
</p>

<p align="center"><strong>把桌面 Codex 会话装进口袋。</strong></p>

Codex Pocket 是一套面向本地开发环境的开源移动伴侣。它把 VS Code、Cursor、Antigravity、Windsurf 等兼容编辑器中的 Codex 会话，通过用户自己控制的局域网 Bridge 安全地连接到 Android 手机。你可以查看在线窗口、继续对话、排队或引导消息、处理审批、精确停止 Pocket 发起的任务，并让 AI 通过 MCP 向手机发起 Markdown、图片和选项交互。

> 本项目是社区维护的非官方项目，与 OpenAI、Microsoft、Anysphere、Google 或 LibreChat 官方无隶属关系。Codex、VS Code、Cursor、Antigravity、Windsurf 和 LibreChat 是其各自所有者的商标。

## 它解决什么问题

- Codex 任务运行时间较长时，用户必须留在电脑前查看进度和批准操作。
- 多个编辑器窗口同时打开时，很难从移动端确认对话实际属于哪个窗口和工作区。
- 桌面端开始、停止或完成任务后，普通移动客户端容易出现发送键、停止键和运行状态不同步。
- 运行中追加内容需要区分“下一个 turn 排队”和“当前 turn 引导”，且必须避免旧 turn 事件影响新任务。
- AI 需要向用户展示图片、Markdown、选择项并等待确认时，缺少一个可控的手机交互通道。

Codex Pocket 将窗口绑定、在线标签、thread/turn 状态、审批与消息队列集中在本机 Bridge 中处理。手机不是最终状态来源，因此 APP 重启、Bridge 重启或编辑器切换后仍能恢复权威状态。

## 主要功能

- **在线会话**：按 `instanceId + threadId` 展示实际打开的 Codex 标签，同一 thread 在不同窗口打开时分别显示。
- **兼容多编辑器**：Companion 可安装到 VS Code、Cursor、Antigravity 和其他 Code 系编辑器。
- **双端实时控制**：Pocket 发起的 turn 可从手机或对应编辑器窗口精确停止；桌面独立任务只同步状态。
- **排队与引导**：运行中发送默认进入 Bridge FIFO 队列，队列项可在条件允许时转为 `turn/steer`。
- **有序消息时间线**：思考、文本、工具调用和最终回答按原始事件顺序交错显示，不按类型重排。
- **移动审批**：命令、文件和权限审批支持允许一次、本次会话允许和拒绝，前后台均可处理。
- **工作区文件**：浏览绑定电脑的当前工作区文件并选择到对话，而不是读取手机本地文件。
- **MCP 对话**：提供受 Token 保护的 Streamable HTTP MCP 服务，支持 Markdown、图片、选项和自由文本。
- **Windows 控制器**：查看服务、Codex、编辑器、会话、FTP 和 FRP 状态，并发布为自包含单文件。
- **对话专属提示词**：每个 thread 可保存独立提示词并作为 `developer_instructions` 发送。

## 架构

```text
Android APP
    |  HTTP / SSE / Bearer Token
    v
Bridge (Fastify + Codex App Server) ---- MCP clients
    |                    |
    | local control      +---- workspace files / approvals / queue
    v
Editor Companion
    |
    +---- VS Code / Cursor / Antigravity / Windsurf windows
```

| 目录 | 职责 | 技术 |
| --- | --- | --- |
| `mobile/` | Android 客户端、聊天、审批和设置界面 | Kotlin Multiplatform、Jetpack Compose、Ktor、Koin、Room、DataStore |
| `bridge/` | Codex App Server 适配、API、SSE、队列、MCP 和工作区服务 | TypeScript、Node.js、Fastify、Zod、WebSocket |
| `bridge-control/` | Windows 图形控制器和静态单文件发布 | C#、.NET、Windows Forms |
| `vscode-companion/` | 编辑器窗口、标签和运行状态同步 | JavaScript、VS Code Extension API |

## 软件截图

真实截图请放到 [`docs/screenshots/`](docs/screenshots/README.md)，使用以下文件名：

| 文件名 | 建议内容 |
| --- | --- |
| `online-sessions.png` | 多编辑器、多窗口在线对话列表 |
| `chat-control.png` | 对话、工具调用、排队/引导和实时停止 |
| `approval.png` | 对话内联计划确认或权限审批 |
| `mcp-dialog.png` | MCP Markdown、图片与选择项交互 |
| `server-console.png` | Windows Bridge 状态控制器 |

截图上传完成后，按截图目录说明中的片段启用 README 图集。请先裁掉 Token、用户名、绝对路径、IP、私有仓库名和对话隐私信息。

## 快速开始

### 环境要求

- Windows 10/11 x64
- Node.js 22 或更高版本
- .NET 10 SDK（构建 Windows 控制器）
- JDK 21 及 Android SDK（构建 Android APP）
- 已安装 Codex 扩展的 VS Code 或兼容编辑器
- 手机与电脑位于可信局域网，或自行配置受保护的隧道

### 1. 构建 Bridge

```powershell
Set-Location .\bridge
npm.cmd ci
npm.cmd run check
npm.cmd run build
```

开发模式可通过 `npm.cmd run dev` 启动。对局域网监听时必须设置不可预测的 `BRIDGE_API_TOKEN`；未设置 Token 时 Bridge 只允许回环地址。

### 2. 安装 Companion

```powershell
Set-Location .\vscode-companion
.\install.ps1
```

脚本会为已存在的 VS Code、Cursor 和 Antigravity 扩展目录创建开发用目录联接。安装后需要重新加载各编辑器窗口一次。

### 3. 构建 Windows 静态控制器

```powershell
Set-Location .\bridge-control
.\build-static.ps1
```

输出位于 `bridge-control/publish-static/CodexPocketBridge.exe`。该文件内嵌 Node.js 和 Bridge bundle，运行状态与密钥保存在 `%LOCALAPPDATA%\CodexMobileBridge`，不会写入源码目录。

### 4. 构建 Android APP

```powershell
Set-Location .\mobile
.\gradlew.bat :app:assembleDebug
```

APK 输出位于 `mobile/app/build/outputs/apk/debug/app-debug.apk`。调试构建使用 Android 标准调试签名；正式分发请通过 `keystore.properties` 或 `SIGNING_*` 环境变量提供自己的发布证书，严禁提交证书和密码。

### 5. 连接手机

启动 Windows 控制器，选择当前正在运行的兼容编辑器，设置端口并生成密钥。APP 中填写控制器显示的局域网 URL 和 Token。Windows 防火墙只开放所需端口，并确保当前网络为可信专用网络。

## MCP 对话

在 APP 设置页进入“**MCP 对话**”，选择端口并开启服务。界面会显示局域网 URL、鉴权头以及可复制的提示词。MCP 客户端必须使用页面生成的 Bearer Token；不要把配置截图、Token 或 MCP URL 发布到 issue。

## 安全边界

- Bridge 对非回环地址监听时强制要求 Bearer Token。
- Companion 只连接本机回环 Bridge，窗口命令按 `instanceId + threadId` 定向。
- 工作区文件访问受当前绑定窗口路径限制。
- 审批采用先到响应生效，重复或过期响应会被拒绝。
- 手机对桌面独立发起的 turn 不执行进程级强杀。
- 本项目面向个人可信网络，不应未经额外 TLS、访问控制和审计直接暴露到公网。

完整安全策略与漏洞报告方式见 [SECURITY.md](SECURITY.md)。

## 持续集成

Windows、Bridge 和 Android 的 GitHub Actions 配置示例位于 [`docs/build-workflow.yml.example`](docs/build-workflow.yml.example)。仓库维护者可在具备 GitHub `workflow` 权限时将其复制为 `.github/workflows/build.yml`。

## 技术栈

- Kotlin Multiplatform、Jetpack Compose、Compose Multiplatform
- Ktor Client、Koin、Room、DataStore、Kotlinx Serialization、Coil
- TypeScript、Node.js、Fastify、Zod、WebSocket、FTP server
- C#、.NET 10、Windows Forms
- VS Code Extension API、Codex App Server 协议、Model Context Protocol

## 致谢

感谢以下项目和社区提供的基础能力：

- [OpenAI Codex](https://openai.com/codex/)：本项目连接和控制的 AI 编程环境。
- [LibreChat](https://github.com/danny-avila/LibreChat)：开放聊天生态和 API 设计。
- [LibreChat Mobile](https://github.com/garfiec/Librechat-Mobile)：移动端 UI 与 Kotlin Multiplatform 基础，本项目在其 MIT 许可下进行了大量适配。
- [Model Context Protocol](https://modelcontextprotocol.io/)：AI 与手机交互服务的开放协议。
- Fastify、Ktor、Koin、Room、DataStore、Compose、Coil、Zod、WebSocket 及所有传递依赖的维护者。

第三方版权和来源说明见 [NOTICE](NOTICE)。

## 参与贡献

提交代码前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。Bug 报告必须删除 Token、IP、用户名、本机路径和私人对话内容。

## 许可证

Codex Pocket 以 [MIT License](LICENSE) 开源。`mobile/` 基于 LibreChat Mobile 的 MIT 许可代码，原始版权和许可声明已保留。
