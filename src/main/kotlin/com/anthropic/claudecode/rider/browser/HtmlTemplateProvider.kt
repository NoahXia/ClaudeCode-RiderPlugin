package com.anthropic.claudecode.rider.browser

import java.util.UUID

/** Theme colors read from the IDE at load time and injected as CSS variable overrides. */
data class ThemeColors(
    val isDark: Boolean = true,
    val panelBackground: String = "#252526",
    val editorBackground: String = "#1e1e1e",
    val foreground: String = "#cccccc",
    val inputBackground: String = "#3c3c3c",
    val inputForeground: String = "#cccccc",
    val buttonBackground: String = "#0e639c",
    val buttonForeground: String = "#ffffff",
    val buttonHover: String = "#1177bb",
    val selectionBackground: String = "#04395e",
    val selectionForeground: String = "#ffffff",
    val hoverBackground: String = "#2a2d2e",
    val focusBorder: String = "#007fd4",
    val linkForeground: String = "#3794ff",
    val editorFontFamily: String = "Consolas, \"Courier New\", monospace",
    val editorFontSize: Int = 13,
    val uiFontFamily: String = "-apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif",
    val uiFontSize: Int = 13
)

/**
 * Generates the HTML shell that hosts the VS Code webview React bundle in JCEF.
 *
 * The key trick is injecting a JavaScript shim that implements `acquireVsCodeApi()`
 * on top of JCEF's `window.cefQuery()`, making the unmodified VS Code webview bundle
 * work transparently inside Rider.
 */
object HtmlTemplateProvider {

    fun generate(
        cssUrl: String,
        jsUrl: String,
        isSidebar: Boolean = true,
        isFullEditor: Boolean = false,
        isSessionListOnly: Boolean = false,
        initialPrompt: String? = null,
        initialSession: String? = null,
        initialAuthStatus: String? = null,
        themeColors: ThemeColors = ThemeColors()
    ): String {
        val nonce = UUID.randomUUID().toString().replace("-", "")

        val dataAttrs = buildString {
            initialPrompt?.let { append(" data-initial-prompt=\"") ; append(it.escapeHtml()); append("\"") }
            initialSession?.let { append(" data-initial-session=\"") ; append(it.escapeHtml()); append("\"") }
            initialAuthStatus?.let { append(" data-initial-auth-status=\"") ; append(it.escapeHtml()); append("\"") }
        }

        // Dynamic CSS overrides derived from IDE theme — applied after the Dark+ defaults above.
        val dynamicCss = """
            :root {
                --vscode-font-family: ${themeColors.uiFontFamily};
                --vscode-font-size: ${themeColors.uiFontSize}px;
                --vscode-chat-font-family: ${themeColors.uiFontFamily};
                --vscode-chat-font-size: ${themeColors.uiFontSize}px;
                --vscode-editor-font-family: ${themeColors.editorFontFamily};
                --vscode-editor-font-size: ${themeColors.editorFontSize}px;
                --vscode-foreground: ${themeColors.foreground};
                --vscode-sideBar-background: ${themeColors.panelBackground};
                --vscode-editor-background: ${themeColors.editorBackground};
                --vscode-input-background: ${themeColors.inputBackground};
                --vscode-input-foreground: ${themeColors.inputForeground};
                --vscode-button-background: ${themeColors.buttonBackground};
                --vscode-button-foreground: ${themeColors.buttonForeground};
                --vscode-button-hoverBackground: ${themeColors.buttonHover};
                --vscode-list-activeSelectionBackground: ${themeColors.selectionBackground};
                --vscode-list-activeSelectionForeground: ${themeColors.selectionForeground};
                --vscode-list-hoverBackground: ${themeColors.hoverBackground};
                --vscode-focusBorder: ${themeColors.focusBorder};
                --vscode-textLink-foreground: ${themeColors.linkForeground};
                --vscode-textLink-activeForeground: ${themeColors.linkForeground};
                --vscode-menu-background: ${themeColors.panelBackground};
                --vscode-editorWidget-background: ${themeColors.panelBackground};
                --vscode-editorHoverWidget-background: ${themeColors.panelBackground};
                --vscode-editorSuggestWidget-background: ${themeColors.panelBackground};
                --vscode-notifications-background: ${themeColors.panelBackground};
            }
        """.trimIndent()

        // language=HTML
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <link rel="stylesheet" href="$cssUrl"/>
    <style>
        :root {
            /* ── Font ── */
            --vscode-font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            --vscode-font-size: 13px;
            --vscode-font-weight: normal;
            --vscode-editor-font-family: "JetBrains Mono", "Cascadia Code", Consolas, "Courier New", monospace;
            --vscode-editor-font-size: 13px;
            --vscode-editor-font-weight: normal;
            --vscode-chat-font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            --vscode-chat-font-size: 13px;

            /* ── Core palette (VS Code Dark+) ── */
            --vscode-foreground: #cccccc;
            --vscode-disabledForeground: #cccccc80;
            --vscode-descriptionForeground: #acacac;
            --vscode-errorForeground: #f48771;
            --vscode-icon-foreground: #c5c5c5;
            --vscode-focusBorder: #007fd4;
            --vscode-widget-shadow: rgba(0,0,0,0.36);
            --vscode-widget-border: #303031;
            --vscode-contrastBorder: transparent;
            --vscode-contrastActiveBorder: transparent;

            /* ── Editor ── */
            --vscode-editor-background: #1e1e1e;
            --vscode-editor-foreground: #d4d4d4;
            --vscode-editor-selectionBackground: #264f78;
            --vscode-editor-inactiveSelectionBackground: #3a3d41;
            --vscode-editor-lineHighlightBackground: #2a2d2e;
            --vscode-editor-placeholder-foreground: #cccccc73;

            /* ── Sidebar / Panel ── */
            --vscode-sideBar-background: #252526;
            --vscode-sideBarSectionHeader-border: transparent;
            --vscode-sideBarActivityBarTop-border: transparent;
            --vscode-panel-border: #80808059;

            /* ── Inputs ── */
            --vscode-input-background: #3c3c3c;
            --vscode-input-foreground: #cccccc;
            --vscode-input-placeholderForeground: #cccccc80;
            --vscode-inputOption-activeBorder: #007acc;
            --vscode-inputOption-hoverBackground: #5a5d5e80;
            --vscode-inputValidation-infoBorder: #007acc;
            --vscode-inlineChatInput-border: #454545;

            /* ── Buttons ── */
            --vscode-button-background: #0e639c;
            --vscode-button-foreground: #ffffff;
            --vscode-button-hoverBackground: #1177bb;
            --vscode-button-border: transparent;
            --vscode-button-separator: #ffffff66;
            --vscode-button-secondaryBackground: #3a3d41;
            --vscode-button-secondaryForeground: #cccccc;
            --vscode-button-secondaryHoverBackground: #45494e;

            /* ── Lists ── */
            --vscode-list-activeSelectionBackground: #04395e;
            --vscode-list-activeSelectionForeground: #ffffff;
            --vscode-list-hoverBackground: #2a2d2e;
            --vscode-list-highlightForeground: #2aaaff;
            --vscode-list-focusHighlightForeground: #2aaaff;

            /* ── Badges ── */
            --vscode-badge-background: #4d4d4d;
            --vscode-badge-foreground: #cccccc;

            /* ── Scrollbar ── */
            --vscode-scrollbar-shadow: #000000;
            --vscode-scrollbarSlider-background: #79797966;
            --vscode-scrollbarSlider-hoverBackground: #646464b3;
            --vscode-scrollbarSlider-activeBackground: #bfbfbf66;

            /* ── Text links ── */
            --vscode-textLink-foreground: #3794ff;
            --vscode-textLink-activeForeground: #3794ff;
            --vscode-textCodeBlock-background: #0a0a0a;

            /* ── Notifications ── */
            --vscode-notifications-background: #252526;

            /* ── Banners ── */
            --vscode-banner-background: #04395e;
            --vscode-banner-foreground: #cccccc;
            --vscode-banner-iconForeground: #2aaaff;

            /* ── Toolbar ── */
            --vscode-toolbar-hoverBackground: #5a5d5e50;
            --vscode-actionBar-toggledBackground: #383a49;

            /* ── Menu ── */
            --vscode-menu-background: #303031;
            --vscode-menu-foreground: #cccccc;
            --vscode-menu-selectionBackground: #04395e;
            --vscode-menu-selectionForeground: #ffffff;
            --vscode-menu-border: #454545;
            --vscode-menu-selectionBorder: transparent;
            --vscode-menu-separatorBackground: #454545;

            /* ── Progress bar ── */
            --vscode-progressBar-background: #0e70c0;

            /* ── Editor widgets ── */
            --vscode-editorWidget-background: #252526;
            --vscode-editorWidget-foreground: #cccccc;
            --vscode-editorWidget-border: #454545;
            --vscode-editorWidget-resizeBorder: #007fd4;
            --vscode-editorHoverWidget-background: #252526;
            --vscode-editorHoverWidget-foreground: #cccccc;
            --vscode-editorHoverWidget-border: #454545;
            --vscode-editorHoverWidget-highlightForeground: #2aaaff;
            --vscode-editorHoverWidget-statusBarBackground: #2c2c2d;
            --vscode-editorSuggestWidget-background: #252526;
            --vscode-editorSuggestWidget-foreground: #d4d4d4;
            --vscode-editorSuggestWidget-border: #454545;
            --vscode-editorSuggestWidget-highlightForeground: #2aaaff;
            --vscode-editorSuggestWidget-focusHighlightForeground: #2aaaff;
            --vscode-editorSuggestWidget-selectedForeground: #ffffff;
            --vscode-editorSuggestWidget-selectedIconForeground: #ffffff;
            --vscode-editorSuggestWidgetStatus-foreground: #acacac80;

            /* ── Keybinding labels ── */
            --vscode-keybindingLabel-background: #80808033;
            --vscode-keybindingLabel-foreground: #cccccc;
            --vscode-keybindingLabel-border: #33333399;
            --vscode-keybindingLabel-bottomBorder: #44444499;

            /* ── Git decorations ── */
            --vscode-gitDecoration-addedResourceForeground: #81b88b;
            --vscode-gitDecoration-deletedResourceForeground: #c74e39;

            /* ── Problems / Diagnostics icons ── */
            --vscode-problemsErrorIcon-foreground: #f14c4c;
            --vscode-problemsWarningIcon-foreground: #cca700;
            --vscode-problemsInfoIcon-foreground: #3794ff;

            /* ── Diff editor ── */
            --vscode-diffEditor-insertedTextBackground: #9bb95533;
            --vscode-diffEditor-removedTextBackground: #ff000033;
            --vscode-diffEditor-insertedLineBackground: #9bb95520;
            --vscode-diffEditor-removedLineBackground: #ff000020;
            --vscode-diffEditor-border: transparent;
            --vscode-diffEditor-diagonalFill: #cccccc1a;

            /* ── Chat / slash commands ── */
            --vscode-chat-slashCommandBackground: #34414b;
            --vscode-chat-slashCommandForeground: #40a6ff;

            /* ── Charts ── */
            --vscode-charts-blue: #3794ff;
            --vscode-charts-green: #89d185;

            /* ── Sash ── */
            --vscode-sash-size: 4px;
            --vscode-sash-hoverBorder: #007fd4;
            --vscode-sash-hover-size: 4px;

            /* ── Misc widget hints used by JS ── */
            --vscode-hover-maxWidth: 500px;
            --vscode-hover-whiteSpace: pre-wrap;
            --vscode-hover-sourceWhiteSpace: pre-wrap;
        }

        $dynamicCss

        html, body {
            margin: 0; padding: 0; height: 100%; overflow: hidden;
            background-color: var(--vscode-sideBar-background, #252526);
            color: var(--vscode-foreground, #cccccc);
            font-family: var(--vscode-font-family);
            font-size: var(--vscode-font-size);
        }
        #root { height: 100%; }

        /* Session list popup: prevent the inner root from clipping the Local/Web tab row */
        [class*="root_OOQiHg"] { overflow: visible; }
        /* Ensure the segmented tab bar has breathing room on the left */
        [class*="segmented_OOQiHg"] { margin-left: 2px; }

        /* inputMentionChip: text inserted via insert_at_mention is wrapped in a chip span.
           Override so it renders as plain foreground text instead of a dark invisible chip. */
        [class*="inputMentionChip"] {
            background: transparent !important;
            color: var(--vscode-foreground, #cccccc) !important;
            border: none !important;
            padding: 0 !important;
            white-space: pre-wrap !important;
        }

        /* ── Thin overlay scrollbars (WebKit/Chromium) ── */
        ::-webkit-scrollbar { width: 6px; height: 6px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-webkit-scrollbar-corner { background: transparent; }
        ::-webkit-scrollbar-corner { background: transparent; }
        ::-webkit-scrollbar-thumb {
            background: var(--vscode-scrollbarSlider-background, #79797966);
            border-radius: 3px;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: var(--vscode-scrollbarSlider-hoverBackground, #646464b3);
        }
        ::-webkit-scrollbar-thumb:active {
            background: var(--vscode-scrollbarSlider-activeBackground, #bfbfbf66);
        }
        /* scrollbar-gutter so content doesn't shift when scrollbar appears */
        * { scrollbar-width: thin; scrollbar-color: var(--vscode-scrollbarSlider-background, #79797966) transparent; }
    </style>
</head>
<body>
    <div id="root"$dataAttrs></div>

    <script nonce="$nonce">
    (function () {
        'use strict';

        let _api = null;

        window.acquireVsCodeApi = function () {
            if (_api) return _api;
            let _state = null;
            _api = {
                postMessage: function (message) {
                    if (typeof window.cefQuery === 'undefined') {
                        console.warn('[claude-rider] cefQuery not yet available, queuing message', message);
                        return;
                    }
                    window.cefQuery({
                        request: JSON.stringify(message),
                        persistent: false,
                        onSuccess: function () {},
                        onFailure: function (errorCode, errorMessage) {
                            console.error('[claude-rider] IPC error', errorCode, errorMessage);
                        }
                    });
                },
                getState: function () { return _state; },
                setState: function (newState) {
                    _state = newState;
                    return _state;
                }
            };
            return _api;
        };

        window.__fromExtension = function (message) {
            window.dispatchEvent(new MessageEvent('message', { data: message }));
            // JCEF non-OSR doesn't always repaint after JS-driven DOM updates without
            // user input. Promote the root to a compositing layer for one frame so CEF
            // flushes whatever the React scheduler just committed.
            requestAnimationFrame(function () {
                document.documentElement.style.willChange = 'transform';
                requestAnimationFrame(function () {
                    document.documentElement.style.willChange = '';
                });
            });
        };

        window.IS_SIDEBAR = ${isSidebar};
        window.IS_FULL_EDITOR = ${isFullEditor};
        window.IS_SESSION_LIST_ONLY = ${isSessionListOnly};

    }());
    </script>

    <script src="$jsUrl"></script>
    <script>
    (function () {
        var HIDDEN_ITEMS = ['Switch account', '/remote-control'];
        var HIDDEN_ITEM_PREFIXES = ['General config'];
        var HIDDEN_SECTIONS = ['Settings', 'Slash Commands'];

        function applyHides() {
            // Hide specific command items
            document.querySelectorAll('[class*="commandLabel"]').forEach(function (el) {
                var text = el.textContent.trim();
                var hide = HIDDEN_ITEMS.indexOf(text) !== -1 ||
                           HIDDEN_ITEM_PREFIXES.some(function (p) { return text.startsWith(p); });
                if (hide) {
                    var item = el.closest('[class*="commandItem"]');
                    if (item) item.style.display = 'none';
                }
            });
            // Hide entire section containers (sectionDivider + sectionHeader + items)
            // when all commandItems in that section are hidden.
            // DOM shape: <div key=sectionName> [sectionDivider?] sectionHeader commandItem* </div>
            document.querySelectorAll('[class*="sectionHeader"]').forEach(function (header) {
                var text = header.textContent.trim();
                if (HIDDEN_SECTIONS.indexOf(text) === -1) return;
                var sibling = header.nextElementSibling;
                var hasVisible = false;
                while (sibling && !sibling.className.includes('sectionHeader')) {
                    if (sibling.className.includes('commandItem') &&
                        sibling.style.display !== 'none') {
                        hasVisible = true;
                        break;
                    }
                    sibling = sibling.nextElementSibling;
                }
                // Hide the parent section container (which includes sectionDivider)
                var container = header.parentElement;
                if (container) {
                    container.style.display = hasVisible ? '' : 'none';
                } else {
                    header.style.display = hasVisible ? '' : 'none';
                }
            });
        }

        var observer = new MutationObserver(applyHides);
        observer.observe(document.documentElement, { childList: true, subtree: true });
    }());
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
