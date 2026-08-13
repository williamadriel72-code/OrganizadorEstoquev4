package com.organizador.estoque.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExpiryPdfParser {
    fun parse(text: String): List<ExpiryImportRow> {
        val rows = mutableListOf<ExpiryImportRow>()
        for (raw in text.lines()) {
            val tokens = raw.trim().split(' ', ';', '|', '\t').filter { it.isNotBlank() }
            if (tokens.size < 3) continue
            val dateIndex = tokens.indexOfFirst { it.count { c -> c == '/' } == 2 || it.count { c -> c == '-' } == 2 }
            if (dateIndex <= 0 || dateIndex >= tokens.lastIndex) continue
            val productRef = tokens.take(dateIndex).firstOrNull { token -> token.all { it.isDigit() } } ?: continue
            val quantity = tokens.drop(dateIndex + 1).firstNotNullOfOrNull { it.replace(',', '.').toDoubleOrNull() } ?: continue
            val date = normalizeDate(tokens[dateIndex]) ?: continue
            if (quantity >= 0) rows += ExpiryImportRow(productRef, date, quantity)
        }
        return rows
    }

    private fun normalizeDate(value: String): String? = runCatching {
        if ('/' in value) LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy")).toString()
        else LocalDate.parse(value).toString()
    }.getOrNull()
}
