package com.example.spreadsheet

import com.example.model.SpreadsheetWorkbook
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

object XlsxEngine {

    /**
     * Converts a 0-based column index to an Excel column letter (0 -> A, 25 -> Z, 26 -> AA).
     */
    fun columnIndexToLetter(colIndex: Int): String {
        var num = colIndex
        val result = StringBuilder()
        while (num >= 0) {
            result.insert(0, ('A'.code + (num % 26)).toChar())
            num = (num / 26) - 1
        }
        return result.toString()
    }

    /**
     * Escapes XML special characters for safe inclusion in OpenXML strings.
     */
    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Generates a fully compliant, production-grade Office Open XML (.xlsx) file in bytes.
     */
    fun generateXlsxBytes(workbook: SpreadsheetWorkbook): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 3. xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 4. xl/styles.xml
            zos.putNextEntry(ZipEntry("xl/styles.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF059669"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 5. xl/workbook.xml
            val safeSheetName = escapeXml(workbook.sheetName.ifBlank { "Expenses" })
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="$safeSheetName" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 6. xl/worksheets/sheet1.xml
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheetBuilder = StringBuilder()
            sheetBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sheetBuilder.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            sheetBuilder.append("""<sheetData>""")

            // Row 1: Headers (with styled header formatting s="1")
            sheetBuilder.append("""<row r="1">""")
            workbook.headers.forEachIndexed { colIndex, header ->
                val cellRef = "${columnIndexToLetter(colIndex)}1"
                val escapedVal = escapeXml(header)
                sheetBuilder.append("""<c r="$cellRef" t="inlineStr" s="1"><is><t>$escapedVal</t></is></c>""")
            }
            sheetBuilder.append("""</row>""")

            // Data Rows (Starting at row 2)
            workbook.rows.forEachIndexed { rowIndex, rowMap ->
                val excelRowNumber = rowIndex + 2
                sheetBuilder.append("""<row r="$excelRowNumber">""")
                workbook.headers.forEachIndexed { colIndex, header ->
                    val cellRef = "${columnIndexToLetter(colIndex)}$excelRowNumber"
                    val rawValue = rowMap[header] ?: ""
                    val trimmed = rawValue.trim()

                    val numericVal = trimmed.removePrefix("$").toDoubleOrNull()
                    if (numericVal != null && (!trimmed.startsWith("0") || trimmed == "0" || trimmed == "0.00")) {
                        sheetBuilder.append("""<c r="$cellRef"><v>$trimmed</v></c>""")
                    } else {
                        val escaped = escapeXml(rawValue)
                        sheetBuilder.append("""<c r="$cellRef" t="inlineStr"><is><t>$escaped</t></is></c>""")
                    }
                }
                sheetBuilder.append("""</row>""")
            }

            sheetBuilder.append("""</sheetData>""")
            sheetBuilder.append("""</worksheet>""")

            zos.write(sheetBuilder.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return bos.toByteArray()
    }

    /**
     * Parses an uploaded file (XLSX, XLS XML, or CSV) into a SpreadsheetWorkbook.
     */
    fun parseSpreadsheet(inputStream: InputStream, fileName: String): SpreadsheetWorkbook {
        val rawBytes = inputStream.readBytes()

        // Check for ZIP magic numbers (PK..) which signifies modern XLSX
        if (rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()) {
            return try {
                parseXlsxFromBytes(rawBytes, fileName)
            } catch (e: Exception) {
                fallbackFromCsv(rawBytes, fileName)
            }
        }

        // Check if XML Spreadsheet 2003 (.xls)
        val textSnippet = String(rawBytes.take(2048).toByteArray(), Charsets.UTF_8)
        if (textSnippet.contains("Workbook", ignoreCase = true) && textSnippet.contains("<Table", ignoreCase = true)) {
            return try {
                parseSpreadsheetML(rawBytes, fileName)
            } catch (e: Exception) {
                fallbackFromCsv(rawBytes, fileName)
            }
        }

        // Fallback to CSV / Delimited text parsing
        return fallbackFromCsv(rawBytes, fileName)
    }

    private fun parseXlsxFromBytes(bytes: ByteArray, fileName: String): SpreadsheetWorkbook {
        val sharedStrings = mutableListOf<String>()
        var sheetXml: String? = null

        // First pass: extract shared strings and worksheet xml
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (name.equals("xl/sharedStrings.xml", ignoreCase = true)) {
                    val content = zis.readBytes().toString(Charsets.UTF_8)
                    parseSharedStrings(content, sharedStrings)
                } else if (name.equals("xl/worksheets/sheet1.xml", ignoreCase = true)) {
                    sheetXml = zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }

        if (sheetXml == null) {
            throw IllegalArgumentException("Could not locate sheet1.xml inside XLSX archive")
        }

        return parseWorksheetXml(sheetXml!!, sharedStrings, fileName)
    }

    private fun parseSharedStrings(xmlContent: String, outputList: MutableList<String>) {
        try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
            val tNodes = doc.getElementsByTagName("t")
            for (i in 0 until tNodes.length) {
                outputList.add(tNodes.item(i).textContent ?: "")
            }
        } catch (_: Exception) {}
    }

    private fun parseWorksheetXml(
        sheetXml: String,
        sharedStrings: List<String>,
        fileName: String
    ): SpreadsheetWorkbook {
        val allRows = mutableListOf<List<String>>()
        try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(sheetXml.toByteArray(Charsets.UTF_8)))
            val rowNodes = doc.getElementsByTagName("row")

            for (i in 0 until rowNodes.length) {
                val rowElement = rowNodes.item(i) as? Element ?: continue
                val cNodes = rowElement.getElementsByTagName("c")
                val currentRow = mutableListOf<String>()

                for (j in 0 until cNodes.length) {
                    val cElement = cNodes.item(j) as? Element ?: continue
                    val cellType = cElement.getAttribute("t")
                    val value = when (cellType) {
                        "s" -> {
                            val vNodes = cElement.getElementsByTagName("v")
                            val rawIdx = if (vNodes.length > 0) vNodes.item(0).textContent?.trim() else ""
                            val idx = rawIdx?.toIntOrNull()
                            if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else (rawIdx ?: "")
                        }
                        "inlineStr" -> {
                            val tNodes = cElement.getElementsByTagName("t")
                            if (tNodes.length > 0) tNodes.item(0).textContent ?: "" else ""
                        }
                        else -> {
                            val vNodes = cElement.getElementsByTagName("v")
                            if (vNodes.length > 0) {
                                vNodes.item(0).textContent ?: ""
                            } else {
                                val tNodes = cElement.getElementsByTagName("t")
                                if (tNodes.length > 0) tNodes.item(0).textContent ?: "" else ""
                            }
                        }
                    }
                    currentRow.add(value.trim())
                }

                if (currentRow.isNotEmpty()) {
                    allRows.add(currentRow)
                }
            }
        } catch (_: Exception) {}

        if (allRows.isEmpty()) {
            return DefaultTemplates.BusinessExpenses.let {
                SpreadsheetWorkbook(
                    name = fileName,
                    headers = it.columns,
                    rows = emptyList(),
                    isCustomUploaded = true
                )
            }
        }

        val headers = allRows.first().mapIndexed { i, h ->
            h.ifBlank { "Column ${i + 1}" }
        }

        val dataRows = allRows.drop(1).map { rowValues ->
            headers.mapIndexed { idx, header ->
                header to (rowValues.getOrNull(idx) ?: "")
            }.toMap()
        }

        return SpreadsheetWorkbook(
            name = fileName,
            headers = headers,
            rows = dataRows,
            isCustomUploaded = true
        )
    }

    private fun parseSpreadsheetML(bytes: ByteArray, fileName: String): SpreadsheetWorkbook {
        val allRows = mutableListOf<List<String>>()
        try {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(bytes))
            val rowNodes = doc.getElementsByTagName("Row")

            for (i in 0 until rowNodes.length) {
                val rowElement = rowNodes.item(i) as? Element ?: continue
                val cellNodes = rowElement.getElementsByTagName("Cell")
                val currentRow = mutableListOf<String>()

                for (j in 0 until cellNodes.length) {
                    val cellElement = cellNodes.item(j) as? Element ?: continue
                    val dataNodes = cellElement.getElementsByTagName("Data")
                    val text = if (dataNodes.length > 0) dataNodes.item(0).textContent ?: "" else ""
                    currentRow.add(text.trim())
                }
                if (currentRow.isNotEmpty()) {
                    allRows.add(currentRow)
                }
            }
        } catch (_: Exception) {
            return fallbackFromCsv(bytes, fileName)
        }

        if (allRows.isEmpty()) return fallbackFromCsv(bytes, fileName)

        val headers = allRows.first().mapIndexed { i, h -> h.ifBlank { "Column ${i + 1}" } }
        val rows = allRows.drop(1).map { row ->
            headers.mapIndexed { idx, header ->
                header to (row.getOrNull(idx) ?: "")
            }.toMap()
        }

        return SpreadsheetWorkbook(
            name = fileName,
            headers = headers,
            rows = rows,
            isCustomUploaded = true
        )
    }

    private fun fallbackFromCsv(bytes: ByteArray, fileName: String): SpreadsheetWorkbook {
        val lines = String(bytes, Charsets.UTF_8).lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return DefaultTemplates.BusinessExpenses.let {
                SpreadsheetWorkbook(
                    name = fileName,
                    headers = it.columns,
                    rows = emptyList(),
                    isCustomUploaded = true
                )
            }
        }

        // Detect delimiter (comma, tab, semicolon)
        val firstLine = lines.first()
        val delimiter = when {
            firstLine.contains("\t") -> "\t"
            firstLine.contains(";") -> ";"
            else -> ","
        }

        val headers = parseDelimitedLine(firstLine, delimiter).mapIndexed { i, h ->
            h.ifBlank { "Column ${i + 1}" }
        }

        val dataRows = lines.drop(1).map { line ->
            val cells = parseDelimitedLine(line, delimiter)
            headers.mapIndexed { idx, header ->
                header to (cells.getOrNull(idx) ?: "")
            }.toMap()
        }

        return SpreadsheetWorkbook(
            name = fileName,
            headers = headers,
            rows = dataRows,
            isCustomUploaded = true
        )
    }

    private fun parseDelimitedLine(line: String, delimiter: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c.toString() == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim().removeSurrounding("\""))
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        tokens.add(sb.toString().trim().removeSurrounding("\""))
        return tokens
    }
}
