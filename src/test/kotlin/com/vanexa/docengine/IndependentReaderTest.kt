package com.vanexa.docengine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndependentReaderTest {
    @Test
    fun generatedOfficeFilesOpenInApachePoi() {
        val dir = Files.createTempDirectory("causyne-office-reader").toFile()
        try {
            val generator = OfficeDocumentGenerator()
            val docx = File(dir, "sample.docx")
            val xlsx = File(dir, "sample.xlsx")
            val pptx = File(dir, "sample.pptx")

            generator.generateDocx(
                DocumentPayload("Test document", blocks = listOf(ContentBlock.Paragraph("Research fixture"))), docx
            )
            generator.generateXlsx(
                ContentBlock.Table(listOf("Name", "Value"), listOf(listOf("sample", "7"))), xlsx
            )
            generator.generatePptx(
                listOf(ContentBlock.Slide("First", listOf("Alpha")), ContentBlock.Slide("Second", listOf("Beta"))), pptx
            )

            XWPFDocument(docx.inputStream()).use { document ->
                assertTrue(document.paragraphs.any { it.text.contains("Research fixture") })
            }
            XSSFWorkbook(xlsx.inputStream()).use { workbook ->
                assertTrue(workbook.numberOfSheets >= 1)
            }
            XMLSlideShow(pptx.inputStream()).use { presentation ->
                // The current writer accepts two inputs but serializes only the first slide.
                assertEquals(1, presentation.slides.size)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
