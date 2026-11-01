# Agent 快速路径

简体中文 | [English](agent-quickstart.en.md)

给电脑上的 AI Agent：按下面的固定步骤安装 Mika 并连接手机，不用自己权衡方案。完整说明见[安装](computer-install.md)和[CLI](cli.md)。

## 1. 解析最新版本 tag

```sh
curl -s https://api.github.com/repos/wangzan101/Mika/releases/latest   # 取 tag_name
```

```powershell
(Invoke-RestMethod https://api.github.com/repos/wangzan101/Mika/releases/latest).tag_name
```

下文 `<tag>` 即该值（如 `v1.2.3`），`<ver>` 是去掉 `v` 的版本号。

## 2. 选 CLI 还是 Skill

- Agent 支持 Skill（如 Claude Code）：**默认装 Skill**，它自带 CLI 脚本。
- 否则装 CLI：`pipx install 'git+https://github.com/wangzan101/Mika.git@<tag>#subdirectory=tools/mika'`。

## 3. 安装 Skill

Claude Code 的技能目录是 `~/.claude/skills/mika`；其他 Agent 用它文档里的技能目录。

POSIX：

```sh
curl -L -o mika.zip https://github.com/wangzan101/Mika/archive/refs/tags/<tag>.zip
unzip -q mika.zip -d mika-src
mkdir -p ~/.claude/skills && cp -R mika-src/Mika-<ver>/tools/mika ~/.claude/skills/mika
python3 ~/.claude/skills/mika/scripts/mika_cli.py --version
```

Windows PowerShell：

```powershell
Invoke-WebRequest https://github.com/wangzan101/Mika/archive/refs/tags/<tag>.zip -OutFile mika.zip
Expand-Archive mika.zip -DestinationPath mika-src
New-Item -ItemType Directory -Force "$HOME\.claude\skills" | Out-Null
Copy-Item -Recurse mika-src\Mika-<ver>\tools\mika "$HOME\.claude\skills\mika"
py -3 "$HOME\.claude\skills\mika\scripts\mika_cli.py" --version
```

解释器：Windows 用 `py -3`，其他系统用 `python3`。下文 `mika` 在只装 Skill 时即 `python3 <技能目录>/mika/scripts/mika_cli.py`（Windows `py -3 ...`）。

## 4. 连接规则

1. 运行 `adb devices`。恰好一台状态为 `device`：走 USB。
   ```sh
   mika devices --json
   mika connect --device SERIAL --json
   ```
2. 只有用户给了 URL 和令牌文件路径时才走 Wi-Fi / VPN（令牌只从文件读，不显示内容）：
   ```sh
   mika --url http://PHONE:8765 --token-file /path/to/token doctor --json
   ```
3. 两者都没有：向用户问一次，不要猜。

状态 `unauthorized`：让用户在手机上点允许 USB 调试。`offline` 或手机锁屏：让用户解锁、重插。

## 注意

- 不提交手机任务，不打印、不回贴令牌。
- `connect` / `doctor` 成功只说明连通，不代表任务能成功。
