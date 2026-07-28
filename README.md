# Codex Pocket

<p align="center">
  <img src="mobile/docs/brand/codex-pocket-icon.svg" width="128" alt="Codex Pocket 图标">
</p>

<p align="center"><strong>把电脑上的 Codex 会话装进口袋，离开工位也能继续查看、发送、审批和控制任务。</strong></p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green.svg" alt="MIT License"></a>
  <img src="https://img.shields.io/badge/platform-Windows%20%2B%20Android-1677ff" alt="Windows and Android">
  <img src="https://img.shields.io/badge/editor-VS%20Code%20%7C%20Cursor%20%7C%20Antigravity%20%7C%20Windsurf-555" alt="Supported editors">
</p>

Codex Pocket 是一套开源的 Codex 移动伴侣，由 **Android 客户端、Windows Bridge 和编辑器 Companion** 组成。它把电脑上正在运行的 Codex 会话安全地连接到手机，让你不用一直守在电脑前，也能看到任务进度、继续发送要求、处理审批、管理排队消息，并在需要时停止由手机发起的任务。

它不是另一个聊天壳，也不会把你的工作区托管到第三方中转服务器。Bridge 运行在你自己的 Windows 电脑上，手机通过局域网连接；会话状态、工作区和编辑器窗口仍由本机环境管理。

> 本项目是社区维护的非官方项目，与 OpenAI、Microsoft、Anysphere、Google 或 LibreChat 官方无隶属关系。Codex、VS Code、Cursor、Antigravity、Windsurf 和 LibreChat 是其各自所有者的商标。

## 为什么实用

- **任务跑很久，不必守着电脑**：在手机上查看 Codex 的思考、回复和工具调用，随时掌握执行进度。
- **临时想到补充要求，可以马上发送**：任务执行中可把消息加入队列，也可将队列消息转为当前回合的引导消息。
- **审批不再卡住工作流**：命令、文件修改、权限申请和计划确认可以直接在手机上处理。
- **多窗口不会认错会话**：按编辑器实例和会话标识在线窗口，同一会话在不同窗口打开时也能区分。
- **手机和电脑状态保持一致**：发送、停止、运行中、排队和完成状态由 Bridge 统一同步，APP 重启后可重新恢复。
- **AI 可以主动向手机提问**：通过 MCP 展示 Markdown、图片、选择项或自由文本输入，适合需要人工确认的自动化流程。

## 适合谁

- 经常让 Codex 执行编译、重构、检索或其他长时间任务的开发者。
- 同时使用 VS Code、Cursor、Antigravity、Windsurf 等多个 Code 系编辑器的人。
- 希望在局域网内自主管理连接、密钥和数据路径，不依赖公共聊天中转服务的人。
- 需要让 AI 在执行过程中把图片、选项和确认请求发送到手机的人。

## 界面预览

<p align="center">
  <img src="docs/screenshots/online-sessions.png" width="260" alt="Codex Pocket 在线会话列表">
  <img src="docs/screenshots/chat-control.png" width="260" alt="Codex Pocket 实时对话和消息控制">
  <img src="docs/screenshots/approval.png" width="260" alt="Codex Pocket 移动审批">
</p>

<p align="center">
  <img src="docs/screenshots/mcp-dialog.png" width="320" alt="Codex Pocket MCP 交互">
</p>

<p align="center">
  <img src="docs/screenshots/server-console.png" width="820" alt="Codex Pocket Windows Bridge 控制器">
</p>

图片会直接显示在本页。维护者只需按 [截图上传说明](docs/screenshots/README.md) 将对应文件上传到 `docs/screenshots/`，不需要再修改 README。

## 核心功能

| 功能 | 实际用途 |
| --- | --- |
| 在线会话 | 查看当前在哪个编辑器、窗口和工作区中打开的 Codex 会话 |
| 实时对话 | 按原始顺序显示思考、回复、工具调用、文件修改和最终结果 |
| 动态发送/停止 | 输入框有内容时显示发送按钮，清空后恢复为停止按钮 |
| 消息队列 | 当前任务运行时继续提交要求，Bridge 按顺序执行，不丢消息 |
| 消息引导 | 将队列中的要求追加到 Pocket 当前正在运行的回合 |
| 精确停止 | 只停止 Pocket 发起且回合标识匹配的任务，避免误停桌面独立任务 |
| 移动审批 | 在手机端允许一次、会话内允许或拒绝命令和权限请求 |
| 工作区文件 | 浏览当前绑定电脑的工作区文件，并将文件引用加入对话 |
| MCP 对话 | 向手机发送 Markdown、图片、选项和自由文本交互 |
| 对话提示词 | 为每个会话保存独立的 `developer_instructions` |
| Windows 控制器 | 管理 Bridge、连接密钥、防火墙、编辑器进程、FTP 和 FRP 状态 |

## 工作原理

```text
Android APP
    |  HTTP / SSE / Bearer Token
    v
Windows Bridge -------- MCP 客户端
    |                       |
    | Codex App Server      +-- Markdown / 图片 / 选项 / 文本请求
    |
Editor Companion
    |
    +-- VS Code / Cursor / Antigravity / Windsurf
```

1. **Editor Companion** 上报当前编辑器窗口、工作区和正在运行的会话。
2. **Windows Bridge** 连接 Codex App Server，统一管理事件流、审批、队列、引导和访问密钥。
3. **Android APP** 只连接你的 Bridge，并通过 Bearer Token 鉴权。
4. Bridge 是运行状态的权威来源，因此旧事件不会覆盖新回合，手机重连后也能恢复正确状态。

## 快速开始

### 环境要求

- Windows 10/11 x64
- Android 手机，手机和电脑位于同一个可信局域网
- Node.js 22 或更高版本
- .NET 10 SDK
- JDK 21 和 Android SDK
- 已安装并可正常使用 Codex 的 VS Code 或兼容编辑器

所有示例命令均在仓库根目录使用 Windows PowerShell 执行。

### 1. 获取源码和安装依赖

```powershell
git clone https://github.com/LeoChen-CoreMind/codex-pocket.git
Set-Location .\codex-pocket\bridge
npm.cmd ci
```

### 2. 安装 Editor Companion

```powershell
Set-Location ..\vscode-companion
.\install.ps1
```

安装脚本会为已存在的 VS Code、Cursor 和 Antigravity 扩展目录创建开发用目录联接。完成后重新加载每个编辑器窗口一次。打开 Codex 会话后，控制器和 APP 才能识别对应的在线窗口。

### 3. 构建 Windows Bridge 控制器

```powershell
Set-Location ..\bridge-control
.\build-static.ps1
```

产物位于：

```text
bridge-control/publish-static/CodexPocketBridge.exe
```

该程序已经内嵌 Node.js 和 Bridge bundle，运行时不需要单独打开终端。配置、密钥和运行状态保存在 `%LOCALAPPDATA%\CodexMobileBridge`，不会写进源码目录。

### 4. 构建并安装 Android APP

```powershell
Set-Location ..\mobile
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

调试 APK 使用 Android 标准调试签名。正式分发时，请通过 `keystore.properties` 或 `SIGNING_*` 环境变量配置自己的发布证书，严禁提交证书和密码。

### 5. 启动 Bridge

1. 先启动 VS Code、Cursor、Antigravity 或 Windsurf，并打开一个 Codex 会话。
2. 运行 `CodexPocketBridge.exe`，点击“刷新进程”。
3. 在“监听程序”中选择要绑定的编辑器；未自动识别时点击“选择编辑器 EXE”。
4. 首次使用可点击“安装局域网防火墙规则”，只开放项目所需端口。
5. 点击“应用并启动”，等待“Bridge 状态”显示为在线。
6. 点击“复制连接信息”。控制器会复制正确的局域网地址和访问密钥，不要手动填写 `127.0.0.1` 或猜测网卡地址。

手机稳定入口使用端口 `47831`。即使控制器内部监听端口调整，复制出来的连接信息仍应作为手机端的准确信息来源。

### 6. 连接手机

1. 打开 APP，在“连接到 Codex Pocket”页面粘贴控制器复制的地址，例如 `http://192.168.1.20:47831`。
2. 点击连接后进入密钥页面，粘贴“访问密钥”并确认。
3. 进入会话列表后，侧边栏“在线列表”会显示 Companion 已识别的编辑器窗口。
4. 打开会话即可查看实时输出、继续发送消息、处理审批或停止 Pocket 发起的任务。

地址和密钥必须来自同一台正在运行的 Bridge。重新生成密钥后，手机端需要使用新密钥重新连接。

## 消息队列和引导

当 Codex 正在运行时，Codex Pocket 会区分两种追加方式：

- **排队**：消息等待当前回合结束后，作为下一个回合自动发送。适合独立的新要求。
- **引导**：消息通过 `turn/steer` 加入当前回合。适合补充限制、纠正方向或追加当前任务所需的信息。

只有由 Pocket 发起且仍在运行的回合可以被引导或停止。桌面端独立发起的回合会同步状态，但手机不会冒险操作错误的任务。

输入框会根据内容自动切换按钮：有文字时显示发送，清空后显示停止。这样运行中的任务既能追加消息，也能快速暂停。

## MCP 手机交互

MCP 对话让支持 Model Context Protocol 的 AI 客户端主动向手机发起交互，适合“执行到关键步骤后等待用户确认”的自动化任务。

1. 在 APP 设置中进入“**MCP 对话**”。
2. 选择端口并开启服务。
3. 使用页面显示的第一个局域网地址；不要使用 `127.0.0.1`、虚拟网卡或 VPN 网卡地址。
4. 复制页面生成的 MCP 地址、Bearer Token 和提示词，配置到需要调用它的 MCP 客户端。
5. AI 发起请求后，手机可展示 Markdown、图片、单选/多选项或自由文本输入，并把结果返回给调用方。

MCP 地址和 Token 相当于访问凭据，不要发布到 issue、聊天记录或公开截图中。

## 常见问题

### 手机无法访问 Bridge

- 确认电脑和手机连接到同一局域网，且当前 Windows 网络类型为“专用网络”。
- 在控制器中重新点击“安装局域网防火墙规则”。
- 使用“复制连接信息”得到的地址，不要填写 `localhost` 或 `127.0.0.1`。
- 暂时断开 VPN、虚拟网卡或代理工具后重试，避免系统选错路由。
- 确认 `CodexPocketBridge.exe` 中 Bridge 状态为在线，并且手机访问的是端口 `47831`。

### APP 中没有在线会话

- 确认已经运行 `vscode-companion/install.ps1`。
- 安装后重新加载编辑器窗口。
- 确认编辑器中已经打开 Codex 会话，而不只是启动了编辑器。
- 在控制器中点击“刷新进程”，检查“在线编辑器”和“在线对话”。

### 地址正确但密钥无法通过

- 在控制器中点击“复制连接信息”，重新复制完整密钥。
- 如果点击过“重新生成密钥”，旧密钥会立即失效。
- 确认地址和密钥来自同一个 Bridge 实例。

### 消息只能排队，不能引导

- 引导只适用于 Pocket 发起的当前活动回合。
- 当前回合已结束、回合标识发生变化或任务由桌面独立发起时，消息会安全地保留在队列中。
- 刷新会话活动状态后再尝试，避免操作已经结束的旧回合。

### MCP 开启后仍无法访问

- 优先使用 APP 显示的第一个局域网 IPv4 地址。
- 确认端口未被其他程序占用，防火墙允许该端口。
- MCP 客户端必须携带页面生成的 Bearer Token。
- 不要把控制器 Bridge 地址误当成 MCP 地址，两者端口和路径可能不同。

## 项目结构

| 目录 | 职责 | 技术 |
| --- | --- | --- |
| `mobile/` | Android 客户端、聊天、审批、设置和 MCP 界面 | Kotlin Multiplatform、Jetpack Compose、Ktor、Koin、Room |
| `bridge/` | Codex App Server 适配、API、SSE、队列、MCP 和工作区服务 | TypeScript、Node.js、Fastify、Zod、WebSocket |
| `bridge-control/` | Windows 图形控制器和自包含单文件发布 | C#、.NET、Windows Forms |
| `vscode-companion/` | 编辑器窗口、工作区、标签和运行状态同步 | JavaScript、VS Code Extension API |

## 安全边界

- Bridge 对非回环地址监听时强制要求 Bearer Token。
- Companion 只连接本机 Bridge，并按 `instanceId + threadId` 定向窗口命令。
- 工作区文件访问受当前绑定编辑器窗口的工作区路径限制。
- 审批采用先到响应生效，重复或过期响应会被拒绝。
- 手机不会对桌面独立发起的回合执行进程级强杀。
- 本项目面向个人可信网络；未经 TLS、额外访问控制和审计，不应直接暴露到公网。

完整安全策略和漏洞报告方式见 [SECURITY.md](SECURITY.md)。

## 开发检查

```powershell
Set-Location .\bridge
npm.cmd run check

Set-Location ..\bridge-control
.\build-static.ps1

Set-Location ..\mobile
.\gradlew.bat :app:assembleDebug
```

GitHub Actions 配置示例位于 [`docs/build-workflow.yml.example`](docs/build-workflow.yml.example)。

## 技术栈

- Kotlin Multiplatform、Jetpack Compose、Compose Multiplatform
- Ktor Client、Koin、Room、DataStore、Kotlinx Serialization、Coil
- TypeScript、Node.js、Fastify、Zod、WebSocket、FTP server
- C#、.NET 10、Windows Forms
- VS Code Extension API、Codex App Server 协议、Model Context Protocol

## 致谢

- [OpenAI Codex](https://openai.com/codex/)：本项目连接和控制的 AI 编程环境。
- [LibreChat](https://github.com/danny-avila/LibreChat)：开放聊天生态和 API 设计。
- [LibreChat Mobile](https://github.com/garfiec/Librechat-Mobile)：移动端 UI 与 Kotlin Multiplatform 基础，本项目在其 MIT 许可下进行了大量适配。
- [Model Context Protocol](https://modelcontextprotocol.io/)：AI 与手机交互服务的开放协议。

第三方版权和来源说明见 [NOTICE](NOTICE)。参与贡献前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

Codex Pocket 以 [MIT License](LICENSE) 开源。`mobile/` 基于 LibreChat Mobile 的 MIT 许可代码，原始版权和许可声明已保留。
