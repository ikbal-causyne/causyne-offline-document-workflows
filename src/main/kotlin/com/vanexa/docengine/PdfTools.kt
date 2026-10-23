package com.vanexa.docengine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * PdfTools — experimental document-stream and pagination helpers.
 *
 * The stream operation below copies bytes. It does not compress a PDF.
 *
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
object PdfTools {

    private const val MAX_RECOMMENDED_FILE_SIZE = 50 * 1024 * 1024L // 50 MB

    /**
     * Calculates the memory allocation chunk size based on available system heap.
     * Prevents process death from Dalvik/ART Out-Of-Memory (OOM) errors:
     *
     * B_alloc = min(M_max_chunk, RAM_avail * phi)
     */
    fun calculateMemoryChunkAllocation(availableRamBytes: Long, phi: Double = 0.20): Long {
        val maxChunk = 8 * 1024 * 1024L // 8 MB max in RAM
        val safetyAllocation = (availableRamBytes * phi).toLong()
        return minOf(maxChunk, maxOf(1024 * 1024L, safetyAllocation))
    }

    /**
     * Calculates compression percentage ratio:
     * R_comp = (Size_initial - Size_optimized) / Size_initial * 100%
     */
    fun calculateCompressionRatio(initialBytes: Long, optimizedBytes: Long): Double {
        if (initialBytes <= 0) return 0.0
        val saved = (initialBytes - optimizedBytes).coerceAtLeast(0)
        return (saved.toDouble() / initialBytes.toDouble()) * 100.0
    }

    /** Copies a document stream without changing its PDF structure or size. */
    fun copyPdfStream(
        inputFile: File,
        outputFile: File
    ): Flow<ProgressUpdate> = flow {
        val startTime = System.currentTimeMillis()
        emit(ProgressUpdate.Loading("Copying document..."))

        if (!inputFile.exists()) {
            emit(ProgressUpdate.Error("Input file does not exist: ${inputFile.absolutePath}"))
            return@flow
        }

        val initialSize = inputFile.length()
        emit(ProgressUpdate.Progress(15, "Opening document stream..."))
        inputFile.inputStream().use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        emit(ProgressUpdate.Progress(75, "Document copied..."))

        val finalSize = outputFile.length()
        val durationMs = System.currentTimeMillis() - startTime

        emit(
            ProgressUpdate.Success(
                outputFile = outputFile,
                originalBytes = initialSize,
                finalBytes = finalSize,
                durationMs = durationMs
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Layout pagination computation: partitions text spans into fixed-dimension pages.
     */
    fun paginateBlocks(
        blocks: List<ContentBlock>,
        pageHeightPx: Int = 842, // Standard A4 at 72 DPI
        marginPx: Int = 40
    ): List<List<ContentBlock>> {
        val usableHeight = pageHeightPx - (2 * marginPx)
        val pages = mutableListOf<MutableList<ContentBlock>>()
        var currentPage = mutableListOf<ContentBlock>()
        var currentHeight = 0

        for (block in blocks) {
            val estimatedBlockHeight = when (block) {
                is ContentBlock.Heading -> 50
                is ContentBlock.Paragraph -> 30 + (block.text.length / 60) * 18
                is ContentBlock.Table -> 40 + (block.rows.size * 25)
                is ContentBlock.KeyValue -> 24
                is ContentBlock.Slide -> 120
            }

            if (currentHeight + estimatedBlockHeight > usableHeight && currentPage.isNotEmpty()) {
                pages.add(currentPage)
                currentPage = mutableListOf()
                currentHeight = 0
            }

            currentPage.add(block)
            currentHeight += estimatedBlockHeight
        }

        if (currentPage.isNotEmpty()) {
            pages.add(currentPage)
        }

        return pages
    }
}
