# Agent quick path

[简体中文](agent-quickstart.md) | English

For AI agents on a computer: follow these fixed steps to install Mika and connect the phone, without weighing options. Full details: [Install](computer-install.en.md) and [CLI](cli.en.md).

## 1. Resolve the latest release tag

```sh
curl -s https://api.github.com/repos/wangzan101/Mika/releases/latest   # read tag_name
```

```powershell
(Invoke-RestMethod https://api.github.com/repos/wangzan101/Mika/releases/latest).tag_name
```

Below, `<tag>` is that value (e.g. `v1.2.3`) and `<ver>` is the version without the `v`.

## 2. CLI or Skill

- Agent supports skills (e.g. Claude Code): **install the Skill by default**; it bundles the CLI script.
- Otherwise install the CLI: `pipx install 'git+https://github.com/wangzan101/Mika.git@<tag>#subdirectory=tools/mika'`.

## 3. Install the Skill

Claude Code's skills directory is `~/.claude/skills/mika`; for other agents use the skills directory in that agent's documentation.

POSIX:

```sh
curl -L -o mika.zip https://github.com/wangzan101/Mika/archive/refs/tags/<tag>.zip
unzip -q mika.zip -d mika-src
mkdir -p ~/.claude/skills && cp -R mika-src/Mika-<ver>/tools/mika ~/.claude/skills/mika
python3 ~/.claude/skills/mika/scripts/mika_cli.py --version
```

Windows PowerShell:

```powershell
Invoke-WebRequest https://github.com/wangzan101/Mika/archive/refs/tags/<tag>.zip -OutFile mika.zip
Expand-Archive mika.zip -DestinationPath mika-src
New-Item -ItemType Directory -Force "$HOME\.claude\skills" | Out-Null
Copy-Item -Recurse mika-src\Mika-<ver>\tools\mika "$HOME\.claude\skills\mika"
py -3 "$HOME\.claude\skills\mika\scripts\mika_cli.py" --version
```

Interpreter: `py -3` on Windows, `python3` elsewhere. With only the Skill, `mika` below means `python3 <skills-directory>/mika/scripts/mika_cli.py` (`py -3 ...` on Windows).

## 4. Connection rule

1. Run `adb devices`. Exactly one device in state `device`: use USB.
   ```sh
   mika devices --json
   mika connect --device SERIAL --json
   ```
2. Use Wi-Fi / VPN only when the user gives a URL and a token file path (read the token from the file only; never show it):
   ```sh
   mika --url http://PHONE:8765 --token-file /path/to/token doctor --json
   ```
3. Neither: ask the user once; do not guess.

State `unauthorized`: ask the user to accept the USB debugging prompt on the phone. `offline` or locked phone: ask them to unlock and replug.

## Notes

- Do not submit phone tasks; never print or echo the token.
- A successful `connect` / `doctor` only proves connectivity, not that a task will succeed.
