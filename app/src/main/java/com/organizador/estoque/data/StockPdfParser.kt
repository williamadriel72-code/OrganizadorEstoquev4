package com.organizador.estoque.data

object StockPdfParser {
    private val startWithEan = Regex("(\\d{1,6})\\s+(\\d{8,14})\\s+(.+)")
    private val qtyAtEnd = Regex("(-?\\d+[.,]\\d{2,3})$")
    private val qtyOnly = Regex("^-?\\d+[.,]\\d{2,3}$")
    private val eanAtEnd = Regex("(\\d{8,14})$")

    fun parse(text: String): List<Product> {
        val products = linkedMapOf<String, Product>()
        var code: String? = null
        var ean: String? = null
        val description = mutableListOf<String>()

        fun save(quantity: Double) {
            val c = code ?: return
            val d = description.joinToString(" ").trim()
            if (d.isNotBlank()) {
                products[c] = Product(c, ean, d, null, null, quantity, false, true)
            }
            code = null
            ean = null
            description.clear()
        }

        for (raw in text.lines()) {
            var line = raw.trim()
            if (line.isBlank()) continue
            val lower = line.lowercase()
            if (lower.startsWith("grupo :") || lower.startsWith("quantidade de itens") || lower.startsWith("total do produto")) continue
            if (("código" in lower || "codigo" in lower) && ("estoque" in lower || "descrição" in lower || "descricao" in lower)) continue
            if (lower.startsWith("d.a.m ") || lower.startsWith("produto:")) continue

            if (code != null && qtyOnly.matches(line)) {
                save(number(line) ?: 0.0)
                continue
            }

            val found = startWithEan.find(line)
            if (found != null) {
                code = found.groupValues[1]
                ean = found.groupValues[2]
                description.clear()
                var rest = found.groupValues[3].trim()
                val qty = qtyAtEnd.find(rest)
                if (qty != null) {
                    rest = rest.substring(0, qty.range.first).trim()
                    description += rest
                    save(number(qty.value) ?: 0.0)
                } else {
                    description += rest
                }
                continue
            }

            if (code != null) {
                val qty = qtyAtEnd.find(line)
                if (qty != null) {
                    val before = line.substring(0, qty.range.first).trim()
                    if (before.isNotBlank()) description += before
                    save(number(qty.value) ?: 0.0)
                } else {
                    description += line
                }
                continue
            }

            val firstToken = line.substringBefore(' ')
            if (firstToken.all { it.isDigit() } && firstToken.length in 1..6) {
                val possibleEan = eanAtEnd.find(line)?.value
                val possibleQty = line.split(' ').firstOrNull { it.contains(',') && number(it) != null }
                if (possibleQty != null) {
                    val afterCode = line.removePrefix(firstToken).trim()
                    var desc = afterCode.replace(possibleQty, "").trim()
                    if (possibleEan != null) desc = desc.removeSuffix(possibleEan).trim()
                    products[firstToken] = Product(firstToken, possibleEan, desc, null, null, number(possibleQty) ?: 0.0, false, true)
                }
            }
        }
        return products.values.toList()
    }

    private fun number(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()
}
