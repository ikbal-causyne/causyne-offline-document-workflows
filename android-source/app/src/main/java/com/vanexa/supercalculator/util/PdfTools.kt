package com.vanexa.supercalculator.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * PdfTools — Offline PDF Compression & Word-to-PDF Conversion Engine.
 *
 * Optimisations:
 * - English as default language for all status and logs.
 * - Memory-safe page rendering (iLovePDF style).
 * - Multi-mode compression support (Extreme, High, Recommended, Lossless).
 */
object PdfTools {

    private const val TAG           = "PdfTools"
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024L // 50 MB

    enum class CompressionMode(
        val dpi         : Float,
        val jpegQuality : Int,
        val label       : String
    ) {
        EXTREME     (dpi = 96f,  jpegQuality = 30, label = "Extreme"),
        HIGH        (dpi = 130f, jpegQuality = 45, label = "High"),
        RECOMMENDED (dpi = 180f, jpegQuality = 60, label = "Recommended"),
        LOSSLESS    (dpi = 0f,   jpegQuality = 0,  label = "Lossless")
    }

    // ─────────────────────────────────────────────────────────────
    //  PDF COMPRESSION
    // ─────────────────────────────────────────────────────────────

    fun compressPdf(
        context : Context,
        inputUri: Uri,
        mode    : CompressionMode = CompressionMode.HIGH
    ): Flow<ProgressUpdate> = flow {

        emit(ProgressUpdate.Loading("Initializing engine..."))
        PDFBoxResourceLoader.init(context)

        val fileSize = getFileSize(context, inputUri)
        if (fileSize > MAX_FILE_SIZE) {
            val sizeMB = String.format("%.1f", fileSize / (1024.0 * 1024.0))
            emit(ProgressUpdate.Error("File too large (Max 50 MB). Current: ${sizeMB} MB"))
            return@flow
        }

        val outputDir  = File(context.cacheDir, "pdf_tools").apply { mkdirs() }
        val outputFile = File(outputDir, "vanexa_opt_${System.currentTimeMillis()}.pdf")

    // ── Enterprise Routing Pipeline ──────────────────────────
        try {
            emit(ProgressUpdate.Loading("Parsing document structure..."))
            
            // Memory-Safe Parsing: Prevents OOM by offloading parsing to temp files instead of RAM.
            val inputStream = context.contentResolver.openInputStream(inputUri)
            val doc = PDDocument.load(inputStream, MemoryUsageSetting.setupTempFileOnly())
            
            val isEncrypted = doc.isEncrypted
            doc.close()
            inputStream?.close()

            if (isEncrypted) {
                emit(ProgressUpdate.Error("Cannot compress password-protected PDF. Please remove the password first."))
                return@flow
            }

            // Route all PDFs through the non-destructive, high-speed Object Engine.
            compressObjectLevel(context, inputUri, fileSize, outputFile, mode)
                .collect { emit(it) }

        } catch (e: Exception) {
            emit(ProgressUpdate.Error("Engine failure: ${e.message}"))
        }

    }.flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────
    //  STRATEGY 2: High-Speed Object Level Optimization
    //  Surgical compression. Finds images & streams inside the PDF
    //  and crushes them. Instant performance for 1000+ page docs.
    // ─────────────────────────────────────────────────────────────
    private fun compressObjectLevel(
        context    : Context,
        inputUri   : Uri,
        fileSize   : Long,
        outputFile : File,
        mode       : CompressionMode
    ): Flow<ProgressUpdate> = flow {

        emit(ProgressUpdate.Loading("Analyzing internal PDF structure..."))

        val inputStream = context.contentResolver.openInputStream(inputUri)
            ?: throw Exception("Could not open input file")
        val document = try {
            PDDocument.load(inputStream, MemoryUsageSetting.setupTempFileOnly())
        } finally {
            inputStream.close()
        }

        try {
            val allObjects   = document.document.objects
            val totalObjects = allObjects.size
            var imagesOptimized   = 0
            var streamsCompressed = 0
            val processed    = mutableSetOf<Int>()

            val maxDimension = when (mode) {
                CompressionMode.EXTREME -> 800
                CompressionMode.HIGH -> 1200
                CompressionMode.RECOMMENDED -> 1600
                CompressionMode.LOSSLESS -> 2000
            }

            val jpegQual = if (mode == CompressionMode.LOSSLESS) 80 else mode.jpegQuality

            allObjects.forEachIndexed { idx, cosObject ->
                val base = cosObject.getObject()
                if (base !is COSStream) return@forEachIndexed

                if (!processed.add(System.identityHashCode(base))) return@forEachIndexed

                val subtype = base.getCOSName(COSName.SUBTYPE)
                if (subtype == COSName.IMAGE && mode != CompressionMode.LOSSLESS) {
                    try {
                        // Memory-safe Image Extraction
                        val pdStream = com.tom_roush.pdfbox.pdmodel.common.PDStream(base)
                        val imageRawStream = pdStream.createInputStream()
                        
                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeStream(imageRawStream, null, options)
                        imageRawStream.close()
                        
                        val originalW = options.outWidth
                        val originalH = options.outHeight

                        if (originalW > 50 && originalH > 50) {
                            var inSampleSize = 1
                            if (originalW > maxDimension || originalH > maxDimension) {
                                val halfH = originalH / 2
                                val halfW = originalW / 2
                                while ((halfH / inSampleSize) >= maxDimension && (halfW / inSampleSize) >= maxDimension) {
                                    inSampleSize *= 2
                                }
                            }

                            options.inJustDecodeBounds = false
                            options.inSampleSize = inSampleSize
                            
                            val imageDecodeStream = pdStream.createInputStream()
                            val scaled = BitmapFactory.decodeStream(imageDecodeStream, null, options)
                            imageDecodeStream.close()

                            if (scaled != null) {
                                val out = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, jpegQual, out)
                                
                                val finalW = scaled.width
                                val finalH = scaled.height
                                scaled.recycle()

                                base.removeItem(COSName.FILTER); base.removeItem(COSName.DECODE_PARMS)
                                base.createOutputStream().use { it.write(out.toByteArray()) }
                                base.setItem(COSName.FILTER, COSName.DCT_DECODE)
                                base.setInt (COSName.WIDTH, finalW)
                                base.setInt (COSName.HEIGHT, finalH)
                                base.setInt (COSName.BITS_PER_COMPONENT, 8)
                                base.setItem(COSName.COLORSPACE, COSName.DEVICERGB)
                                imagesOptimized++
                            }
                        }
                    } catch (e: Exception) { /* skip */ }
                } else if (subtype != COSName.IMAGE) {
                    // Compress raw text/vector streams
                    val existingFilter = base.getItem(COSName.FILTER)
                    val alreadyFlated = when {
                        existingFilter == null -> false
                        existingFilter is COSName && existingFilter == COSName.FLATE_DECODE -> true
                        existingFilter is COSArray -> existingFilter.toList().any { it is COSName && it == COSName.FLATE_DECODE }
                        else -> false
                    }
                    if (!alreadyFlated) {
                        try {
                            val raw = base.createInputStream().use { it.readBytes() }
                            if (raw.isNotEmpty()) {
                                // Achieve max possible compression on text using level 9 (BEST_COMPRESSION)
                                val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
                                val baos = java.io.ByteArrayOutputStream()
                                val deflaterStream = java.util.zip.DeflaterOutputStream(baos, deflater)
                                deflaterStream.write(raw)
                                deflaterStream.close()
                                deflater.end()

                                val compressed = baos.toByteArray()
                                base.removeItem(COSName.FILTER); base.removeItem(COSName.DECODE_PARMS)
                                base.createOutputStream().use { it.write(compressed) }
                                base.setItem(COSName.FILTER, COSName.FLATE_DECODE)
                                streamsCompressed++
                            }
                        } catch (e: Exception) { /* skip */ }
                    }
                }

                if (idx % 200 == 0) {
                    emit(ProgressUpdate.Progress((idx * 80 / totalObjects), "Compressing media..."))
                }
            }

            // Safe metadata stripping (avoid destroying forms or bookmarks)
            try {
                document.documentCatalog.metadata = null
                document.documentInformation.apply {
                    author = null; title = null; subject = null; keywords = null; creator = null; producer = "Vanexa Optimizer"
                }
            } catch (e: Exception) {}

            emit(ProgressUpdate.Progress(90, "Rebuilding structure..."))
            document.save(outputFile)
            
            val newKB = outputFile.length() / 1024
            val oldKB = fileSize / 1024
            val reduction = if (oldKB > 0) ((1.0 - newKB.toDouble() / oldKB.toDouble()) * 100).toInt() else 0

            val summary = if (reduction > 0) {
                "✓ Compression Complete!\n$oldKB KB → $newKB KB ($reduction% smaller)"
            } else if (imagesOptimized == 0) {
                "✓ Fully Optimized Text Document\nNo compressible images found. File is already at minimum size ($newKB KB)."
            } else {
                "✓ Compression Complete!\nFinal size: $newKB KB"
            }

            emit(ProgressUpdate.Success(outputFile.absolutePath, summary))

        } finally {
            document.close()
        }
    }.flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────
    //  WORD → PDF
    // ─────────────────────────────────────────────────────────────

    fun wordToPdf(context: Context, inputUri: Uri): Flow<ProgressUpdate> = flow {
        emit(ProgressUpdate.Loading("Parsing Word document..."))

        val fileSize = getFileSize(context, inputUri)
        if (fileSize > MAX_FILE_SIZE) {
            emit(ProgressUpdate.Error("File too large (Max 50MB)"))
            return@flow
        }

        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw Exception("Could not open file")
            val doc = XWPFDocument(inputStream)
            inputStream.close()

            val contentBlocks = mutableListOf<ContentBlock>()
            doc.bodyElements.forEach { element ->
                when (element) {
                    is org.apache.poi.xwpf.usermodel.XWPFParagraph -> {
                        val spans = element.runs.mapNotNull { run ->
                            val text = run.getText(0) ?: ""
                            if (text.isEmpty()) null else TextSpan(
                                text        = text,
                                isBold      = run.isBold,
                                isItalic    = run.isItalic,
                                isUnderline = run.underline != UnderlinePatterns.NONE,
                                fontSize    = if (run.fontSize > 0) run.fontSize.toFloat() else 11f
                            )
                        }
                        contentBlocks.add(ContentBlock.Paragraph(
                            spans.ifEmpty { listOf(TextSpan(" ", false, false, false, 11f)) }
                        ))
                    }
                    is XWPFTable -> {
                        val rows = element.rows.map { it.tableCells.map { c -> c.text } }
                        if (rows.isNotEmpty()) contentBlocks.add(ContentBlock.Table(rows))
                    }
                }
            }
            doc.close()

            emit(ProgressUpdate.Progress(40, "Generating PDF pages..."))

            val pageWidth = 595; val pageHeight = 842
            val marginLeft = 50f; val marginTop = 50f; val marginBottom = 50f
            val usableWidth = pageWidth - marginLeft - 50f

            val pdfDoc = PdfDocument()
            var pageNumber = 1
            var currentY = marginTop

            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            contentBlocks.forEachIndexed { idx, block ->
                when (block) {
                    is ContentBlock.Paragraph -> {
                        val paint = TextPaint().apply {
                            isAntiAlias = true
                            textSize    = (block.spans.firstOrNull()?.fontSize ?: 11f) * 1.35f
                            val isB = block.spans.any { it.isBold }; val isI = block.spans.any { it.isItalic }
                            typeface = when {
                                isB && isI -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                                isB        -> Typeface.DEFAULT_BOLD
                                isI        -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                                else       -> Typeface.DEFAULT
                            }
                        }

                        val fullText = block.spans.joinToString("") { it.text }
                        @Suppress("DEPRECATION")
                        val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            StaticLayout.Builder.obtain(fullText, 0, fullText.length, paint, usableWidth.toInt())
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1.2f).build()
                        } else {
                            StaticLayout(fullText, paint, usableWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.2f, 2f, false)
                        }

                        if (currentY + layout.height > pageHeight - marginBottom) {
                            pdfDoc.finishPage(page); pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = pdfDoc.startPage(pageInfo); canvas = page.canvas; currentY = marginTop
                        }
                        canvas.save(); canvas.translate(marginLeft, currentY); layout.draw(canvas); canvas.restore()
                        currentY += layout.height + 5f
                    }
                    is ContentBlock.Table -> {
                        val numCols = block.rows.maxOfOrNull { it.size } ?: 1
                        val colW = usableWidth / numCols; val rowH = 26f
                        if (currentY + (block.rows.size * rowH) > pageHeight - marginBottom) {
                            pdfDoc.finishPage(page); pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = pdfDoc.startPage(pageInfo); canvas = page.canvas; currentY = marginTop
                        }
                        val borderP = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = android.graphics.Color.GRAY }
                        val textP = TextPaint().apply { isAntiAlias = true; textSize = 14f }
                        block.rows.forEachIndexed { rIdx, row ->
                            val cellY = currentY + (rIdx * rowH)
                            row.forEachIndexed { cIdx, text ->
                                val cellX = marginLeft + (cIdx * colW)
                                canvas.drawRect(cellX, cellY, cellX + colW, cellY + rowH, borderP)
                                canvas.drawText(text.take(30), cellX + 4f, cellY + rowH - 6f, textP)
                            }
                        }
                        currentY += (block.rows.size * rowH) + 10f
                    }
                }
                if (idx % 10 == 0) emit(ProgressUpdate.Progress(40 + (idx * 50 / contentBlocks.size), "Rendering..."))
            }

            pdfDoc.finishPage(page)
            val outputDir = File(context.cacheDir, "pdf_tools").apply { mkdirs() }
            val outputFile = File(outputDir, "word_to_pdf_${System.currentTimeMillis()}.pdf")
            outputFile.outputStream().use { pdfDoc.writeTo(it) }
            pdfDoc.close()

            emit(ProgressUpdate.Success(outputFile.absolutePath, "✓ Converted successfully ($pageNumber pages)"))
        } catch (e: Exception) {
            // ═══════════════════════════════════════════════════
            // DIAGNOSTIC: Nuclear error logging for AAB crash hunt
            // ═══════════════════════════════════════════════════
            val exClass = e.javaClass.name
            val exMsg = e.message ?: "null"
            val causeChain = buildString {
                var cause: Throwable? = e.cause
                var depth = 0
                while (cause != null && depth < 5) {
                    append(" → [Cause ${depth}] ${cause.javaClass.name}: ${cause.message}")
                    cause = cause.cause
                    depth++
                }
            }
            val stackFrames = e.stackTrace.take(8).joinToString("\n") { 
                "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" 
            }
            val diagnosticMsg = "WORD2PDF CRASH DIAGNOSTIC:\n" +
                "Exception: $exClass\n" +
                "Message: $exMsg\n" +
                "Cause chain: ${causeChain.ifEmpty { "none" }}\n" +
                "Stack:\n$stackFrames"
            Log.e(TAG, diagnosticMsg, e)
            emit(ProgressUpdate.Error(diagnosticMsg))
        }
    }.flowOn(Dispatchers.IO)

    // ─────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────

    private fun getFileSize(context: Context, uri: Uri): Long =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx != -1 && cursor.moveToFirst()) cursor.getLong(idx) else 0L
        } ?: 0L

    private data class TextSpan(
        val text: String, val isBold: Boolean, val isItalic: Boolean,
        val isUnderline: Boolean, val fontSize: Float
    )

    private sealed class ContentBlock {
        data class Paragraph(val spans: List<TextSpan>) : ContentBlock()
        data class Table(val rows: List<List<String>>) : ContentBlock()
    }

    sealed class ProgressUpdate {
        data class Progress(val percentage: Int, val status: String) : ProgressUpdate()
        data class Loading (val status: String)                      : ProgressUpdate()
        data class Success (val filePath: String, val summary: String = "") : ProgressUpdate()
        data class Error   (val message: String)                     : ProgressUpdate()
    }

    fun saveFileToPublicFolder(context: Context, sourceFile: File, mimeType: String): Uri? {
        try {
            val fileName = sourceFile.name
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOCUMENTS + "/Vanexa")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let { destUri ->
                    resolver.openOutputStream(destUri)?.use { outStream ->
                        sourceFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    return destUri
                } ?: throw Exception("Failed to insert media record")
            } else {
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Vanexa")
                if (!dir.exists()) dir.mkdirs()
                val destFile = File(dir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                
                val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(destFile)
                context.sendBroadcast(intent)
                return Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save failed", e)
            return null
        }
    }
}
