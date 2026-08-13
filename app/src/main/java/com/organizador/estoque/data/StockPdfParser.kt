package com.organizador.estoque.data

object StockPdfParser {
    fun parse(text: String): List<Product> {
        val products = linkedMapOf<String, Product>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isBlank()) continue
            val lower = line.lowercase()
            if (("codigo" in lower || "código" in lower) && "estoque" in lower) continue

            val parts = line.split(';').map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size < 3) continue
            val code = parts.first()
            val quantity = parts.last().replace(',', '.').toDoubleOrNull() ?: continue
            if (quantity < 0) continue

            val hasEan = parts.size > 3 && parts[1].length in 8..14 && parts[1].all { it.isDigit() }
            val start = if (hasEan) 2 else 1
            val description = parts.subList(start, parts.lastIndex).joinToString(" ").trim()
            if (description.isBlank()) continue

            products[code] = Product(
                code = code,
                ean = if (hasEan) parts[1] else null,
                description = description,
                groupCode = null,
                category = null,
                stock = quantity,
                controlsExpiry = false,
                active = true
            )
        }
        return products.values.toList()
    }
}
