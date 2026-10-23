package com.vanexa.docengine

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ExportHelper — High-Fidelity Multi-Format Content Exporter.
 *
 * Implements lifecycle decoupling to prevent asynchronous garbage collection
 * during mobile print spooling and file synthesis.
 *
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
object ExportHelper {

    fun generateMarkdown(payload: DocumentPayload): String {
        val sb = StringBuilder()
        sb.appendLine("# ${payload.title}")
        if (payload.author.isNotBlank()) {
            sb.appendLine("**Author:** ${payload.author}  ")
        }
        if (payload.timestamp.isNotBlank()) {
            sb.appendLine("**Generated:** ${payload.timestamp}  ")
        }
        sb.appendLine("---")
        sb.appendLine()

        for (block in payload.blocks) {
            when (block) {
                is ContentBlock.Heading -> {
                    val prefix = "#".repeat(block.level.coerceIn(1, 6))
                    sb.appendLine("$prefix ${block.text}")
                    sb.appendLine()
                }
                is ContentBlock.Paragraph -> {
                    var line = block.text
                    if (block.isBold) line = "**$line**"
                    if (block.isItalic) line = "*$line*"
                    sb.appendLine(line)
                    sb.appendLine()
                }
                is ContentBlock.Table -> {
                    if (block.headers.isNotEmpty()) {
                        sb.appendLine("| " + block.headers.joinToString(" | ") + " |")
                        sb.appendLine("| " + block.headers.map { "---" }.joinToString(" | ") + " |")
                        for (row in block.rows) {
                            sb.appendLine("| " + row.joinToString(" | ") + " |")
                        }
                        sb.appendLine()
                    }
                }
                is ContentBlock.KeyValue -> {
                    sb.appendLine("- **${block.key}:** ${block.value}")
                }
                is ContentBlock.Slide -> {
                    sb.appendLine("## Slide: ${block.title}")
                    for (b in block.bullets) {
                        sb.appendLine("  * $b")
                    }
                    sb.appendLine()
                }
            }
        }
        return sb.toString()
    }

    fun generateHtml(payload: DocumentPayload): String {
        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html lang=\"en\">")
        sb.appendLine("<head>")
        sb.appendLine("<meta charset=\"utf-8\" />")
        sb.appendLine("<title>${payload.title}</title>")
        sb.appendLine("<style>")
        sb.appendLine("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #222; max-width: 800px; margin: 40px auto; padding: 0 20px; }")
        sb.appendLine("h1 { font-size: 26px; border-bottom: 2px solid #eee; padding-bottom: 8px; color: #111; }")
        sb.appendLine("h2 { font-size: 20px; color: #333; margin-top: 24px; }")
        sb.appendLine("table { width: 100%; border-collapse: collapse; margin: 16px 0; }")
        sb.appendLine("th, td { border: 1px solid #ddd; padding: 10px 12px; text-align: left; }")
        sb.appendLine("th { background-color: #f8f9fa; font-weight: 600; }")
        sb.appendLine("ul { margin: 8px 0; padding-left: 24px; }")
        sb.appendLine(".meta { color: #666; font-size: 14px; margin-bottom: 20px; }")
        sb.appendLine("</style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")
        sb.appendLine("<h1>${payload.title}</h1>")
        sb.appendLine("<div class=\"meta\">Author: ${payload.author} | Date: ${payload.timestamp}</div>")

        for (block in payload.blocks) {
            when (block) {
                is ContentBlock.Heading -> {
                    val tag = "h${block.level.coerceIn(1, 6)}"
                    sb.appendLine("<$tag>${escapeHtml(block.text)}</$tag>")
                }
                is ContentBlock.Paragraph -> {
                    var text = escapeHtml(block.text)
                    if (block.isBold) text = "<strong>$text</strong>"
                    if (block.isItalic) text = "<em>$text</em>"
                    sb.appendLine("<p>$text</p>")
                }
                is ContentBlock.Table -> {
                    sb.appendLine("<table>")
                    if (block.headers.isNotEmpty()) {
                        sb.appendLine("<thead><tr>")
                        for (h in block.headers) sb.appendLine("<th>${escapeHtml(h)}</th>")
                        sb.appendLine("</tr></thead>")
                    }
                    sb.appendLine("<tbody>")
                    for (row in block.rows) {
                        sb.appendLine("<tr>")
                        for (cell in row) sb.appendLine("<td>${escapeHtml(cell)}</td>")
                        sb.appendLine("</tr>")
                    }
                    sb.appendLine("</tbody></table>")
                }
                is ContentBlock.KeyValue -> {
                    sb.appendLine("<p><strong>${escapeHtml(block.key)}:</strong> ${escapeHtml(block.value)}</p>")
                }
                is ContentBlock.Slide -> {
                    sb.appendLine("<div style=\"border:1px solid #ccc; padding:16px; margin:16px 0; border-radius:8px;\">")
                    sb.appendLine("<h3>${escapeHtml(block.title)}</h3>")
                    sb.appendLine("<ul>")
                    for (b in block.bullets) sb.appendLine("<li>${escapeHtml(b)}</li>")
                    sb.appendLine("</ul></div>")
                }
            }
        }

        sb.appendLine("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
