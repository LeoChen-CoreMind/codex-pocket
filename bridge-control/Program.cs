using System.Diagnostics;
using System.Net;
using System.Net.Http.Headers;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Reflection;
using System.Security.Cryptography;
using System.Text.Json;

namespace CodexPocketBridge.Control;

internal sealed record HostProcess(int Id, string Name, string Title, string Path)
{
    public override string ToString() => $"{Name}  PID {Id}  {Title}";
}

internal sealed record BridgeConfig(
    int Port = 47831,
    int? HostProcessId = null,
    string? HostExecutable = null,
    string? FrpcExecutable = null,
    string? FrpcConfig = null
);

internal sealed class MainForm : Form
{
    private const int StableMobilePort = 47831;
    private const int LegacyMobilePort = 47816;
    private static readonly JsonSerializerOptions JsonOptions = new() {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = true,
    };

    private readonly Label statusValue = StatusLabel();
    private readonly Label codexValue = StatusLabel();
    private readonly Label frpStatusValue = StatusLabel();
    private readonly TextBox ipValue = ReadOnlyBox();
    private readonly NumericUpDown portValue = new() { Minimum = 1024, Maximum = 65535, Value = 47831, Width = 160 };
    private readonly TextBox endpointValue = ReadOnlyBox();
    private readonly TextBox tokenValue = ReadOnlyBox();
    private readonly TextBox bridgePidValue = ReadOnlyBox();
    private readonly ComboBox hostValue = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 620 };
    private readonly TextBox executableValue = new() { Dock = DockStyle.Fill, BorderStyle = BorderStyle.FixedSingle };
    private readonly TextBox monitoredProcessValue = ReadOnlyBox();
    private readonly TextBox editorValue = ReadOnlyBox(multiline: true);
    private readonly TextBox conversationValue = ReadOnlyBox();
    private readonly TextBox ftpValue = ReadOnlyBox();
    private readonly TextBox frpcExecutableValue = new() { Dock = DockStyle.Fill, BorderStyle = BorderStyle.FixedSingle };
    private readonly TextBox frpcConfigValue = new() { Dock = DockStyle.Fill, BorderStyle = BorderStyle.FixedSingle };
    private readonly Button startButton = new() { Text = "应用并启动", AutoSize = true };
    private readonly Button stopButton = new() { Text = "停止 Bridge", AutoSize = true };
    private readonly Button copyButton = new() { Text = "复制连接信息", AutoSize = true };
    private readonly Button regenerateButton = new() { Text = "重新生成密钥", AutoSize = true };
    private readonly Button refreshButton = new() { Text = "刷新进程", AutoSize = true };
    private readonly Button browseButton = new() { Text = "选择编辑器 EXE", AutoSize = true };
    private readonly Button firewallButton = new() { Text = "安装局域网防火墙规则", AutoSize = true };
    private readonly Button browseFrpcButton = new() { Text = "选择 frpc.exe", AutoSize = true };
    private readonly Button browseFrpcConfigButton = new() { Text = "选择 FRP 配置", AutoSize = true };
    private readonly Button startFrpButton = new() { Text = "启动 FRP", AutoSize = true };
    private readonly Button stopFrpButton = new() { Text = "停止 FRP", AutoSize = true };
    private readonly Button openStateButton = new() { Text = "打开状态目录", AutoSize = true };
    private readonly System.Windows.Forms.Timer statusTimer = new() { Interval = 2000 };
    private readonly HttpClient http = new() { Timeout = TimeSpan.FromSeconds(2) };
    private readonly RuntimeAssets runtime;
    private readonly string stateDirectory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "CodexMobileBridge"
    );
    private int? configuredHostProcessId;
    private bool serviceOnline;
    private bool refreshing;

    private string ConfigPath => Path.Combine(stateDirectory, "bridge-config.json");
    private string TokenPath => Path.Combine(stateDirectory, "bridge.token");
    private string BridgePidPath => Path.Combine(stateDirectory, "bridge.pid");
    private string FrpPidPath => Path.Combine(stateDirectory, "frpc.pid");
    private string LocalBaseUrl => $"http://127.0.0.1:{(int)portValue.Value}";

    public MainForm()
    {
        Directory.CreateDirectory(stateDirectory);
        runtime = RuntimeAssets.Extract(stateDirectory);
        Text = "Codex Pocket Bridge";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(900, 760);
        Size = new Size(1040, 900);
        Font = new Font("Segoe UI", 10);

        var content = new TableLayoutPanel {
            Dock = DockStyle.Top,
            Padding = new Padding(24),
            ColumnCount = 2,
            RowCount = 19,
            AutoSize = true,
        };
        content.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 150));
        content.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        var scroll = new Panel { Dock = DockStyle.Fill, AutoScroll = true };
        scroll.Controls.Add(content);
        Controls.Add(scroll);

        AddRow(content, 0, "Bridge 状态", statusValue);
        AddRow(content, 1, "Codex 状态", codexValue);
        AddRow(content, 2, "Bridge PID", bridgePidValue);
        AddRow(content, 3, "局域网 IP", ipValue);
        AddRow(content, 4, "监听端口", portValue);
        AddRow(content, 5, "监听地址", endpointValue);
        AddRow(content, 6, "访问密钥", tokenValue);
        AddRow(content, 7, "监听程序", hostValue);
        AddRow(content, 8, "程序路径", executableValue);
        AddRow(content, 9, "进程状态", monitoredProcessValue);
        AddRow(content, 10, "在线编辑器", editorValue, 108);
        AddRow(content, 11, "在线对话", conversationValue);
        AddRow(content, 12, "FTP 状态", ftpValue);
        AddRow(content, 13, "FRP 状态", frpStatusValue);
        AddRow(content, 14, "FRP 程序", frpcExecutableValue);
        AddRow(content, 15, "FRP 配置", frpcConfigValue);

        var hostActions = ActionRow(refreshButton, browseButton, new Label {
            Text = "支持 VS Code、Antigravity、Cursor、Windsurf 等 Code 系程序",
            AutoSize = true,
            Padding = new Padding(8, 7, 0, 0),
        });
        content.Controls.Add(hostActions, 1, 16);

        var bridgeActions = ActionRow(startButton, stopButton, regenerateButton, copyButton, firewallButton, openStateButton);
        bridgeActions.Padding = new Padding(0, 10, 0, 0);
        content.Controls.Add(bridgeActions, 1, 17);

        var frpActions = ActionRow(browseFrpcButton, browseFrpcConfigButton, startFrpButton, stopFrpButton);
        content.Controls.Add(frpActions, 1, 18);

        ipValue.Text = GetLanIp();
        LoadConfig();
        EnsureToken();
        RefreshProcesses();
        RefreshToken();
        RefreshEndpoint();

        startButton.Click += async (_, _) => await ApplyAndStart();
        stopButton.Click += async (_, _) => await StopBridge(showErrors: true);
        regenerateButton.Click += async (_, _) => await RegenerateToken();
        copyButton.Click += (_, _) => CopyConnectionInfo();
        refreshButton.Click += (_, _) => RefreshProcesses();
        browseButton.Click += (_, _) => BrowseExecutable();
        firewallButton.Click += async (_, _) => await InstallFirewallRule();
        openStateButton.Click += (_, _) => Process.Start(new ProcessStartInfo(stateDirectory) { UseShellExecute = true });
        browseFrpcButton.Click += (_, _) => BrowseFrpcExecutable();
        browseFrpcConfigButton.Click += (_, _) => BrowseFrpcConfig();
        startFrpButton.Click += async (_, _) => await StartFrp();
        stopFrpButton.Click += (_, _) => StopFrp();
        portValue.ValueChanged += (_, _) => RefreshEndpoint();
        hostValue.SelectedIndexChanged += (_, _) => UpdateSelectedHost();
        statusTimer.Tick += async (_, _) => await RefreshStatus();
        statusTimer.Start();
        Shown += async (_, _) => await RefreshStatus();
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing) {
            statusTimer.Dispose();
            http.Dispose();
        }
        base.Dispose(disposing);
    }

    private static Label StatusLabel() => new() {
        AutoSize = true,
        Font = new Font("Segoe UI", 11, FontStyle.Bold),
    };

    private static TextBox ReadOnlyBox(bool multiline = false) => new() {
        ReadOnly = true,
        Dock = DockStyle.Fill,
        BorderStyle = BorderStyle.FixedSingle,
        Multiline = multiline,
        ScrollBars = multiline ? ScrollBars.Vertical : ScrollBars.None,
    };

    private static FlowLayoutPanel ActionRow(params System.Windows.Forms.Control[] controls)
    {
        var panel = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoSize = true };
        panel.Controls.AddRange(controls);
        return panel;
    }

    private static void AddRow(
        TableLayoutPanel panel,
        int row,
        string label,
        System.Windows.Forms.Control control,
        int height = 48
    )
    {
        panel.RowStyles.Add(new RowStyle(SizeType.Absolute, height));
        panel.Controls.Add(new Label { Text = label, AutoSize = true, Anchor = AnchorStyles.Left }, 0, row);
        control.Anchor = AnchorStyles.Left | AnchorStyles.Right;
        panel.Controls.Add(control, 1, row);
    }

    private void LoadConfig()
    {
        if (!File.Exists(ConfigPath)) return;
        try {
            var config = JsonSerializer.Deserialize<BridgeConfig>(File.ReadAllText(ConfigPath), JsonOptions);
            if (config is null) return;
            if (config.Port is >= 1024 and <= 65535) portValue.Value = config.Port;
            configuredHostProcessId = config.HostProcessId;
            executableValue.Text = config.HostExecutable ?? "";
            frpcExecutableValue.Text = config.FrpcExecutable ?? "";
            frpcConfigValue.Text = config.FrpcConfig ?? "";
        } catch { }
    }

    private void SaveConfig()
    {
        Directory.CreateDirectory(stateDirectory);
        var selected = hostValue.SelectedItem as HostProcess;
        var config = new BridgeConfig(
            Port: (int)portValue.Value,
            HostProcessId: selected?.Id ?? configuredHostProcessId,
            HostExecutable: NullIfBlank(executableValue.Text),
            FrpcExecutable: NullIfBlank(frpcExecutableValue.Text),
            FrpcConfig: NullIfBlank(frpcConfigValue.Text)
        );
        File.WriteAllText(ConfigPath, JsonSerializer.Serialize(config, JsonOptions));
    }

    private void RefreshProcesses()
    {
        var selectedId = (hostValue.SelectedItem as HostProcess)?.Id ?? configuredHostProcessId;
        var selectedPath = executableValue.Text;
        var candidates = Process.GetProcesses().Select(ToHostProcess)
            .Where(item => item is not null)
            .Cast<HostProcess>()
            .OrderBy(item => item.Name)
            .ThenBy(item => item.Title)
            .ToList();
        hostValue.DataSource = candidates;
        hostValue.SelectedItem = candidates.FirstOrDefault(item => item.Id == selectedId)
            ?? candidates.FirstOrDefault(item => PathsEqual(item.Path, selectedPath));
        configuredHostProcessId = (hostValue.SelectedItem as HostProcess)?.Id;
        UpdateSelectedHost();
    }

    private static HostProcess? ToHostProcess(Process process)
    {
        try {
            var name = process.ProcessName;
            var title = process.MainWindowTitle;
            var path = process.MainModule?.FileName ?? "";
            var codeBased = name.Contains("code", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("antigravity", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("cursor", StringComparison.OrdinalIgnoreCase) ||
                name.Contains("windsurf", StringComparison.OrdinalIgnoreCase);
            return codeBased && !string.IsNullOrWhiteSpace(title)
                ? new HostProcess(process.Id, name, title, path)
                : null;
        } catch {
            return null;
        }
    }

    private void UpdateSelectedHost()
    {
        if (hostValue.SelectedItem is not HostProcess process) {
            monitoredProcessValue.Text = "未选择正在运行的兼容编辑器";
            return;
        }
        executableValue.Text = process.Path;
        configuredHostProcessId = process.Id;
        monitoredProcessValue.Text = $"在线 · {process.Name} · PID {process.Id} · {process.Title}";
    }

    private void BrowseExecutable()
    {
        using var dialog = new OpenFileDialog {
            Filter = "Windows 程序 (*.exe)|*.exe|所有文件 (*.*)|*.*",
            CheckFileExists = true,
            Title = "选择 VS Code 或兼容程序",
        };
        if (dialog.ShowDialog(this) != DialogResult.OK) return;
        executableValue.Text = dialog.FileName;
        RefreshProcesses();
    }

    private void BrowseFrpcExecutable()
    {
        using var dialog = new OpenFileDialog {
            Filter = "FRP 客户端 (frpc.exe)|frpc.exe|Windows 程序 (*.exe)|*.exe",
            CheckFileExists = true,
            Title = "选择 frpc.exe",
        };
        if (dialog.ShowDialog(this) == DialogResult.OK) frpcExecutableValue.Text = dialog.FileName;
    }

    private void BrowseFrpcConfig()
    {
        using var dialog = new OpenFileDialog {
            Filter = "FRP 配置 (*.toml;*.ini)|*.toml;*.ini|所有文件 (*.*)|*.*",
            CheckFileExists = true,
            Title = "选择 FRP 客户端配置",
        };
        if (dialog.ShowDialog(this) == DialogResult.OK) frpcConfigValue.Text = dialog.FileName;
    }

    private async Task ApplyAndStart()
    {
        SetBusy(true);
        try {
            SaveConfig();
            EnsureToken();
            await StopBridge(showErrors: false);
            var host = ResolveConfiguredHost();
            if (host is null) throw new InvalidOperationException("选择的兼容编辑器没有运行，请刷新进程后重新选择");

            var info = new ProcessStartInfo(runtime.NodePath) {
                WorkingDirectory = runtime.Directory,
                UseShellExecute = false,
                CreateNoWindow = true,
            };
            info.ArgumentList.Add(runtime.BridgePath);
            info.Environment["BRIDGE_HOST"] = "0.0.0.0";
            info.Environment["BRIDGE_PORT"] = ((int)portValue.Value).ToString();
            info.Environment["BRIDGE_API_TOKEN"] = File.ReadAllText(TokenPath).Trim();
            info.Environment["BRIDGE_HOST_PROCESS_ID"] = host.Id.ToString();
            info.Environment["BRIDGE_HOST_EXECUTABLE"] = host.Path;
            info.Environment["BRIDGE_JSON_WORKER"] = runtime.WorkerPath;
            info.Environment["BRIDGE_CODEX_PROXY"] = runtime.ProxyPath;
            info.Environment["NODE_NO_WARNINGS"] = "1";

            var process = Process.Start(info) ?? throw new InvalidOperationException("无法启动内置 Bridge 运行时");
            File.WriteAllText(BridgePidPath, process.Id.ToString());
            var ready = false;
            for (var attempt = 0; attempt < 40; attempt++) {
                await Task.Delay(500);
                if (process.HasExited) break;
                if (await IsHealthy()) {
                    ready = true;
                    break;
                }
            }
            if (!ready) {
                if (!process.HasExited) process.Kill(entireProcessTree: true);
                File.Delete(BridgePidPath);
                throw new InvalidOperationException("Bridge 未能在 20 秒内就绪");
            }
            await RefreshStatus();
        } catch (Exception error) {
            MessageBox.Show(this, error.Message, "Bridge 启动失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        } finally {
            SetBusy(false);
        }
    }

    private HostProcess? ResolveConfiguredHost()
    {
        var selected = hostValue.SelectedItem as HostProcess;
        if (selected is not null && IsProcessAlive(selected.Id, selected.Path)) return selected;
        var executable = executableValue.Text.Trim();
        if (!File.Exists(executable)) return null;
        return Process.GetProcesses().Select(ToHostProcess)
            .FirstOrDefault(item => item is not null && PathsEqual(item.Path, executable));
    }

    private async Task StopBridge(bool showErrors)
    {
        try {
            if (await IsHealthy()) {
                using var request = AuthorizedRequest(HttpMethod.Post, "/internal/shutdown");
                using var response = await http.SendAsync(request);
            }
            var pid = ReadPid(BridgePidPath);
            if (pid is not null) {
                for (var attempt = 0; attempt < 20 && ProcessExists(pid.Value); attempt++) await Task.Delay(250);
                if (ProcessExists(pid.Value) && IsExpectedProcess(pid.Value, runtime.NodePath)) {
                    Process.GetProcessById(pid.Value).Kill(entireProcessTree: true);
                }
            }
            if (File.Exists(BridgePidPath)) File.Delete(BridgePidPath);
            await RefreshStatus();
        } catch (Exception error) {
            if (showErrors) {
                MessageBox.Show(this, error.Message, "停止 Bridge 失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }

    private async Task RegenerateToken()
    {
        var wasOnline = serviceOnline;
        File.WriteAllText(TokenPath, Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant());
        RefreshToken();
        if (wasOnline) await ApplyAndStart();
    }

    private async Task StartFrp()
    {
        try {
            SaveConfig();
            var executable = frpcExecutableValue.Text.Trim();
            var config = frpcConfigValue.Text.Trim();
            if (!File.Exists(executable)) throw new FileNotFoundException("找不到 frpc.exe", executable);
            if (!File.Exists(config)) throw new FileNotFoundException("找不到 FRP 配置文件", config);
            StopFrp();
            var info = new ProcessStartInfo(executable) {
                WorkingDirectory = Path.GetDirectoryName(config) ?? stateDirectory,
                UseShellExecute = false,
                CreateNoWindow = true,
            };
            info.ArgumentList.Add("-c");
            info.ArgumentList.Add(config);
            var process = Process.Start(info) ?? throw new InvalidOperationException("无法启动 FRP 客户端");
            File.WriteAllText(FrpPidPath, process.Id.ToString());
            await Task.Delay(500);
            RefreshFrpStatus();
        } catch (Exception error) {
            MessageBox.Show(this, error.Message, "FRP 启动失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private void StopFrp()
    {
        var pid = ReadPid(FrpPidPath);
        var expected = frpcExecutableValue.Text.Trim();
        if (pid is not null && ProcessExists(pid.Value) && IsExpectedProcess(pid.Value, expected)) {
            Process.GetProcessById(pid.Value).Kill(entireProcessTree: true);
        }
        if (File.Exists(FrpPidPath)) File.Delete(FrpPidPath);
        RefreshFrpStatus();
    }

    private async Task InstallFirewallRule()
    {
        try {
            SaveConfig();
            var port = (int)portValue.Value;
            var ports = new[] { StableMobilePort, LegacyMobilePort, port }
                .Distinct()
                .Select(value => value.ToString());
            var portList = string.Join(',', ports);
            var arguments = $"advfirewall firewall add rule name=\"Codex Pocket Bridge\" dir=in action=allow protocol=TCP localport={portList} profile=private,public";
            using var process = Process.Start(new ProcessStartInfo("netsh.exe", arguments) {
                UseShellExecute = true,
                Verb = "runas",
                WindowStyle = ProcessWindowStyle.Hidden,
            }) ?? throw new InvalidOperationException("无法启动防火墙配置");
            await process.WaitForExitAsync();
            if (process.ExitCode != 0) throw new InvalidOperationException($"防火墙规则安装失败，退出码 {process.ExitCode}");
            MessageBox.Show(this, $"已允许专用和公用网络 TCP {portList}", "防火墙规则", MessageBoxButtons.OK, MessageBoxIcon.Information);
        } catch (Exception error) {
            MessageBox.Show(this, error.Message, "防火墙配置失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private async Task RefreshStatus()
    {
        if (refreshing) return;
        refreshing = true;
        try {
            ipValue.Text = GetLanIp();
            RefreshEndpoint();
            serviceOnline = await IsHealthy();
            if (!serviceOnline) {
                SetStatus(statusValue, "已停止", Color.Firebrick);
                codexValue.Text = "离线";
                bridgePidValue.Text = ReadPid(BridgePidPath)?.ToString() ?? "-";
                editorValue.Text = "没有可用的 Bridge 状态";
                conversationValue.Text = "0";
                ftpValue.Text = "已停止";
                RefreshFrpStatus();
                return;
            }

            SetStatus(statusValue, "正在运行", Color.SeaGreen);
            bridgePidValue.Text = ReadPid(BridgePidPath)?.ToString() ?? "外部进程";
            try {
                using var status = await GetJson("/api/status");
                codexValue.Text = StringProperty(status.RootElement, "bridgeState", "未知");
            } catch (Exception error) {
                codexValue.Text = $"状态不可用 · {error.Message}";
            }

            try {
                using var instancesDocument = await GetJson("/api/vscode/instances");
                var instances = ArrayPayload(instancesDocument.RootElement);
                var online = instances.EnumerateArray()
                    .Where(item => BoolProperty(item, "online"))
                    .ToList();
                var summaries = online.Select(item => {
                    var editor = StringProperty(item, "editorName", "Code");
                    var title = StringProperty(item, "windowTitle", "未命名窗口");
                    var pid = NumberProperty(item, "processId");
                    var bound = BoolProperty(item, "bound") ? " [已绑定]" : "";
                    return $"{editor} · PID {pid}{bound} · {title}";
                });
                editorValue.Text = online.Count == 0 ? "0 个在线编辑器" : string.Join(Environment.NewLine, summaries);
                var conversations = online.Sum(item =>
                    item.TryGetProperty("openThreads", out var threads) && threads.ValueKind == JsonValueKind.Array
                        ? threads.GetArrayLength()
                        : 0
                );
                conversationValue.Text = $"{conversations} 个打开的对话 · {online.Count} 个在线窗口";
            } catch (Exception error) {
                editorValue.Text = $"状态不可用 · {error.Message}";
                conversationValue.Text = "状态不可用";
            }

            try {
                using var ftp = await GetJson("/api/ftp/status");
                var ftpRunning = BoolProperty(ftp.RootElement, "running");
                ftpValue.Text = ftpRunning
                    ? $"运行中 · {StringProperty(ftp.RootElement, "host", "0.0.0.0")}:{NumberProperty(ftp.RootElement, "port")} · {StringProperty(ftp.RootElement, "root", "-")}"
                    : "已停止";
            } catch (Exception error) {
                ftpValue.Text = $"状态不可用 · {error.Message}";
            }
            RefreshFrpStatus();
        } catch (Exception error) {
            SetStatus(statusValue, "状态读取失败", Color.DarkOrange);
            codexValue.Text = error.Message;
            RefreshFrpStatus();
        } finally {
            refreshing = false;
        }
    }

    private async Task<bool> IsHealthy()
    {
        try {
            using var response = await http.GetAsync($"{LocalBaseUrl}/health");
            return response.IsSuccessStatusCode;
        } catch {
            return false;
        }
    }

    private async Task<JsonDocument> GetJson(string path)
    {
        using var request = AuthorizedRequest(HttpMethod.Get, path);
        using var response = await http.SendAsync(request);
        var content = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode) {
            var detail = ResponseError(content);
            throw new HttpRequestException($"{path}: {detail} ({(int)response.StatusCode})");
        }
        return JsonDocument.Parse(content);
    }

    private HttpRequestMessage AuthorizedRequest(HttpMethod method, string path)
    {
        var request = new HttpRequestMessage(method, $"{LocalBaseUrl}{path}");
        if (method == HttpMethod.Post) {
            request.Content = new StringContent("{}", System.Text.Encoding.UTF8, "application/json");
        }
        if (File.Exists(TokenPath)) {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", File.ReadAllText(TokenPath).Trim());
        }
        return request;
    }

    private void RefreshFrpStatus()
    {
        var pid = ReadPid(FrpPidPath);
        var executable = frpcExecutableValue.Text.Trim();
        if (pid is not null && ProcessExists(pid.Value) && IsExpectedProcess(pid.Value, executable)) {
            SetStatus(frpStatusValue, $"运行中 · PID {pid} · {executable}", Color.SeaGreen);
        } else if (string.IsNullOrWhiteSpace(executable)) {
            SetStatus(frpStatusValue, "未配置", Color.DimGray);
        } else {
            SetStatus(frpStatusValue, $"已停止 · {executable}", Color.Firebrick);
        }
    }

    private void EnsureToken()
    {
        if (!File.Exists(TokenPath) || string.IsNullOrWhiteSpace(File.ReadAllText(TokenPath))) {
            File.WriteAllText(TokenPath, Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant());
        }
    }

    private void RefreshToken() => tokenValue.Text = File.Exists(TokenPath)
        ? File.ReadAllText(TokenPath).Trim()
        : "启动后生成";

    private void RefreshEndpoint()
    {
        var internalPort = (int)portValue.Value;
        endpointValue.Text = internalPort == StableMobilePort
            ? $"http://{ipValue.Text}:{StableMobilePort} · 稳定手机入口"
            : $"http://{ipValue.Text}:{StableMobilePort} · 稳定手机入口 → 内部端口 {internalPort}";
    }

    private void CopyConnectionInfo()
    {
        Clipboard.SetText($"地址: http://{ipValue.Text}:{StableMobilePort}\r\n密钥: {tokenValue.Text}");
    }

    private void SetBusy(bool busy)
    {
        startButton.Enabled = !busy;
        stopButton.Enabled = !busy;
        regenerateButton.Enabled = !busy;
    }

    private static void SetStatus(Label label, string text, Color color)
    {
        label.Text = text;
        label.ForeColor = color;
    }

    private static string StringProperty(JsonElement element, string name, string fallback) =>
        element.ValueKind == JsonValueKind.Object &&
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? fallback
            : fallback;

    private static bool BoolProperty(JsonElement element, string name) =>
        element.ValueKind == JsonValueKind.Object &&
        element.TryGetProperty(name, out var value) &&
        value.ValueKind is JsonValueKind.True or JsonValueKind.False &&
        value.GetBoolean();

    private static string NumberProperty(JsonElement element, string name) =>
        element.ValueKind == JsonValueKind.Object &&
        element.TryGetProperty(name, out var value) &&
        value.ValueKind == JsonValueKind.Number &&
        value.TryGetInt32(out var number)
            ? number.ToString()
            : "?";

    private static JsonElement ArrayPayload(JsonElement root)
    {
        if (root.ValueKind == JsonValueKind.Array) return root;
        if (root.ValueKind == JsonValueKind.Object &&
            root.TryGetProperty("data", out var data) &&
            data.ValueKind == JsonValueKind.Array) return data;
        throw new InvalidDataException("接口未返回 data 数组");
    }

    private static string ResponseError(string content)
    {
        try {
            using var document = JsonDocument.Parse(content);
            return StringProperty(document.RootElement, "error", content);
        } catch {
            return string.IsNullOrWhiteSpace(content) ? "服务器未返回错误详情" : content;
        }
    }

    private static string? NullIfBlank(string value) => string.IsNullOrWhiteSpace(value) ? null : value.Trim();

    private static int? ReadPid(string path) => File.Exists(path) && int.TryParse(File.ReadAllText(path), out var pid) ? pid : null;

    private static bool ProcessExists(int pid)
    {
        try { return !Process.GetProcessById(pid).HasExited; } catch { return false; }
    }

    private static bool IsProcessAlive(int pid, string path) => ProcessExists(pid) && IsExpectedProcess(pid, path);

    private static bool IsExpectedProcess(int pid, string expectedPath)
    {
        if (string.IsNullOrWhiteSpace(expectedPath)) return false;
        try { return PathsEqual(Process.GetProcessById(pid).MainModule?.FileName, expectedPath); } catch { return false; }
    }

    private static bool PathsEqual(string? left, string? right) =>
        !string.IsNullOrWhiteSpace(left) && !string.IsNullOrWhiteSpace(right) &&
        string.Equals(Path.GetFullPath(left), Path.GetFullPath(right), StringComparison.OrdinalIgnoreCase);

    private static string GetLanIp() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(item => item.OperationalStatus == OperationalStatus.Up)
        .SelectMany(adapter => adapter.GetIPProperties().UnicastAddresses.Select(entry => (adapter, entry.Address)))
        .Where(item => item.Address.AddressFamily == AddressFamily.InterNetwork &&
            !IPAddress.IsLoopback(item.Address) && IsPrivateAddress(item.Address))
        .OrderByDescending(item => LanAddressPriority(item.adapter, item.Address))
        .Select(item => item.Address.ToString())
        .FirstOrDefault() ?? "127.0.0.1";

    private static int LanAddressPriority(NetworkInterface adapter, IPAddress address)
    {
        var name = $"{adapter.Name} {adapter.Description}";
        var virtualAdapter = name.Contains("virtual", StringComparison.OrdinalIgnoreCase) ||
            name.Contains("vmware", StringComparison.OrdinalIgnoreCase) ||
            name.Contains("hyper-v", StringComparison.OrdinalIgnoreCase) ||
            name.Contains("wsl", StringComparison.OrdinalIgnoreCase) ||
            name.Contains("tap", StringComparison.OrdinalIgnoreCase) ||
            name.Contains("tun", StringComparison.OrdinalIgnoreCase);
        var physicalAdapter = adapter.NetworkInterfaceType is NetworkInterfaceType.Wireless80211 or NetworkInterfaceType.Ethernet;
        var addressText = address.ToString();
        var addressScore = addressText.StartsWith("192.168.") ? 30 : addressText.StartsWith("10.") ? 20 : 10;
        return addressScore + (physicalAdapter ? 100 : 0) - (virtualAdapter ? 100 : 0);
    }

    private static bool IsPrivateAddress(IPAddress address) =>
        address.ToString().StartsWith("192.168.") || address.ToString().StartsWith("10.") || IsPrivate172(address);

    private static bool IsPrivate172(IPAddress address)
    {
        var bytes = address.GetAddressBytes();
        return bytes.Length == 4 && bytes[0] == 172 && bytes[1] is >= 16 and <= 31;
    }
}

internal sealed record RuntimeAssets(
    string Directory,
    string NodePath,
    string BridgePath,
    string WorkerPath,
    string ProxyPath
)
{
    private const string Prefix = "CodexPocketBridge.Runtime.";

    public static RuntimeAssets Extract(string stateDirectory)
    {
        var directory = Path.Combine(stateDirectory, "runtime");
        System.IO.Directory.CreateDirectory(directory);
        var assembly = Assembly.GetExecutingAssembly();
        ExtractResource(assembly, Prefix + "node.exe", Path.Combine(directory, "node.exe"));
        ExtractResource(assembly, Prefix + "bridge.cjs", Path.Combine(directory, "bridge.cjs"));
        ExtractResource(assembly, Prefix + "json-parser.worker.cjs", Path.Combine(directory, "json-parser.worker.cjs"));
        var proxyPath = ExtractVersionedResource(assembly, Prefix + "codex-proxy.exe", directory, "codex-proxy", ".exe");
        return new RuntimeAssets(
            directory,
            Path.Combine(directory, "node.exe"),
            Path.Combine(directory, "bridge.cjs"),
            Path.Combine(directory, "json-parser.worker.cjs"),
            proxyPath
        );
    }

    private static string ExtractVersionedResource(
        Assembly assembly,
        string resourceName,
        string directory,
        string baseName,
        string extension
    )
    {
        using var source = assembly.GetManifestResourceStream(resourceName)
            ?? throw new InvalidOperationException($"Missing static resource: {resourceName}");
        using var hash = SHA256.Create();
        var digest = Convert.ToHexString(hash.ComputeHash(source)).ToLowerInvariant()[..16];
        source.Position = 0;
        var destination = Path.Combine(directory, $"{baseName}-{digest}{extension}");
        if (File.Exists(destination) && SameContent(source, destination)) return destination;
        source.Position = 0;
        var temporary = destination + ".tmp";
        using (var output = new FileStream(temporary, FileMode.Create, FileAccess.Write, FileShare.None)) {
            source.CopyTo(output);
        }
        File.Move(temporary, destination, overwrite: true);
        return destination;
    }

    private static void ExtractResource(Assembly assembly, string resourceName, string destination)
    {
        using var source = assembly.GetManifestResourceStream(resourceName)
            ?? throw new InvalidOperationException($"静态资源缺失: {resourceName}");
        if (File.Exists(destination) && SameContent(source, destination)) return;
        source.Position = 0;
        var temporary = destination + ".tmp";
        using (var output = new FileStream(temporary, FileMode.Create, FileAccess.Write, FileShare.None)) {
            source.CopyTo(output);
        }
        File.Move(temporary, destination, overwrite: true);
    }

    private static bool SameContent(Stream source, string destination)
    {
        using var file = File.OpenRead(destination);
        if (source.Length != file.Length) return false;
        using var sourceHash = SHA256.Create();
        using var fileHash = SHA256.Create();
        var left = sourceHash.ComputeHash(source);
        source.Position = 0;
        var right = fileHash.ComputeHash(file);
        return left.AsSpan().SequenceEqual(right);
    }
}

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        try {
            Application.Run(new MainForm());
        } catch (Exception error) {
            MessageBox.Show(error.ToString(), "Codex Pocket Bridge 启动失败", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
