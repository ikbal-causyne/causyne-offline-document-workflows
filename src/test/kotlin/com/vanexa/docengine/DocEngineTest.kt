package com.vanexa.docengine

import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit Tests for the Zero-Cloud Document Synthesis Engine.
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
class DocEngineTest {

    @Test
    fun testMemoryChunkAllocationSafety() {
        // Test with 256 MB available heap
        val chunk256Mb = PdfTools.calculateMemoryChunkAllocation(256 * 1024 * 1024L)
        // Should cap at 8 MB
        assertEquals(8 * 1024 * 1024L, chunk256Mb)

        // Test with constrained 16 MB heap
        val chunk16Mb = PdfTools.calculateMemoryChunkAllocation(16 * 1024 * 1024L)
        // 16MB * 0.20 = 3.2 MB
        assertTrue(chunk16Mb in (3 * 1024 * 1024L)..(4 * 1024 * 1024L))
    }

    @Test
    fun testCompressionRatio() {
        val ratio = PdfTools.calculateCompressionRatio(1000L, 200L)
        assertEquals(80.0, ratio)
    }

    @Test
    fun testMarkdownAndHtmlGeneration() {
        val payload = DocumentPayload(
            title = "Research Audit",
            author = "Ikbal Fadilah",
            timestamp = "2026-10-23",
            blocks = listOf(
                ContentBlock.Heading("Executive Summary", level = 2),
                ContentBlock.Paragraph("This document was generated completely on-device without cloud microservices.", isBold = true),
                ContentBlock.Table(
                    headers = listOf("Metric", "Value"),
                    rows = listOf(
                listOf("Latency", "not measured"),
                        listOf("Privacy", "100% Offline")
                    )
                )
            )
        )

        val md = ExportHelper.generateMarkdown(payload)
        assertTrue(md.contains("# Research Audit"))
        assertTrue(md.contains("Executive Summary"))
        assertTrue(md.contains("| Metric | Value |"))

        val html = ExportHelper.generateHtml(payload)
        assertTrue(html.contains("<h1>Research Audit</h1>"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("not measured"))
    }

    @Test
    fun testDocxAndXlsxGeneration() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "doc_engine_test").apply { mkdirs() }
        val docxFile = File(tempDir, "test_doc.docx")
        val xlsxFile = File(tempDir, "test_sheet.xlsx")
        val pptxFile = File(tempDir, "test_deck.pptx")

        val generator = OfficeDocumentGenerator()

        // 1. Generate DOCX
        val payload = DocumentPayload(
            title = "Offline Generated DOCX",
            blocks = listOf(
                ContentBlock.Paragraph("Clean OpenXML without AWT dependencies."),
                ContentBlock.Table(
                    headers = listOf("A", "B"),
                    rows = listOf(listOf("1", "2"))
                )
            )
        )
        generator.generateDocx(payload, docxFile)
        assertTrue(docxFile.exists())
        assertTrue(docxFile.length() > 0)

        // Verify DOCX is a valid ZIP containing word/document.xml
        ZipFile(docxFile).use { zip ->
            assertNotNull(zip.getEntry("word/document.xml"))
            assertNotNull(zip.getEntry("[Content_Types].xml"))
        }

        // 2. Generate XLSX
        val table = ContentBlock.Table(
            headers = listOf("Item", "Cost"),
            rows = listOf(
                listOf("Local Inference", "0.0"),
                listOf("Total", "0.0")
            )
        )
        generator.generateXlsx(table, xlsxFile)
        assertTrue(xlsxFile.exists())
        ZipFile(xlsxFile).use { zip ->
            assertNotNull(zip.getEntry("xl/worksheets/sheet1.xml"))
            assertNotNull(zip.getEntry("xl/workbook.xml"))
        }

        // 3. Generate PPTX
        val slides = listOf(
            ContentBlock.Slide("Slide 1", listOf("Bullet point 1", "Bullet point 2"))
        )
        generator.generatePptx(slides, pptxFile)
        assertTrue(pptxFile.exists())
        ZipFile(pptxFile).use { zip ->
            assertNotNull(zip.getEntry("ppt/presentation.xml"))
            assertNotNull(zip.getEntry("ppt/slides/slide1.xml"))
        }

        // Cleanup
        docxFile.delete()
        xlsxFile.delete()
        pptxFile.delete()
    }
}
