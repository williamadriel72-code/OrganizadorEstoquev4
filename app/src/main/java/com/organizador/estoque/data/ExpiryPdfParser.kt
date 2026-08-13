package com.organizador.estoque.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExpiryPdfParser {
    private val productStart = Regex("(\\d{1,6})\\s+(\\d{8,14})\\s*")
    private val datePattern = Regex("\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}")
    private val quantityPattern = Regex("-?\\d+[.,]\\d{2,3}")

    fun parse(text: String): List<ExpiryImportRow> {
        val rows = mutableListOf<ExpiryImportRow>()
        var currentProduct: String? = null

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isBlank()) continue
            val lower = line.lowercase()
            if (("código" in lower || "codigo" in lower) && "validade" in lower) continue
            if (lower.startsWith("grupo :") || lower.startsWith("quantidade de itens agrupados")) continue
            if (lower.startsWith("d.a.m ")) continue

            val product = productStart.find(line)
            if (product != null) currentProduct = product.groupValues[1]

            val date = datePattern.find(line) ?: continue
            if (lower.startsWith("data validade") && currentProduct == null) continue

            val beforeDate = line.substring(0, date.range.first)
            val quantity = quantityPattern.findAll(beforeDate).lastOrNull()?.value?.let(::number) ?: continue
            val ref = product?.groupValues?.get(1) ?: currentProduct ?: continue
            val normalized = normalizeDate(date.value) ?: continue
            rows += ExpiryImportRow(ref, normalized, quantity.coerceAtLeast(0.0))
            currentProduct = null
        }
        return rows
    }

    private fun number(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()

    private fun normalizeDate(value: String): String? = runCatching {
        if ('/' in value) LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy")).toString()
        else LocalDate.parse(value).toString()
    }.getOrNull()
}
