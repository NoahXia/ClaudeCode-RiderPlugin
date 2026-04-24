package com.anthropic.claudecode.rider.browser

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Extracts the bundled webview assets (index.js, index.css) from the plugin JAR
 * into a temporary directory so that JCEF can load them via file:// URLs.
 *
 * Also writes the generated index.html into the same directory so it can be loaded
 * via loadURL("file:///...") — this ensures relative/absolute file:// references
 * to index.js and index.css work without CSP issues.
 */
object WebviewAssetProvider {

    private val log = Logger.getInstance(WebviewAssetProvider::class.java)
    private var cachedDir: Path? = null

    @Synchronized
    fun ensureExtracted(): Path {
        cachedDir?.let { return it }

        val dir = Files.createTempDirectory("claude-code-rider-webview")
        log.info("Extracting Claude webview assets to $dir")

        for (name in listOf("index.js", "index.css")) {
            val resource = WebviewAssetProvider::class.java.getResourceAsStream("/webview/$name")
                ?: throw IllegalStateException(
                    "Webview resource '/webview/$name' not found in plugin JAR. " +
                    "Copy webview/index.js and webview/index.css from the VS Code extension " +
                    "into src/main/resources/webview/"
                )
            resource.use { input ->
                Files.copy(input, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING)
            }
            log.debug("Extracted $name")
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                FileUtil.delete(dir.toFile())
            } catch (ignored: Exception) { }
        })

        cachedDir = dir
        return dir
    }

    /**
     * Writes the HTML string into index.html in the asset directory and returns
     * the file:// URL suitable for JBCefBrowser.loadURL().
     */
    fun writeHtml(assetDir: Path, html: String): String {
        val htmlFile = assetDir.resolve("index.html")
        Files.writeString(htmlFile, html)
        return htmlFile.toUri().toString()
    }
}
