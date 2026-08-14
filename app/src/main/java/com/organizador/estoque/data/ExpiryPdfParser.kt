package com.organizador.estoque.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExpiryPdfParser {
    // Relatórios de validade normalmente trazem: código interno + EAN.
    // O EAN é preferido como referência porque é o mesmo valor lido pelo bip.
    private val productStart = Regex("(?<!\\d)(\\d{1,10})\\s+(\\d{8,14})(?!\\d)")
    private val datePattern = Regex("\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}")
    private val quantityPattern = Regex("-?\\d+[.,]\\d{2,3}")

    fun parse(text: String): List<ExpiryImportRow> {
        val rows = mutableListOf<ExpiryImportRow>()
        var currentProductRef: String? = null

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isBlank()) continue
            val lower = line.lowercase()
            if (("código" in lower || "codigo" in lower) && "validade" in lower) continue
            if (lower.startsWith("grupo :") || lower.startsWith("quantidade de itens agrupados")) continue
            if (lower.startsWith("d.a.m ")) continue

            val product = productStart.find(line)
            if (product != null) {
                val internalCode = product.groupValues[1]
                val ean = product.groupValues[2]
                currentProductRef = ean.ifBlank { internalCode }
            }

            val date = datePattern.find(line) ?: continue
            if (lower.startsWith("data validade") && currentProductRef == null) continue

            val beforeDate = line.substring(0, date.range.first)
            // Preserva a validade mesmo se a quantidade não estiver legível no PDF.
            val quantity = quantityPattern.findAll(beforeDate).lastOrNull()?.value?.let(::number) ?: 0.0
            val ref = currentProductRef ?: continue
            val normalized = normalizeDate(date.value) ?: continue

            rows += ExpiryImportRow(
                productRef = ref,
                expiryDate = normalized,
                quantity = quantity.coerceAtLeast(0.0)
            )
            // Não limpamos currentProductRef aqui: um mesmo produto pode ter várias
            // linhas de validade antes do próximo produto aparecer no relatório.
        }
        return rows
    }

    private fun number(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()

    private fun normalizeDate(value: String): String? = runCatching {
        if ('/' in value) LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy")).toString()
        else LocalDate.parse(value).toString()
    }.getOrNull()
}
