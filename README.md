# Claude Code — JetBrains Rider Plugin

A JetBrains Rider plugin that embeds the [Claude Code](https://claude.ai/code) AI coding assistant directly into the IDE, reusing the VS Code extension's pre-built React webview via JCEF.

## Features

### Chat & Sessions
- **Embedded chat UI** — the full Claude Code React interface runs inside a Rider tool window
- **Claude CLI bridge** — spawns `claude` as a subprocess per conversation, communicating via the stream-JSON protocol
- **Session management** — lists, resumes and deletes past sessions stored in `~/.claude/projects/`
- **Tool window icon states** — idle / pending (blue dot while Claude is generating) / done (orange dot when finished)

### IDE Integration
- **Active file context sync** — whenever you switch tabs or change the selection, the webview automatically receives the active file path, selected text, line range and language; Claude always knows what you're looking at
- **Editor right-click menu** — right-click any file or selection to:
  - **Ask Claude** — open Claude with cursor in the input box
  - **Explain with Claude** — send selected code with an explanation prompt
  - **Fix with Claude** — send selected code with a fix prompt
  - **Send File to Claude** — inject the current file as an `@path/to/file` mention
- **Navigate to file** — Claude can open any file at a specific line in the editor via `open_file`
- **Git awareness** — reports the current git branch to Claude for every session

### Appearance
- **IDE theme adaptation** — reads the IDE's color palette and editor font at startup and on every theme switch; the webview recolors itself automatically (light and dark themes supported)
- **Editor font injection** — `--vscode-editor-font-family` and `--vscode-editor-font-size` are set from the IDE's editor font settings

### Settings & Reliability
- **Persistent settings** — permission mode, model, thinking level, custom env vars, all saved across IDE restarts
- **Load failure retry** — if the webview fails to load, a styled error page is shown with a Retry button
- **Crash notification** — if the Claude subprocess exits unexpectedly, a balloon notification is shown
- **Keyboard shortcuts**
  - `Ctrl+Escape` — focus the Claude tool window
  - `Ctrl+Shift+Escape` — start a new conversation

## Requirements

| Requirement | Version |
|---|---|
| JetBrains Rider | 2022.2+ |
| Java (build) | 17+ |
| Claude CLI (`claude`) | latest |

The `claude` binary must be on your `PATH`, or its path can be configured under **Settings → Tools → Claude Code**.

## Building

```bash
# Clone
git clone https://github.com/NoahXia/ClaudeCode-RiderPlugin.git
cd ClaudeCode-RiderPlugin

# Copy the VS Code extension webview assets (required — not bundled in the repo)
# From: ~/.vscode/extensions/anthropic.claude-code-*/webview/
cp /path/to/index.js  src/main/resources/webview/index.js
cp /path/to/index.css src/main/resources/webview/index.css

# Build plugin ZIP
JAVA_HOME=/path/to/jdk-17 ./gradlew buildPlugin

# Output: build/distributions/claude-code-rider-1.0.0-mvp.zip
```

## Installation

1. Build the ZIP (see above) or download a release
2. In Rider: **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Select `claude-code-rider-1.0.0-mvp.zip`
4. Restart Rider

## Settings

Open **Settings → Tools → Claude Code**:

| Setting | Description |
|---|---|
| Executable path | Path to `claude` binary (empty = auto-detect from PATH) |
| Test Connection | Runs `claude --version` and shows the result |
| Initial permission mode | `default` / `acceptEdits` / `plan` / `bypassPermissions` |
| Use Ctrl+Enter to send | Alternative send shortcut |
| Environment variables | Extra `NAME=VALUE` pairs passed to every `claude` subprocess |

## Architecture

```
ClaudeToolWindowPanel          (Swing panel, hosts JCEF browser)
└── ClaudeBrowserManager       (JBCefBrowser lifecycle, theme injection, LafManagerListener,
    │                           editor context sync via FileEditorManagerListener + SelectionListener)
    ├── HtmlTemplateProvider   (generates HTML shell with acquireVsCodeApi() shim + CSS vars)
    ├── WebviewAssetProvider   (extracts webview/index.{js,css} to temp dir for file:// loading)
    └── ClaudeMessageRouter    (CefMessageRouter handler — all JS↔Kotlin IPC)
            │
            └── ClaudeProcessManager   (project service — one claude subprocess per channel)

actions/
├── OpenClaudeAction           (Ctrl+Escape — focus tool window)
├── NewConversationAction      (Ctrl+Shift+Escape — new conversation)
├── ClaudeEditorActionGroup    (editor right-click "Claude" submenu)
├── AskClaudeAction            (Ask / Explain with Claude / Fix with Claude)
└── SendFileAction             (Send File to Claude — injects @relative/path mention)
```

**IPC protocol**: the React bundle communicates via `window.cefQuery()` (Kotlin side) / `window.__fromExtension()` (JS side), wrapped in the same `{ type: "from-extension", message }` envelope that the VS Code extension uses — so the unmodified webview bundle works transparently.

## Notes

- The VS Code webview assets (`index.js`, `index.css`) are **not included** in this repository because they are part of the proprietary Anthropic VS Code extension. Copy them from your local VS Code extension directory.
- This plugin is intended for local use only and is not published to the JetBrains Marketplace.
