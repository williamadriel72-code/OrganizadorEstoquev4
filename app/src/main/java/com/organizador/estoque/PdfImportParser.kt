package com.organizador.estoque

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

data class PdfImportItem(
    val codigoInterno: String,
    val ean: String?,
    val nome: String,
    val unCx: Double? = null,
    val quantidade: Double? = null,
    val validade: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("codigoInterno", codigoInterno)
        put("ean", ean ?: JSONObject.NULL)
        put("nome", nome)
        put("unCx", unCx ?: JSONObject.NULL)
        put("quantidade", quantidade ?: JSONObject.NULL)
        put("validade", validade ?: JSONObject.NULL)
    }
}

data class ParsedPdfImport(
    val type: String,
    val fileName: String,
    val companyCode: String?,
    val companyName: String?,
    val pages: Int,
    val items: List<PdfImportItem>
) {
    fun summaryJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("fileName", fileName)
        put("companyCode", companyCode ?: JSONObject.NULL)
        put("companyName", companyName ?: JSONObject.NULL)
        put("pages", pages)
        put("totalItems", items.size)
        put("preview", JSONArray().apply {
            items.take(12).forEach { put(it.toJson()) }
        })
    }

    fun batchJson(offset: Int, limit: Int): JSONObject {
        val safeOffset = offset.coerceAtLeast(0).coerceAtMost(items.size)
        val safeLimit = limit.coerceIn(1, 500)
        val end = (safeOffset + safeLimit).coerceAtMost(items.size)
        return JSONObject().apply {
            put("type", type)
            put("offset", safeOffset)
            put("done", end >= items.size)
            put("totalItems", items.size)
            put("items", JSONArray().apply {
                items.subList(safeOffset, end).forEach { put(it.toJson()) }
            })
        }
    }
}

object PdfImportParser {
    private val spaces = Regex("""\s+""")
    private val companyStock = Regex("""Empresa:\s*(\d+)\s+(.+)""", RegexOption.IGNORE_CASE)
    private val companyValidity = Regex("""Filial\s*:\s*(\d+)\s*-\s*(.+)""", RegexOption.IGNORE_CASE)
    private val stockProduct = Regex("""^Produto:\s*(\d+)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val trailingNumbers = Regex("""(-?\d+(?:[.,]\d+)?)\s+(-?\d+(?:[.,]\d+)?)\s*$""")
    private val dateAny = Regex("""(\d{2}/\d{2}/\d{4})""")
    private val dateGroup = Regex("""Data\s+Validade\s+(\d{2}/\d{2}/\d{4})""", RegexOption.IGNORE_CASE)
    private val productLine = Regex("""^(\d{1,7})\s+(\d{8,14})\s+(.+)$""")

    fun parse(
        context: Context,
        uri: Uri,
        fileName: String,
        progress: (page: Int, total: Int) -> Unit
    ): ParsedPdfImport {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Não foi possível abrir o PDF.")

        input.use { stream ->
            PDDocument.load(stream).use { document ->
                val totalPages = document.numberOfPages
                if (totalPages <= 0) throw IllegalArgumentException("O PDF não possui páginas.")

                val stripper = PDFTextStripper().apply { sortByPosition = true }

                var type: String? = null
                var companyCode: String? = null
                var companyName: String? = null

                val stockItems = ArrayList<PdfImportItem>()
                var currentStockCode: String? = null
                var currentStockName: String? = null

                val validityItems = ArrayList<PdfImportItem>()
                var currentDate: String? = null
                var pendingValidity: PendingValidity? = null

                for (page in 1..totalPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val text = stripper.getText(document)

                    if (type == null) {
                        type = when {
                            text.contains("Relatório de Conferência de Estoque", ignoreCase = true) -> "estoque"
                            text.contains("Relação Geral de Produtos", ignoreCase = true) &&
                                text.contains("Data Validade", ignoreCase = true) -> "validades"
                            text.contains("Data Validade", ignoreCase = true) &&
                                text.contains("Filial", ignoreCase = true) -> "validades"
                            else -> null
                        }
                    }

                    val rawLines = text.split('\n')
                    for (raw in rawLines) {
                        var line = normalize(raw)
                        if (line.isBlank()) continue

                        if (companyCode == null) {
                            companyStock.find(line)?.let {
                                companyCode = it.groupValues[1].trim()
                                companyName = cleanCompanyName(it.groupValues[2])
                            }
                            companyValidity.find(line)?.let {
                                companyCode = it.groupValues[1].trim()
                                companyName = cleanCompanyName(it.groupValues[2])
                            }
                        }

                        if (type == "estoque") {
                            val header = stockProduct.find(line)
                            if (header != null) {
                                currentStockCode = header.groupValues[1].trim()
                                currentStockName = header.groupValues[2].trim()
                                continue
                            }

                            val code = currentStockCode
                            val name = currentStockName
                            if (code != null && name != null && line.startsWith("$code ")) {
                                val afterCode = line.removePrefix(code).trimStart()
                                val eanMatch = Regex("""^(\d{8,14})\s+(.+)$""").find(afterCode)
                                if (eanMatch != null) {
                                    val ean = eanMatch.groupValues[1]
                                    val beforeForm = eanMatch.groupValues[2]
                                        .substringBefore("___/___/______")
                                        .trim()
                                    val nums = trailingNumbers.find(beforeForm)
                                    if (nums != null) {
                                        val unCx = parseNumber(nums.groupValues[1])
                                        val qty = parseNumber(nums.groupValues[2])
                                        stockItems.add(
                                            PdfImportItem(
                                                codigoInterno = code,
                                                ean = ean,
                                                nome = name,
                                                unCx = unCx,
                                                quantidade = qty
                                            )
                                        )
                                        currentStockCode = null
                                        currentStockName = null
                                    }
                                }
                            }
                        } else if (type == "validades") {
                            line = stripValidityFooter(line, totalPages)
                            if (line.isBlank()) continue

                            val group = dateGroup.find(line)
                            if (group != null) {
                                pendingValidity?.let { pending ->
                                    currentDate?.let { d -> validityItems.add(pending.toItem(d)) }
                                }
                                pendingValidity = null
                                currentDate = toIso(group.groupValues[1])
                                continue
                            }

                            val standaloneDate = dateAny.matchEntire(line)
                            if (standaloneDate != null) {
                                val iso = toIso(standaloneDate.groupValues[1])
                                pendingValidity?.let { pending -> validityItems.add(pending.toItem(iso)) }
                                pendingValidity = null
                                continue
                            }

                            val p = productLine.find(line)
                            if (p != null) {
                                pendingValidity?.let { pending ->
                                    currentDate?.let { d -> validityItems.add(pending.toItem(d)) }
                                }

                                val code = p.groupValues[1].trim()
                                val ean = p.groupValues[2].trim()
                                var desc = p.groupValues[3].trim()
                                val endDate = dateAny.find(desc)
                                if (endDate != null && endDate.range.last == desc.lastIndex) {
                                    desc = desc.substring(0, endDate.range.first).trim()
                                    validityItems.add(
                                        PdfImportItem(
                                            codigoInterno = code,
                                            ean = ean,
                                            nome = desc,
                                            validade = toIso(endDate.value)
                                        )
                                    )
                                    pendingValidity = null
                                } else {
                                    pendingValidity = PendingValidity(code, ean, desc)
                                }
                                continue
                            }

                            if (pendingValidity != null && !isValidityNoise(line)) {
                                pendingValidity = pendingValidity!!.append(line)
                            }
                        }
                    }

                    if (page == 1 || page == totalPages || page % 10 == 0) {
                        progress(page, totalPages)
                    }
                }

                if (type == "validades") {
                    pendingValidity?.let { pending ->
                        currentDate?.let { d -> validityItems.add(pending.toItem(d)) }
                    }
                }

                val resolvedType = type ?: throw IllegalArgumentException(
                    "Formato de PDF não reconhecido. Use o relatório de estoque ou o relatório de validades."
                )

                val finalItems = if (resolvedType == "estoque") {
                    val unique = LinkedHashMap<String, PdfImportItem>()
                    stockItems.forEach { unique[it.codigoInterno] = it }
                    unique.values.toList()
                } else {
                    val unique = LinkedHashMap<String, PdfImportItem>()
                    validityItems.forEach { item ->
                        val key = "${item.codigoInterno}|${item.validade}"
                        unique[key] = item
                    }
                    unique.values.toList()
                }

                if (finalItems.isEmpty()) {
                    throw IllegalArgumentException("Nenhum item foi reconhecido no PDF.")
                }

                return ParsedPdfImport(
                    type = resolvedType,
                    fileName = fileName,
                    companyCode = companyCode,
                    companyName = companyName,
                    pages = totalPages,
                    items = finalItems
                )
            }
        }
    }

    private data class PendingValidity(
        val code: String,
        val ean: String,
        val name: String
    ) {
        fun append(part: String): PendingValidity = copy(name = normalize("$name $part"))
        fun toItem(date: String): PdfImportItem = PdfImportItem(codigoInterno = code, ean = ean, nome = name.trim(), validade = date)
    }

    private fun normalize(value: String): String = value.replace('\u00A0', ' ').trim().replace(spaces, " ")
    private fun parseNumber(value: String): Double? = value.replace(".", "").replace(",", ".").toDoubleOrNull()
    private fun toIso(value: String): String { val parts = value.split("/"); return if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else value }
    private fun cleanCompanyName(raw: String): String = normalize(raw).substringBefore("Data Validade").substringBefore("Data:").trim()
    private fun stripValidityFooter(line: String, totalPages: Int): String {
        val marker = Regex("""D\.A\.M\s+Soluções.*?\sDe\s+$totalPages""", setOf(RegexOption.IGNORE_CASE))
        return normalize(line.replace(marker, ""))
    }
    private fun isValidityNoise(line: String): Boolean {
        val l = line.lowercase()
        return l.startsWith("quantidade de itens agrupados") || l.startsWith("relação geral de produtos") || l.startsWith("situação") || l.startsWith("tipo relatorio") || l.startsWith("produto validade") || l.startsWith("empresa") || l.startsWith("filtro") || l.startsWith("t. produto") || l.startsWith("tipo custo") || l.startsWith("custo compra") || l.startsWith("filial") || l.startsWith("d.a.m soluções") || l.contains("usuário:")
    }
}
