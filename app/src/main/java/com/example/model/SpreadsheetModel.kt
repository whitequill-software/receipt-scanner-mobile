package com.example.model

data class SpreadsheetWorkbook(
    val name: String,
    val sheetName: String = "Expenses",
    val headers: List<String>,
    val rows: List<Map<String, String>> = emptyList(),
    val isCustomUploaded: Boolean = false
) {
    fun addRow(rowValues: Map<String, String>): SpreadsheetWorkbook {
        return copy(rows = rows + rowValues)
    }

    fun removeRow(index: Int): SpreadsheetWorkbook {
        if (index !in rows.indices) return this
        val mutable = rows.toMutableList()
        mutable.removeAt(index)
        return copy(rows = mutable)
    }

    fun updateRow(index: Int, updatedRow: Map<String, String>): SpreadsheetWorkbook {
        if (index !in rows.indices) return this
        val mutable = rows.toMutableList()
        mutable[index] = updatedRow
        return copy(rows = mutable)
    }
}

data class SpreadsheetTemplate(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val columns: List<String>,
    val initialRows: List<Map<String, String>> = emptyList()
)
