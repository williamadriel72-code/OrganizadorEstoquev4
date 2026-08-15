package com.stockmaster.clone.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

data class ProductPdfImportReport(
    val found: Int,
    val inserted: Int,
    val updated: Int,
    val ignored: Int,
    val errors: Int
)

private data class ImportedProduct(
    val id: Int,
    val ean: String,
    val description: String,
    val stock: Double,
    val groupId: Int?
)

class ProductPdfImporter(
    private val context: Context,
    private val db: StockMasterDb
) {
    fun import(uri: Uri): ProductPdfImportReport {
        PDFBoxResourceLoader.init(context.applicationContext)

        val temp = File.createTempFile("aws_produtos_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Não foi possível abrir o PDF selecionado." }
                temp.outputStream().use { output -> input.copyTo(output) }
            }

            val parsed = PDDocument.load(temp).use { document ->
                require(document.numberOfPages > 0) { "O PDF está vazio." }

                // Os relatórios DAM/AWS podem ser extraídos em ordens diferentes conforme
                // o renderizador PDF. A ordem natural preserva melhor Código + EAN +
                // Descrição + Estoque. Se ela não reconhecer nada, tentamos a ordenação
                // visual por posição como fallback.
                val naturalText = PDFTextStripper().apply {
                    sortByPosition = false
                }.getText(document)

                require(naturalText.isNotBlank()) {
                    "O PDF não possui texto pesquisável. Use um relatório PDF gerado pelo sistema, não uma imagem digitalizada."
                }

                val naturalResult = parseReport(naturalText)
                if (naturalResult.products.isNotEmpty()) {
                    naturalResult
                } else {
                    val positionedText = PDFTextStripper().apply {
                        sortByPosition = true
                    }.getText(document)
                    parseReport(positionedText)
                }
            }

            require(parsed.products.isNotEmpty()) {
                "Nenhuma mercadoria foi reconhecida. O PDF foi lido, mas o layout das colunas não pôde ser identificado."
            }

            return applyToDatabase(parsed.products, parsed.parseErrors)
        } finally {
            temp.delete()
        }
    }

    private data class ParseResult(
        val products: List<ImportedProduct>,
        val parseErrors: Int
    )

    private data class PendingProduct(
        val id: Int,
        val ean: String,
        val groupId: Int?,
        val descriptionParts: MutableList<String>
    )

    private fun parseReport(rawText: String): ParseResult {
        val products = ArrayList<ImportedProduct>()
        var currentGroup: Int? = null
        var pending: PendingProduct? = null
        var waitingGroupValue = false
        var pendingCode: Int? = null
        var errors = 0

        fun finishPending(stock: Double) {
            val item = pending ?: return
            val description = item.descriptionParts
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (description.isBlank()) {
                errors++
            } else {
                products += ImportedProduct(
                    id = item.id,
                    ean = item.ean,
                    description = description,
                    stock = stock,
                    groupId = item.groupId
                )
            }
            pending = null
            pendingCode = null
        }

        rawText.lineSequence().forEach { originalLine ->
            var line = cleanLine(originalLine)
            if (line.isBlank()) return@forEach

            val groupMatch = groupRegex.find(line)
            if (groupMatch != null) {
                if (pending != null) {
                    errors++
                    pending = null
                }
                pendingCode = null
                currentGroup = groupMatch.groupValues[1].toIntOrNull()
                waitingGroupValue = false
                val trailing = line.substring(groupMatch.range.last + 1).trim()
                if (trailing.isBlank()) return@forEach
                line = trailing
            } else if (groupLabelOnlyRegex.matches(line)) {
                if (pending != null) {
                    errors++
                    pending = null
                }
                pendingCode = null
                waitingGroupValue = true
                return@forEach
            } else if (waitingGroupValue) {
                val splitGroup = groupValueRegex.matchEntire(line)
                if (splitGroup != null) {
                    currentGroup = splitGroup.groupValues[1].toIntOrNull()
                    waitingGroupValue = false
                    return@forEach
                }
                if (isReportNoise(line)) return@forEach
                waitingGroupValue = false
            }

            if (isReportNoise(line)) return@forEach

            val productMatch = productStartRegex.matchEntire(line)
            if (productMatch != null) {
                if (pending != null) {
                    errors++
                    pending = null
                }
                pendingCode = null

                val id = productMatch.groupValues[1].toIntOrNull()
                val ean = productMatch.groupValues[2].trim()
                val remainder = productMatch.groupValues[3].trim()
                if (id == null || ean.isBlank() || remainder.isBlank()) {
                    errors++
                    return@forEach
                }

                val stockAtEnd = stockAtEndRegex.matchEntire(remainder)
                if (stockAtEnd != null) {
                    val description = stockAtEnd.groupValues[1]
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    val stock = parsePtBrNumber(stockAtEnd.groupValues[2])
                    if (description.isBlank() || stock == null) {
                        errors++
                    } else {
                        products += ImportedProduct(id, ean, description, stock, currentGroup)
                    }
                } else {
                    pending = PendingProduct(
                        id = id,
                        ean = ean,
                        groupId = currentGroup,
                        descriptionParts = mutableListOf(remainder)
                    )
                }
                return@forEach
            }

            // Alguns extratores Android quebram o código em uma linha e deixam o EAN
            // com a descrição na linha seguinte. Aceitamos esse formato como fallback.
            val codeOnly = codeOnlyRegex.matchEntire(line)
            if (codeOnly != null && pending == null) {
                pendingCode = codeOnly.groupValues[1].toIntOrNull()
                return@forEach
            }

            val code = pendingCode
            if (code != null && pending == null) {
                val eanDescription = eanDescriptionRegex.matchEntire(line)
                if (eanDescription != null) {
                    pending = PendingProduct(
                        id = code,
                        ean = eanDescription.groupValues[1],
                        groupId = currentGroup,
                        descriptionParts = mutableListOf(eanDescription.groupValues[2].trim())
                    )
                    pendingCode = null
                    return@forEach
                }
            }

            val pendingItem = pending
            if (pendingItem != null) {
                if (standaloneStockRegex.matches(line)) {
                    val stock = parsePtBrNumber(line)
                    if (stock == null) {
                        errors++
                        pending = null
                        pendingCode = null
                    } else {
                        finishPending(stock)
                    }
                } else if (!isReportNoise(line) && !looksLikeFooter(line)) {
                    pendingItem.descriptionParts += line
                }
            }
        }

        if (pending != null) errors++
        return ParseResult(products, errors)
    }

    private fun cleanLine(input: String): String {
        var line = input
            .replace('\u00A0', ' ')
            .replace("\u000C", "")
            .replace("\u200B", "")
            .replace(Regex("[\\t ]+"), " ")
            .trim()

        line = damFooterRegex.replace(line, "").trim()
        line = pageCounterRegex.replace(line, "").trim()
        return line
    }

    private fun isReportNoise(line: String): Boolean {
        val normalized = line.uppercase()
        return normalized.startsWith("CÓDIGO EAN DESCRIÇÃO ESTOQUE ATUAL") ||
            normalized.startsWith("CODIGO EAN DESCRICAO ESTOQUE ATUAL") ||
            normalized.startsWith("VALOR ESTOQUE") ||
            normalized.startsWith("CUSTO COMPRA") ||
            normalized.startsWith("MARGEM CONTRIBUI") ||
            normalized.startsWith("RELAÇÃO GERAL DE PRODUTOS") ||
            normalized.startsWith("RELACAO GERAL DE PRODUTOS") ||
            normalized.startsWith("EMPRESA :") ||
            normalized.startsWith("TIPO RELATORIO") ||
            normalized.startsWith("SITUAÇÃO") ||
            normalized.startsWith("SITUACAO") ||
            normalized.startsWith("AGRUPADO POR") ||
            normalized.startsWith("ORDENADO POR") ||
            normalized.startsWith("T. PRODUTO") ||
            normalized.startsWith("T. ITEM") ||
            normalized.startsWith("T. PROMOÇÃO") ||
            normalized.startsWith("T. PROMOCAO") ||
            normalized.startsWith("FILTRO :") ||
            normalized.startsWith("FILIAL :") ||
            normalized.startsWith("D.A.M SOLUÇÕES") ||
            normalized.startsWith("D.A.M SOLUCOES") ||
            normalized.startsWith("VERSÃO:") ||
            normalized.startsWith("VERSAO:") ||
            normalized.startsWith("USUÁRIO:") ||
            normalized.startsWith("USUARIO:") ||
            normalized.matches(Regex("^\\d+\\s+DE\\s+\\d+$")) ||
            normalized == "CÓDIGO" ||
            normalized == "CODIGO" ||
            normalized == "EAN" ||
            normalized == "DESCRIÇÃO" ||
            normalized == "DESCRICAO" ||
            normalized == "ESTOQUE ATUAL"
    }

    private fun looksLikeFooter(line: String): Boolean {
        val normalized = line.uppercase()
        return normalized.contains("D.A.M SOLUÇÕES") ||
            normalized.contains("D.A.M SOLUCOES") ||
            normalized.contains("ADriel WILLIAM".uppercase()) ||
            normalized.matches(Regex(".*\\d+\\s+DE\\s+\\d+.*"))
    }

    private fun applyToDatabase(
        products: List<ImportedProduct>,
        parseErrors: Int
    ): ProductPdfImportReport {
        val database = db.open()
        runCatching {
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_aws_produto_ean ON produto(ean)")
        }

        var inserted = 0
        var updated = 0
        var ignored = 0
        var errors = parseErrors
        val seenIds = HashSet<Int>()
        val seenEans = HashSet<String>()

        database.beginTransaction()
        try {
            products.forEach { product ->
                if (!seenIds.add(product.id) || !seenEans.add(product.ean)) {
                    ignored++
                    return@forEach
                }

                try {
                    val existingId = findExistingProductId(database, product)
                    if (existingId != null) {
                        val values = ContentValues().apply {
                            if (product.ean.isNotBlank()) put("ean", product.ean)
                            if (product.description.isNotBlank()) put("descricaoproduto", product.description)
                            put("qtestoque", product.stock)
                            product.groupId?.let { put("idgrupo", it) }
                        }
                        val changed = database.update(
                            "produto",
                            values,
                            "id=?",
                            arrayOf(existingId.toString())
                        )
                        if (changed > 0) updated++ else errors++
                    } else {
                        val values = ContentValues().apply {
                            put("id", product.id)
                            put("idempresa", 1)
                            put("idun", "UN")
                            put("ean", product.ean)
                            put("balanca", "N")
                            put("promocao", "N")
                            product.groupId?.let { put("idgrupo", it) }
                            put("descricaoproduto", product.description)
                            put("qtestoque", product.stock)
                            put("ativo", "S")
                        }
                        val rowId = database.insert("produto", null, values)
                        if (rowId == -1L) errors++ else inserted++
                    }
                } catch (_: Exception) {
                    errors++
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return ProductPdfImportReport(
            found = products.size,
            inserted = inserted,
            updated = updated,
            ignored = ignored,
            errors = errors
        )
    }

    private fun findExistingProductId(
        database: android.database.sqlite.SQLiteDatabase,
        product: ImportedProduct
    ): Int? {
        database.rawQuery(
            "SELECT id FROM produto WHERE id=? LIMIT 1",
            arrayOf(product.id.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }

        if (product.ean.isNotBlank()) {
            database.rawQuery(
                "SELECT id FROM produto WHERE TRIM(COALESCE(ean,''))=? LIMIT 1",
                arrayOf(product.ean.trim())
            ).use { c ->
                if (c.moveToFirst()) return c.getInt(0)
            }
        }
        return null
    }

    private fun parsePtBrNumber(value: String): Double? {
        return value
            .replace(".", "")
            .replace(",", ".")
            .trim()
            .toDoubleOrNull()
    }

    companion object {
        private val groupRegex = Regex(
            """Grupo\s*:\s*(\d+)\s*-\s*.+""",
            RegexOption.IGNORE_CASE
        )

        private val groupLabelOnlyRegex = Regex(
            """^Grupo\s*:\s*$""",
            RegexOption.IGNORE_CASE
        )

        private val groupValueRegex = Regex(
            """^(\d+)\s*-\s*.+$"""
        )

        private val productStartRegex = Regex(
            """^(\d{1,9})\s+([0-9]{8,20})\s+(.+)$"""
        )

        private val codeOnlyRegex = Regex(
            """^(\d{1,9})$"""
        )

        private val eanDescriptionRegex = Regex(
            """^([0-9]{8,20})\s+(.+)$"""
        )

        private val stockAtEndRegex = Regex(
            """^(.*?)\s+(-?\d[\d.]*,\d{2,4})$"""
        )

        private val standaloneStockRegex = Regex(
            """^-?\d[\d.]*,\d{2,4}$"""
        )

        private val damFooterRegex = Regex(
            """D\.A\.M\s+Soluções.*?Data\s+\d{2}\.\d{2}\.\d{4}\s+\d{2}:\d{2}:\d{2}""",
            setOf(RegexOption.IGNORE_CASE)
        )

        private val pageCounterRegex = Regex(
            """^\d+\s+De\s+\d+\s*$""",
            RegexOption.IGNORE_CASE
        )
    }
}
