package com.organizador.estoque.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object StockPdfParser {
    private const val NUMBER = "[\\-‐‑‒–—−]?\\d[\\d.]*,\\d{2,3}"
    private const val PRICE = "[\\d.]+,\\d{2}"
    private const val EXPIRY = "(?:\\d{2}/\\d{2}/\\d{4}|\\d{2}:\\d{2}:\\d{2}|0)"

    private val normalRow = Regex(
        """^\s*(\d{1,10})\s+(\d{8,14})\s+(.+?)\s+($NUMBER)\s+\$\s*($PRICE)\s+($EXPIRY)\s*$"""
    )
    private val partialStart = Regex("""^\s*(\d{1,10})\s+(\d{8,14})\s+(.+?)\s*$""")
    private val partialTail = Regex("""^\s*(.+?)\s+($NUMBER)\s+\$\s*($PRICE)\s+($EXPIRY)\s*$""")
    private val codeOnly = Regex("""^\s*(\d{1,10})(?:\s+(.+?))?\s*$""")
    private val eanTail = Regex("""^\s*(\d{8,14})\s+(.+?)\s+($NUMBER)\s+\$\s*($PRICE)\s+($EXPIRY)\s*$""")
    private val invalidExpiryValues = setOf("0", "00:00:00", "01/01/1899")

    private data class Partial(val code: String, val ean: String, val description: String)

    fun parse(text: String): List<Product> = parseSnapshot(text).products

    fun parseSnapshot(text: String): StockPdfSnapshot {
        val products = linkedMapOf<String, Product>()
        val expiries = mutableListOf<ExpiryImportRow>()
        val prices = linkedMapOf<String, Double>()
        var invalidExpiryCount = 0
        var lastCode: String? = null
        var pending: Partial? = null
        var pendingCode: String? = null

        fun save(code: String, ean: String, description: String, stockRaw: String, priceRaw: String, expiryRaw: String) {
            val stock = number(stockRaw) ?: 0.0
            val price = number(priceRaw) ?: 0.0
            val expiry = normalizeExpiryValue(expiryRaw)
            if (expiry == null) invalidExpiryCount++
            val desc = description.replace(Regex("""\s+"""), " ").trim()

            products[code] = Product(
                code = code,
                ean = ean,
                description = desc,
                groupCode = null,
                category = null,
                stock = stock,
                controlsExpiry = expiry != null,
                active = true
            )
            prices[code] = price
            if (expiry != null) {
                expiries += ExpiryImportRow(ean, expiry, stock.coerceAtLeast(0.0))
            }
            lastCode = code
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val stripped = line.trim()
            if (stripped.isBlank() || isHeaderOrFooter(stripped)) continue

            val normal = normalRow.matchEntire(line)
            if (normal != null) {
                val (code, ean, desc, stock, price, expiry) = normal.destructured
                save(code, ean, desc, stock, price, expiry)
                pending = null
                pendingCode = null
                continue
            }

            val waitingCode = pendingCode
            if (waitingCode != null) {
                val tail = eanTail.matchEntire(line)
                if (tail != null) {
                    val (ean, desc, stock, price, expiry) = tail.destructured
                    save(waitingCode, ean, desc, stock, price, expiry)
                    pendingCode = null
                    continue
                }
            }

            if ('$' !in line) {
                val partialMatch = partialStart.matchEntire(line)
                if (partialMatch != null) {
                    val (code, ean, desc) = partialMatch.destructured
                    pending = Partial(code, ean, cleanPartialDescription(desc))
                    pendingCode = null
                    lastCode = null
                    continue
                }
            }

            val waiting = pending
            if (waiting != null) {
                val tail = partialTail.matchEntire(line)
                if (tail != null) {
                    val (tailDesc, stock, price, expiry) = tail.destructured
                    val desc = (tailDesc + " " + waiting.description).trim()
                    save(waiting.code, waiting.ean, desc, stock, price, expiry)
                    pending = null
                    continue
                }
            }

            val indent = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
            if (indent < 8) {
                val codeMatch = codeOnly.matchEntire(line)
                if (codeMatch != null) {
                    val code = codeMatch.groupValues[1]
                    val secondToken = stripped.split(Regex("""\s+"""), limit = 3).getOrNull(1).orEmpty()
                    if (!secondToken.matches(Regex("""\d{8,14}"""))) {
                        // Alguns relatórios sobrepõem um fragmento invisível da descrição
                        // anterior na mesma linha do próximo código. Ignoramos esse fragmento.
                        pendingCode = code
                        pending = null
                        continue
                    }
                }
            }

            val code = lastCode ?: continue
            if (indent >= 8 && '$' !in stripped && looksLikeDescriptionContinuation(stripped)) {
                products[code]?.let { current ->
                    products[code] = current.copy(
                        description = (current.description + " " + stripped)
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                    )
                }
            }
        }

        return StockPdfSnapshot(products.values.toList(), expiries, prices, invalidExpiryCount)
    }

    private fun cleanPartialDescription(value: String): String = value
        .replace(Regex("""^\d+\s*UN\s+\d+\s*G\s+""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun isHeaderOrFooter(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("d.a.m") || lower.startsWith("relação geral") || lower.startsWith("relacao geral") ||
            lower.startsWith("empresa :") || lower.startsWith("tipo relatorio") || lower.startsWith("tipo relatório") ||
            lower.startsWith("situação") || lower.startsWith("situacao") || lower.startsWith("filtro") ||
            lower.startsWith("filial :") || lower.startsWith("usuário") || lower.startsWith("usuario")
    }

    private fun looksLikeDescriptionContinuation(value: String): Boolean {
        if (isHeaderOrFooter(value)) return false
        if (Regex("""^\d{2}/\d{2}/\d{4}$""").matches(value)) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun number(value: String): Double? = value.trim()
        .replace('‐', '-').replace('‑', '-').replace('‒', '-')
        .replace('–', '-').replace('—', '-').replace('−', '-')
        .replace(".", "")
        .replace(",", ".")
        .toDoubleOrNull()

    fun normalizeExpiryValue(value: String): String? {
        if (value in invalidExpiryValues || Regex("""\d{2}:\d{2}:\d{2}""").matches(value)) return null
        return runCatching {
            LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }.getOrNull()?.takeIf { it.year > 1900 }?.toString()
    }
}
