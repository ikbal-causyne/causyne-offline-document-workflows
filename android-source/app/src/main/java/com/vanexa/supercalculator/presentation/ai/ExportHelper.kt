package com.vanexa.supercalculator.presentation.ai

import android.app.Activity
import android.print.DirectPrintHelper
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.view.ViewGroup
import android.view.View
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.vanexa.supercalculator.R
import com.vanexa.supercalculator.data.local.entity.ChatMessage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ExportHelper — Export chat session to TXT, MD, or PDF.
 * Single message or Full Session support.
 */
object ExportHelper {

    enum class Format { TXT, MD, PDF }

    // Lifecycle guard to prevent WebView garbage collection during printing
    private var activeWebView: WebView? = null

    interface ExportCallback {
        fun onProgress(message: String)
        fun onComplete(savedUri: Uri?, mimeType: String)
        fun onError(error: String)
    }

    fun export(activity: Activity, messages: List<ChatMessage>, format: Format, callback: ExportCallback) {
        if (messages.isEmpty()) {
            callback.onError("No messages to export")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        callback.onProgress(activity.getString(R.string.export_preparing))
        
        try {
            when (format) {
                Format.TXT -> {
                    val content = generateTextContent(messages)
                    val fileName = "vanexa_chat_$timestamp.txt"
                    val uri = saveToPublicFolder(activity, fileName, content, "text/plain")
                    callback.onComplete(uri, "text/plain")
                }
                Format.MD -> {
                    val content = generateMarkdownContent(messages)
                    val fileName = "vanexa_chat_$timestamp.md"
                    val uri = saveToPublicFolder(activity, fileName, content, "text/markdown")
                    callback.onComplete(uri, "text/markdown")
                }
                Format.PDF -> {
                    exportHighFidelity(activity, messages, timestamp, callback)
                }
            }
        } catch (e: Exception) {
            callback.onError(e.message ?: "Unknown error")
        }
    }

    fun exportSingleMessage(activity: Activity, message: ChatMessage, format: Format, callback: ExportCallback) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        callback.onProgress(activity.getString(R.string.export_preparing))

        try {
            when (format) {
                Format.TXT -> {
                    val content = generateTextContent(listOf(message))
                    val fileName = "vanexa_output_$timestamp.txt"
                    val uri = saveToPublicFolder(activity, fileName, content, "text/plain")
                    callback.onComplete(uri, "text/plain")
                }
                Format.MD -> {
                    val content = generateMarkdownContent(listOf(message))
                    val fileName = "vanexa_output_$timestamp.md"
                    val uri = saveToPublicFolder(activity, fileName, content, "text/markdown")
                    callback.onComplete(uri, "text/markdown")
                }
                Format.PDF -> {
                    exportHighFidelity(activity, listOf(message), timestamp, callback)
                }
            }
        } catch (e: Exception) {
            callback.onError(e.message ?: "Unknown error")
        }
    }

    fun cancelExport() {
        activeWebView?.stopLoading()
        activeWebView = null
    }

    private fun generateTextContent(messages: List<ChatMessage>): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val builder = StringBuilder()
        
        builder.appendLine("================================================")
        builder.appendLine("VANEXA AI — CONVERSATION EXPORT")
        builder.appendLine("Exported on: $dateStr")
        builder.appendLine("================================================")
        builder.appendLine()

        messages.sortedBy { it.timestamp }.forEach { msg ->
            val role = if (msg.isUser) "YOU" else "VANEXA AI"
            builder.appendLine("[$role]")
            builder.appendLine(msg.content)
            builder.appendLine("-".repeat(20))
            builder.appendLine()
        }
        
        builder.appendLine("================================================")
        builder.appendLine("This document was generated by Vanexa AI.")
        builder.appendLine("================================================")
        
        return builder.toString()
    }

    private fun generateMarkdownContent(messages: List<ChatMessage>): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val builder = StringBuilder()
        builder.appendLine("# Vanexa AI — Conversation Export")
        builder.appendLine("> *Generated on $dateStr*")
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine()

        messages.sortedBy { it.timestamp }.forEach { msg ->
            if (msg.isUser) {
                builder.appendLine("### 👤 You")
                builder.appendLine(msg.content)
            } else {
                builder.appendLine("### 🤖 Vanexa AI")
                builder.appendLine(msg.content)
            }
            builder.appendLine()
            builder.appendLine("---")
            builder.appendLine()
        }
        
        builder.appendLine("Managed by **Vanexa AI**.")
        return builder.toString()
    }

    private fun saveToPublicFolder(context: Context, fileName: String, content: String, mimeType: String): Uri? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Vanexa")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { stream ->
                        stream.write(content.toByteArray())
                    }
                    return it
                } ?: throw Exception("Failed to insert media record")
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Vanexa")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.writeText(content)
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                context.sendBroadcast(intent)
                return Uri.fromFile(file)
            }
        } catch (e: Exception) {
            val tempDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val file = File(tempDir, fileName)
            file.writeText(content)
            shareFile(context, file, mimeType)
            return null
        }
    }

    private fun exportHighFidelity(activity: Activity, messages: List<ChatMessage>, timestamp: String, callback: ExportCallback) {
        activity.runOnUiThread {
            val html = generateHtml(messages)
            val handler = Handler(Looper.getMainLooper())
            
            // Timeout Watchdog - 20 seconds
            val timeoutRunnable = Runnable {
                if (activeWebView != null) {
                    cleanupWebView(activity)
                    callback.onError("Rendering timeout: Connection or script error")
                }
            }
            handler.postDelayed(timeoutRunnable, 20000)

            val webView = WebView(activity)
            activeWebView = webView

            webView.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                // Add JS Interface for explicit "Ready" signal
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onReady() {
                        handler.removeCallbacks(timeoutRunnable)
                        activity.runOnUiThread {
                            if (activeWebView != null) {
                                savePdfDirectly(activity, webView, timestamp, callback)
                            }
                        }
                    }
                }, "VanexaExport")

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        handler.removeCallbacks(timeoutRunnable)
                        activity.runOnUiThread {
                            callback.onError("Resource error: ${error?.description ?: "Unknown"}")
                            cleanupWebView(activity)
                        }
                    }
                }

                // Window Attachment Hack: 
                // Headless WebViews often stall. Attaching to the window forces layout/render.
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                val params = ViewGroup.LayoutParams(1, 1) // 1x1 pixel
                webView.visibility = View.INVISIBLE
                rootView.addView(webView, params)

                loadDataWithBaseURL("file:///android_asset/export/", html, "text/html", "utf-8", null)
            }
        }
    }

    private fun cleanupWebView(activity: Activity) {
        activity.runOnUiThread {
            activeWebView?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.destroy()
            }
            activeWebView = null
        }
    }

    private fun savePdfDirectly(activity: Activity, webView: WebView, timestamp: String, callback: ExportCallback) {
        try {
            val fileName = "vanexa_chat_$timestamp.pdf"
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            val adapter = webView.createPrintDocumentAdapter("Vanexa_Export")

            val mimeType = "application/pdf"
            val resolver = activity.contentResolver
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Vanexa")
                }
                resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Vanexa")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                Uri.fromFile(file)
            }

            if (uri == null) throw Exception("Could not create destination file")

            val pfd = resolver.openFileDescriptor(uri, "w")
                ?: throw Exception("Could not open file descriptor")

            DirectPrintHelper.printAdapterDirectly(adapter, attributes, pfd, object : DirectPrintHelper.DirectPrintCallback {
                override fun onComplete() {
                    try {
                        pfd.close()
                        activity.runOnUiThread {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                intent.data = uri
                                activity.sendBroadcast(intent)
                            }
                            callback.onComplete(uri, mimeType)
                            cleanupWebView(activity)
                        }
                    } catch (e: Exception) {
                        callback.onError(e.message ?: "Finalization failed")
                        cleanupWebView(activity)
                    }
                }

                override fun onError(error: String) {
                    try { pfd.close() } catch (ex: Exception) {}
                    callback.onError(error)
                    cleanupWebView(activity)
                }
            })

        } catch (e: Exception) {
            callback.onError(e.message ?: "Failed to initiate PDF capture")
            cleanupWebView(activity)
        }
    }

    private fun generateHtml(messages: List<ChatMessage>): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val gson = com.google.gson.Gson()
        val messagesJson = gson.toJson(messages.sortedBy { it.timestamp }.map { msg ->
            mapOf(
                "role" to if (msg.isUser) "You" else "Vanexa AI",
                "content" to msg.content,
                "isUser" to msg.isUser
            )
        })

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <link rel="stylesheet" href="katex.min.css">
                <script defer src="katex.min.js"></script>
                <script defer src="auto-render.min.js"></script>
                <script src="marked.min.js"></script>
                <style>
                    body { font-family: 'Inter', 'Segoe UI', Roboto, sans-serif; padding: 40px; line-height: 1.7; color: #111; background: #fff; }
                    .header { border-bottom: 1px solid #e2e8f0; padding-bottom: 15px; margin-bottom: 40px; text-align: right; }
                    .date { font-size: 13px; color: #777; font-weight: 500; }
                    .message-container { margin-bottom: 35px; page-break-inside: avoid; }
                    .role { font-size: 12px; text-transform: uppercase; font-weight: 700; color: #999; margin-bottom: 8px; letter-spacing: 1.5px; }
                    .content { font-size: 15px; }
                    .ai-block { color: #1a1a1a; }
                    .user-bubble { background: #f8fafc; padding: 20px; border-radius: 16px; color: #0f172a; border: 1px solid #e2e8f0; }
                    code { background: #f1f5f9; padding: 3px 6px; border-radius: 6px; font-family: 'Fira Code', 'Courier New', monospace; font-size: 0.9em; color: #e11d48; }
                    pre { background: #1e293b; color: #f8fafc; padding: 20px; border-radius: 12px; overflow-x: auto; margin: 15px 0; font-family: 'Fira Code', monospace; font-size: 13px; }
                    b { color: #000; font-weight: 700; }
                    .footer { margin-top: 60px; font-size: 11px; color: #94a3b8; text-align: center; border-top: 1px solid #e2e8f0; padding-top: 30px; letter-spacing: 0.5px; }
                    p { margin: 10px 0; }
                    ul, ol { margin: 10px 0; padding-left: 25px; }
                    li { margin-bottom: 5px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <span class="date">Generated on $dateStr</span>
                </div>
                <div id="content-area"></div>
                <div class="footer">
                    This document was generated by Vanexa AI.
                </div>
                <script>
                    window.onload = function() {
                        const messages = $messagesJson;
                        const contentArea = document.getElementById('content-area');
                        
                        let tokens = [];
                        let tokenCount = 0;
                        const placeholderPrefix = "VNX_LTX_";

                        messages.forEach(msg => {
                            const container = document.createElement('div');
                            container.className = 'message-container';
                            
                            if (msg.isUser) {
                                const roleDiv = document.createElement('div');
                                roleDiv.className = 'role';
                                roleDiv.textContent = msg.role;
                                container.appendChild(roleDiv);
                            }

                            // SURGICAL ISOLATION JS PORT: Protect Math from Markdown Destruction
                            let rawText = msg.content;
                            
                            // 1. Normalize DeepSeek syntax to dollar signs
                            rawText = rawText.replace(/\\\[([\s\S]*?)\\\]/g, "\n\n$$$$$1$$$$\n\n");
                            rawText = rawText.replace(/\\\(([\s\S]*?)\\\)/g, "$$$1$$");

                            // 2. Tokenize Block Math
                            rawText = rawText.replace(/\$\$([\s\S]*?)\$\$/g, function(match, formula) {
                                let id = placeholderPrefix + "BLK_" + tokenCount++;
                                tokens.push({id: id, formula: formula.trim(), displayMode: true});
                                return "\n\n" + id + "\n\n";
                            });

                            // 3. Tokenize Inline Math
                            rawText = rawText.replace(/\$([^\$]+?)\$/g, function(match, formula) {
                                let id = placeholderPrefix + "INL_" + tokenCount++;
                                tokens.push({id: id, formula: formula.trim(), displayMode: false});
                                return id;
                            });

                            // 4. Render Markdown safely (now blind to the protected math formulas)
                            let html = marked.parse(rawText);

                            // 5. Inject KaTeX back over the placeholders
                            tokens.forEach(token => {
                                try {
                                    let rendered = katex.renderToString(token.formula, {
                                        displayMode: token.displayMode,
                                        throwOnError: false
                                    });
                                    // Replace using split/join to handle any regex-sensitive characters safely
                                    html = html.split(token.id).join(rendered);
                                } catch (e) {
                                    let fallback = token.displayMode ? "$$" + token.formula + "$$" : "$" + token.formula + "$";
                                    html = html.split(token.id).join(fallback);
                                }
                            });
                            
                            const contentDiv = document.createElement('div');
                            contentDiv.className = 'content ' + (msg.isUser ? 'user-bubble' : 'ai-block');
                            contentDiv.innerHTML = html;
                            container.appendChild(contentDiv);
                            
                            contentArea.appendChild(container);
                        });

                        // Explicit signal to Kotlin that rendering is finished
                        // We add a small 200ms delay to ensure DOM paint is finalized
                        setTimeout(() => {
                            if (window.VanexaExport && typeof window.VanexaExport.onReady === 'function') {
                                window.VanexaExport.onReady();
                            }
                        }, 200);
                    };
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Export Vanexa Output"))
    }
}
