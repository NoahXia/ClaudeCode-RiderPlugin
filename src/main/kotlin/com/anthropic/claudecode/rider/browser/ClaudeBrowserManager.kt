package com.anthropic.claudecode.rider.browser

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefDisplayHandler
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Color
import java.awt.Component

class ClaudeBrowserManager(
    private val project: Project,
    parentDisposable: Disposable
) : Disposable {

    private val log = Logger.getInstance(ClaudeBrowserManager::class.java)

    private val browser: JBCefBrowser = JBCefBrowserBuilder()
        .setOffScreenRendering(false)
        .build()

    private val messageRouter: ClaudeMessageRouter = ClaudeMessageRouter(project, this)

    val component: Component get() = browser.component

    init {
        Disposer.register(parentDisposable, this)
        setupRouter()
        setupConsoleHandler()
        setupLoadHandler()
        setupThemeListener()
        loadWebview()
    }

    private fun setupRouter() {
        val config = CefMessageRouter.CefMessageRouterConfig().apply {
            jsQueryFunction = "cefQuery"
            jsCancelFunction = "cefQueryCancel"
        }
        val router = CefMessageRouter.create(config)
        router.addHandler(messageRouter, true)
        browser.jbCefClient.cefClient.addMessageRouter(router)
    }

    /** Capture all JS console.log/warn/error output to the IDE log. */
    private fun setupConsoleHandler() {
        browser.jbCefClient.addDisplayHandler(object : CefDisplayHandler {
            override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {}
            override fun onTitleChange(browser: CefBrowser?, title: String?) {}
            override fun onTooltip(browser: CefBrowser?, text: String?): Boolean = false
            override fun onStatusMessage(browser: CefBrowser?, value: String?) {}
            override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean = false
            override fun onFullscreenModeChange(browser: CefBrowser?, fullscreen: Boolean) {}

            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: org.cef.CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                val tag = when (level) {
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_ERROR,
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_FATAL -> "ERROR"
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING -> "WARN"
                    else -> "INFO"
                }
                log.info("[JS $tag] $message (source: $source:$line)")
                return false
            }
        }, browser.cefBrowser)
    }

    private fun setupLoadHandler() {
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    log.info("Claude webview loaded (status $httpStatusCode)")
                }
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?
            ) {
                log.warn("Claude webview load error: $errorCode - $errorText (url: $failedUrl)")
            }
        }, browser.cefBrowser)
    }

    /** Reload the webview when the user switches the IDE theme. */
    private fun setupThemeListener() {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener {
                loadWebview()
            })
    }

    fun loadWebview() {
        try {
            val assetDir = WebviewAssetProvider.ensureExtracted()
            val html = HtmlTemplateProvider.generate(
                cssUrl = "index.css",
                jsUrl = "index.js",
                isSidebar = true,
                isFullEditor = false,
                isSessionListOnly = false,
                themeColors = readThemeColors()
            )
            val url = WebviewAssetProvider.writeHtml(assetDir, html)
            browser.loadURL(url)
        } catch (e: Exception) {
            log.error("Failed to load Claude webview", e)
            browser.loadHTML("<html><body><h3>Failed to load Claude Code: ${e.message}</h3></body></html>")
        }
    }

    /** Reads current IDE palette + editor font and maps them to VS Code CSS variables. */
    private fun readThemeColors(): ThemeColors {
        val isDark = !JBColor.isBright()

        fun Color?.toHex(fallback: String): String {
            this ?: return fallback
            return "#%02x%02x%02x".format(red, green, blue)
        }

        fun uiColor(key: String, fallback: String): String =
            javax.swing.UIManager.getColor(key).toHex(fallback)

        val panelBg = uiColor("Panel.background", if (isDark) "#252526" else "#f3f3f3")
        val editorBg = uiColor("EditorPane.background", if (isDark) "#1e1e1e" else "#ffffff")
        val fg = uiColor("Label.foreground", if (isDark) "#cccccc" else "#333333")
        val inputBg = uiColor("TextField.background", if (isDark) "#3c3c3c" else "#ffffff")
        val inputFg = uiColor("TextField.foreground", if (isDark) "#cccccc" else "#000000")
        val selBg = uiColor("List.selectionBackground", if (isDark) "#04395e" else "#0078d7")
        val selFg = uiColor("List.selectionForeground", if (isDark) "#ffffff" else "#ffffff")
        val focusBorder = uiColor("Component.focusColor", if (isDark) "#007fd4" else "#0078d7")
        val link = uiColor("Link.activeForeground", if (isDark) "#3794ff" else "#0066cc")

        // Button: try IntelliJ New UI key first, fall back to classic
        val btnBg = uiColor("Button.default.startBackground",
            uiColor("Button.default.background", if (isDark) "#0e639c" else "#0078d7"))
        val btnFg = uiColor("Button.default.foreground", if (isDark) "#ffffff" else "#ffffff")
        val btnHover = uiColor("Button.default.endBackground",
            uiColor("Button.hoverBackground", if (isDark) "#1177bb" else "#1a88e0"))

        val hoverBg = uiColor("Table.hoverBackground",
            if (isDark) "#2a2d2e" else "#e8e8e8")

        // Editor font from EditorColorsManager (most reliable source)
        val scheme = EditorColorsManager.getInstance().globalScheme
        val editorFont = scheme.editorFontName.takeIf { it.isNotBlank() } ?: "Consolas"
        val editorSize = scheme.editorFontSize.takeIf { it > 0 } ?: 13
        val uiFont = javax.swing.UIManager.getFont("Label.font")
        val uiFontFamily = uiFont?.family?.takeIf { it.isNotBlank() }
            ?: "-apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif"
        val uiFontSize = uiFont?.size?.takeIf { it > 0 } ?: 13

        return ThemeColors(
            isDark = isDark,
            panelBackground = panelBg,
            editorBackground = editorBg,
            foreground = fg,
            inputBackground = inputBg,
            inputForeground = inputFg,
            buttonBackground = btnBg,
            buttonForeground = btnFg,
            buttonHover = btnHover,
            selectionBackground = selBg,
            selectionForeground = selFg,
            hoverBackground = hoverBg,
            focusBorder = focusBorder,
            linkForeground = link,
            editorFontFamily = "\"$editorFont\", Consolas, \"Courier New\", monospace",
            editorFontSize = editorSize,
            uiFontFamily = "\"$uiFontFamily\", -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif",
            uiFontSize = uiFontSize
        )
    }

    /**
     * Sends a message to the webview wrapped in the { type: "from-extension", message: ... }
     * envelope that the VS Code React bundle listens for.
     * [innerJson] is the inner message JSON string (e.g. a "response" or "io_message" object).
     */
    fun sendToWebview(innerJson: String) {
        val escaped = innerJson.replace("\\", "\\\\").replace("`", "\\`")
        val script = "window.__fromExtension({type:'from-extension',message:JSON.parse(`$escaped`)})"
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url ?: "", 0)
    }

    /**
     * Inserts [text] into the active session's input box via the webview's
     * `insert_at_mention` request type. Used by editor context-menu actions.
     */
    fun insertAtMention(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("`", "\\`")
        val script = """
            window.__fromExtension({
                type: 'from-extension',
                message: {
                    type: 'request',
                    channelId: '',
                    requestId: '',
                    request: { type: 'insert_at_mention', text: `$escaped` }
                }
            });
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url ?: "", 0)
    }

    override fun dispose() {
        Disposer.dispose(browser)
    }
}
