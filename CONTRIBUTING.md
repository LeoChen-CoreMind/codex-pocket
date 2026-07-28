# 参与贡献

感谢你改进 Codex Pocket。提交前请先搜索已有 issue，并说明问题影响的组件、编辑器、APP 版本和复现条件。

## 开发约定

- 变更应保持在 `mobile/`、`bridge/`、`bridge-control/` 或 `vscode-companion/` 的既有边界内。
- Bridge 是在线状态、队列、审批和当前 turn 的权威来源，不要在 APP 中复制第二套最终状态。
- 所有停止和引导操作必须校验 `threadId + turnId`，旧事件不得覆盖新 turn。
- 消息内容必须保持协议原始顺序，不得按思考、工具和文本类型重新分组。
- 不得提交 Token、IP、用户名、绝对路径、运行日志、对话正文、签名文件或 `%LOCALAPPDATA%\CodexMobileBridge` 内容。
- 新增网络入口必须说明认证、工作区边界、请求大小限制和过期/重复响应行为。

## 本地检查

```powershell
Set-Location .\bridge
npm.cmd ci
npm.cmd run check
npm.cmd run build

Set-Location ..\mobile
.\gradlew.bat :app:assembleDebug

Set-Location ..\bridge-control
.\build-static.ps1
```

Companion 是无构建步骤的 JavaScript 扩展，可通过 `vscode-companion/install.ps1` 安装到开发环境。

## Pull Request

PR 描述应包含动机、行为变化、安全影响和验证结果。涉及 UI 的改动请附已脱敏截图；涉及竞态的改动请给出事件顺序和使用的 `threadId/turnId` 校验规则。
