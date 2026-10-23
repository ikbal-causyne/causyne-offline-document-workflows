package com.vanexa.docengine

import java.io.File
import java.io.Serializable

/**
 * Data structures for the Zero-Cloud Document Synthesis Engine.
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */

/**
 * Supported Export & Compilation Formats.
 */
enum class DocumentFormat {
    PDF,
    DOCX,
    XLSX,
    PPTX,
    MARKDOWN,
    TXT
}

/**
 * PDF Surgical Object Compression Profiles.
 */
enum class CompressionMode(
    val dpi: Float,
    val jpegQuality: Int,
    val label: String
) {
    EXTREME(dpi = 96f, jpegQuality = 30, label = "Extreme"),
    HIGH(dpi = 130f, jpegQuality = 45, label = "High"),
    RECOMMENDED(dpi = 180f, jpegQuality = 60, label = "Recommended"),
    LOSSLESS(dpi = 0f, jpegQuality = 0, label = "Lossless")
}

/**
 * Asynchronous Progress Updates.
 */
sealed class ProgressUpdate {
    data class Loading(val message: String) : ProgressUpdate()
    data class Progress(val percent: Int, val detail: String) : ProgressUpdate()
    data class Success(
        val outputFile: File,
        val originalBytes: Long,
        val finalBytes: Long,
        val durationMs: Long
    ) : ProgressUpdate()
    data class Error(val message: String, val throwable: Throwable? = null) : ProgressUpdate()
}

/**
 * Generic Document Content Block for structured vector rendering.
 */
sealed class ContentBlock : Serializable {
    data class Heading(val text: String, val level: Int = 1) : ContentBlock()
    data class Paragraph(val text: String, val isBold: Boolean = false, val isItalic: Boolean = false) : ContentBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ContentBlock()
    data class KeyValue(val key: String, val value: String) : ContentBlock()
    data class Slide(val title: String, val bullets: List<String>) : ContentBlock()
}

/**
 * Document Synthesis Manifest.
 */
data class DocumentPayload(
    val title: String,
    val author: String = "Ikbal Fadilah",
    val timestamp: String = "",
    val blocks: List<ContentBlock> = emptyList()
) : Serializable
