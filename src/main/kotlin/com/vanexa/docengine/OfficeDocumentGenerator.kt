package com.vanexa.docengine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * OfficeDocumentGenerator — Zero-Dependency OpenXML Document Synthesizer.
 *
 * Compiles standard Microsoft Word (.docx), Excel (.xlsx), and PowerPoint (.pptx)
 * archives directly on Android / edge clients without desktop Java AWT dependencies.
 *
 * Author: Ikbal Fadilah (Causyne)
 * License: MIT
 */
class OfficeDocumentGenerator {

    /**
     * Synthesizes an ISO/IEC 29500 compliant Word Document (.docx).
     */
    fun generateDocx(payload: DocumentPayload, outputFile: File) {
        val zipStream = ZipOutputStream(FileOutputStream(outputFile))

        // 1. [Content_Types].xml
        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
        writeZipEntry(zipStream, "[Content_Types].xml", contentTypesXml)

        // 2. _rels/.rels
        val packageRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
        writeZipEntry(zipStream, "_rels/.rels", packageRelsXml)

        // 3. word/document.xml
        val docSb = StringBuilder()
        docSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:body>
""")

        // Title Paragraph
        docSb.append("""        <w:p>
            <w:pPr><w:jc w:val="center"/></w:pPr>
            <w:r><w:rPr><w:b/><w:sz w:val="48"/></w:rPr><w:t>${escapeXml(payload.title)}</w:t></w:r>
        </w:p>
""")

        for (block in payload.blocks) {
            when (block) {
                is ContentBlock.Heading -> {
                    docSb.append("""        <w:p>
            <w:r><w:rPr><w:b/><w:sz w:val="32"/></w:rPr><w:t>${escapeXml(block.text)}</w:t></w:r>
        </w:p>
""")
                }
                is ContentBlock.Paragraph -> {
                    docSb.append("""        <w:p>
            <w:r><w:t>${escapeXml(block.text)}</w:t></w:r>
        </w:p>
""")
                }
                is ContentBlock.Table -> {
                    docSb.append("""        <w:tbl>
            <w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4"/><w:left w:val="single" w:sz="4"/><w:bottom w:val="single" w:sz="4"/><w:right w:val="single" w:sz="4"/><w:insideH w:val="single" w:sz="4"/><w:insideV w:val="single" w:sz="4"/></w:tblBorders></w:tblPr>
""")
                    // Header row
                    if (block.headers.isNotEmpty()) {
                        docSb.append("            <w:tr>\n")
                        for (h in block.headers) {
                            docSb.append("                <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>${escapeXml(h)}</w:t></w:r></w:p></w:tc>\n")
                        }
                        docSb.append("            </w:tr>\n")
                    }
                    // Data rows
                    for (row in block.rows) {
                        docSb.append("            <w:tr>\n")
                        for (cell in row) {
                            docSb.append("                <w:tc><w:p><w:r><w:t>${escapeXml(cell)}</w:t></w:r></w:p></w:tc>\n")
                        }
                        docSb.append("            </w:tr>\n")
                    }
                    docSb.append("        </w:tbl>\n")
                }
                is ContentBlock.KeyValue -> {
                    docSb.append("""        <w:p>
            <w:r><w:rPr><w:b/></w:rPr><w:t>${escapeXml(block.key)}: </w:t></w:r>
            <w:r><w:t>${escapeXml(block.value)}</w:t></w:r>
        </w:p>
""")
                }
                is ContentBlock.Slide -> {
                    docSb.append("""        <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Slide: ${escapeXml(block.title)}</w:t></w:r></w:p>
""")
                    for (b in block.bullets) {
                        docSb.append("""        <w:p><w:r><w:t>  • ${escapeXml(b)}</w:t></w:r></w:p>
""")
                    }
                }
            }
        }

        docSb.append("""        <w:sectPr/>
    </w:body>
</w:document>""")
        writeZipEntry(zipStream, "word/document.xml", docSb.toString())

        zipStream.close()
    }

    /**
     * Synthesizes an ISO/IEC 29500 compliant Excel Spreadsheet (.xlsx).
     */
    fun generateXlsx(table: ContentBlock.Table, outputFile: File, sheetTitle: String = "Sheet1") {
        val zipStream = ZipOutputStream(FileOutputStream(outputFile))

        // 1. [Content_Types].xml
        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
        writeZipEntry(zipStream, "[Content_Types].xml", contentTypesXml)

        // 2. _rels/.rels
        val packageRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        writeZipEntry(zipStream, "_rels/.rels", packageRelsXml)

        // 3. xl/_rels/workbook.xml.rels
        val workbookRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
        writeZipEntry(zipStream, "xl/_rels/workbook.xml.rels", workbookRelsXml)

        // 4. xl/workbook.xml
        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <sheets>
        <sheet name="${escapeXml(sheetTitle)}" sheetId="1" r:id="rId1"/>
    </sheets>
</workbook>"""
        writeZipEntry(zipStream, "xl/workbook.xml", workbookXml)

        // 5. xl/worksheets/sheet1.xml
        val sheetSb = StringBuilder()
        sheetSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <sheetData>
""")

        var rowIndex = 1

        // Headers
        if (table.headers.isNotEmpty()) {
            sheetSb.append("        <row r=\"$rowIndex\">\n")
            table.headers.forEachIndexed { colIdx, header ->
                val colLetter = getColumnLetter(colIdx + 1)
                sheetSb.append("            <c r=\"$colLetter$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(header)}</t></is></c>\n")
            }
            sheetSb.append("        </row>\n")
            rowIndex++
        }

        // Data Rows
        for (row in table.rows) {
            sheetSb.append("        <row r=\"$rowIndex\">\n")
            row.forEachIndexed { colIdx, value ->
                val colLetter = getColumnLetter(colIdx + 1)
                val numericVal = value.toDoubleOrNull()
                if (numericVal != null) {
                    sheetSb.append("            <c r=\"$colLetter$rowIndex\"><v>$numericVal</v></c>\n")
                } else {
                    sheetSb.append("            <c r=\"$colLetter$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(value)}</t></is></c>\n")
                }
            }
            sheetSb.append("        </row>\n")
            rowIndex++
        }

        sheetSb.append("""    </sheetData>
</worksheet>""")
        writeZipEntry(zipStream, "xl/worksheets/sheet1.xml", sheetSb.toString())

        zipStream.close()
    }

    /**
     * Synthesizes an ISO/IEC 29500 compliant PowerPoint Presentation (.pptx).
     */
    fun generatePptx(slides: List<ContentBlock.Slide>, outputFile: File) {
        val zipStream = ZipOutputStream(FileOutputStream(outputFile))

        // 1. [Content_Types].xml
        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
    <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
</Types>"""
        writeZipEntry(zipStream, "[Content_Types].xml", contentTypesXml)

        // 2. _rels/.rels
        val packageRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
        writeZipEntry(zipStream, "_rels/.rels", packageRelsXml)

        // 3. ppt/_rels/presentation.xml.rels
        val presRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
</Relationships>"""
        writeZipEntry(zipStream, "ppt/_rels/presentation.xml.rels", presRelsXml)

        // 4. ppt/presentation.xml
        val presXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <p:sldIdLst>
        <p:sldId id="256" r:id="rId1"/>
    </p:sldIdLst>
    <p:sldSz cx="9144000" cy="5143500"/>
</p:presentation>"""
        writeZipEntry(zipStream, "ppt/presentation.xml", presXml)

        // 5. ppt/slides/slide1.xml
        val firstSlide = slides.firstOrNull() ?: ContentBlock.Slide("Causyne Presentation", listOf("Exported directly on device"))
        val slideSb = StringBuilder()
        slideSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
    <p:cSld>
        <p:spTree>
            <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
            <p:grpSpPr/>
            <p:sp>
                <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr><a:xfrm><a:off x="457200" y="274320"/><a:ext cx="8229600" cy="1143000"/></a:xfrm></p:spPr>
                <p:txBody><a:bodyPr/><a:p><a:r><a:rPr lang="en-US" b="1" sz="4000"/><a:t>${escapeXml(firstSlide.title)}</a:t></a:r></a:p></p:txBody>
            </p:sp>
            <p:sp>
                <p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr><a:xfrm><a:off x="457200" y="1600200"/><a:ext cx="8229600" cy="3000000"/></a:xfrm></p:spPr>
                <p:txBody><a:bodyPr/>
""")
        for (bullet in firstSlide.bullets) {
            slideSb.append("""                    <a:p><a:r><a:rPr lang="en-US" sz="2400"/><a:t>• ${escapeXml(bullet)}</a:t></a:r></a:p>
""")
        }
        slideSb.append("""                </p:txBody>
            </p:sp>
        </p:spTree>
    </p:cSld>
</p:sld>""")
        writeZipEntry(zipStream, "ppt/slides/slide1.xml", slideSb.toString())

        zipStream.close()
    }

    private fun writeZipEntry(zip: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun getColumnLetter(colNum: Int): String {
        var num = colNum
        val sb = StringBuilder()
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.append(('A'.code + rem).toChar())
            num = (num - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
