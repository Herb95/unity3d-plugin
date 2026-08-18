# Jenkins Unity3d / 团结引擎 构建插件

[English](README.markdown) | 中文

一个 Jenkins 插件，新增 **"Invoke Unity3d Editor"** 自由风格（Freestyle）构建步骤，用于在 Jenkins 里驱动
**Unity** 或 **团结引擎（Tuanjie）** 编辑器进行命令行构建。

相比直接用 shell 调用编辑器，本插件的核心价值在于：Unity / 团结会把控制台输出写到单独的
`Editor.log` 文件，而不是标准输出。本插件会把该日志文件**实时尾随（tail）进 Jenkins 构建控制台**，
即使构建运行在远程 agent 上也能正常显示。

---

## 功能特性

- 一行命令行参数即可调用编辑器，`-projectPath` 会在未显式指定时自动注入。
- `Editor.log` 实时回传到 Jenkins 控制台（支持控制器 / 远程 agent）。
- 退出码处理：`0` 成功；命中自定义"不稳定返回码"列表则为 UNSTABLE；其余为失败。
- **自动识别 Unity 或 团结引擎**，并据此定位可执行文件与日志路径（见下）。

## 团结引擎（Tuanjie）支持

无需在界面手动选择引擎类型：插件会根据安装目录**自动探测**是 Unity 还是团结。

> 说明：团结的安装目录下会同时存在 `Tuanjie.exe` 和一个内容相同的 `Unity.exe`（用于兼容调用
> `Unity.exe` 的工具）。因此插件**优先探测 `Tuanjie.exe`**——只要它存在就判定为团结；
> 纯 Unity 目录没有 `Tuanjie.exe`，自然落到 Unity 分支，对 Unity 用户完全无影响。

探测到的引擎类型会同时决定"启动哪个可执行文件"和"尾随哪个 `Editor.log`"：

| 平台 | 可执行文件（相对 `home`） | 默认 `Editor.log` 位置 |
|---|---|---|
| Windows | `Editor\Unity.exe` 或 `Editor\Tuanjie.exe` | `%LOCALAPPDATA%\Unity\Editor\Editor.log` 或 `%LOCALAPPDATA%\Tuanjie\Editor\Editor.log` |
| macOS | `Contents/MacOS/Unity` 或 `Contents/MacOS/Tuanjie` | `~/Library/Logs/Unity/Editor.log` 或 `~/Library/Logs/Tuanjie/Editor.log` |
| Linux | `Editor/Unity` 或 `Editor/Tuanjie` | `~/.config/unity3d/Editor.log` 或 `~/.config/tuanjie/Editor.log` |

> 在命令行参数里显式加 `-logFile <路径>` 可覆盖上述所有默认日志位置。

---

## 环境要求

- Jenkins **2.479.3** 或更高（Jakarta EE 9）。
- 构建本插件需要 **JDK 17–21**（Maven 需运行在 `JAVA_HOME` 指向的 JDK 上）。
  ⚠️ **不要用 JDK 22+**（如 JDK 25）来构建：父 POM 的 `license-maven-plugin` 和
  `spotbugs-maven-plugin` 读不了 Java 22+ 的字节码，会报 `Unsupported class file major version ...`。

## 从源码构建

如果系统默认 `JAVA_HOME` 已经是 JDK 17–21，直接：

```bash
mvn verify
```

如果默认 JDK 太新（例如 JDK 25），用仓库自带的 wrapper——它会从 **git 忽略的** `.mvn/java-home`
（机器本地路径，需自行创建）或环境变量 `UNITY3D_JAVA_HOME` 读取 JDK，再调用 Maven：

```bash
./mvn21 verify        # Git Bash
mvn21.cmd verify      :: cmd / PowerShell
```

构建产物：

```
target/unity3d-plugin.hpi
```

常用命令：

```bash
mvn spotless:apply   # 提交前必须格式化（spotless 检查不通过会导致构建失败）
mvn test             # 运行单元 + 集成测试
mvn hpi:run          # 本地起一个加载了本插件的开发用 Jenkins：http://localhost:8080/jenkins
```

## 安装到 Jenkins

二选一：

1. **界面上传**：`Manage Jenkins` → `Plugins` → `Advanced settings` → `Deploy Plugin`，
   选择 `target/unity3d-plugin.hpi` 上传，然后重启 Jenkins。
2. **直接拷贝**：把 `unity3d-plugin.hpi` 复制到 `$JENKINS_HOME/plugins/`，重启 Jenkins。

## 配置使用

1. **全局工具配置**：`Manage Jenkins` → `Tools`（Global Tool Configuration）→ 找到 **Unity3d** →
   `Add Unity3d`：
   - **Name**：自定义，例如 `Tuanjie 2022.3` 或 `Unity 2022.3`。
   - **Home**：编辑器**某个具体版本的根目录**（即包含 `Editor\Tuanjie.exe` 或 `Editor\Unity.exe`
     的那一级），例如 `E:\Program Files\Tuanjie\Hub\Editor\2022.3.48t2`。
     - 注意选**具体编辑器版本**的目录，不是 Unity Hub / 团结 Hub 应用本身。本插件只启动已安装的
       编辑器，不负责从 Hub 发现版本或安装编辑器与模块。
   - 插件会自动探测该目录是 Unity 还是团结，并校验可执行文件是否存在。
2. **任务配置**：在 Freestyle 任务中添加构建步骤 **"Invoke Unity3d Editor"**，在
   **Unity3d installation name** 下拉中选择上一步配置的安装，按需填写命令行参数。

> 构建在哪个节点跑，就在哪个节点配置对应的安装目录（可用节点覆盖 / 环境变量）。

## 注意事项

- **仅支持 Freestyle**，不支持 Pipeline（`Unity3dBuilder` 继承自 `Builder`，未实现 `SimpleBuildStep`）。
- 同一台机器同一时刻只能跑一个编辑器构建。建议给构建节点**只配一个执行器（executor）**，
  避免并发构建互相干扰。
- 集成测试（`IntegrationTests`）需要本机真实安装了编辑器，否则会**跳过**——所以一次"全绿"的
  测试运行可能实际没执行这些用例。

## 版本

当前版本 **1.4**（本仓库为自建自用 fork，含团结引擎支持，非官方发布版）。

## 许可

MIT
