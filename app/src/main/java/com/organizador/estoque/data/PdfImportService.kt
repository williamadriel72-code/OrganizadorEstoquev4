package com.organizador.estoque.data

import android.content.Context
import android.net.Uri

class PdfImportService(context: Context, private val repository: InventoryRepository) {
    private val appContext = context.applicationContext
    private val reader = PdfTextReader(appContext)
    private val expiryStore = ExpiryImportStore(appContext)

    fun importStock(
        uri: Uri,
        onProgress: (PdfImportProgress) -> Unit = {}
    ): PdfImportResult {
        onProgress(PdfImportProgress(5, "Lendo PDF"))
        val text = reader.read(uri)

        onProgress(PdfImportProgress(35, "Processando produtos"))
        val parsed = StockPdfParser.parse(text)
        if (parsed.isEmpty()) throw IllegalArgumentException("PDF de estoque sem produtos reconhecidos")

        val total = parsed.size
        val step = (total / 20).coerceAtLeast(1)
        val products = parsed.mapIndexed { index, incoming ->
            if (index % step == 0 || index == total - 1) {
                val percent = 45 + ((index + 1) * 30 / total)
                onProgress(PdfImportProgress(percent.coerceAtMost(75), "Conferindo produtos"))
            }
            val existing = repository.findExact(incoming.code)
            if (existing == null) incoming else incoming.copy(
                groupCode = existing.groupCode,
                category = existing.category,
                controlsExpiry = existing.controlsExpiry
            )
        }

        onProgress(PdfImportProgress(85, "Atualizando estoque"))
        repository.replaceInventory(products)
        onProgress(PdfImportProgress(100, "Concluído"))
        return PdfImportResult(products.size, 0, "Importação de estoque concluída")
    }

    fun importExpiries(
        uri: Uri,
        onProgress: (PdfImportProgress) -> Unit = {}
    ): PdfImportResult {
        onProgress(PdfImportProgress(5, "Lendo PDF"))
        val text = reader.read(uri)

        onProgress(PdfImportProgress(45, "Processando validades"))
        val rows = ExpiryPdfParser.parse(text)
        if (rows.isEmpty()) throw IllegalArgumentException("PDF de validades sem linhas reconhecidas")

        onProgress(PdfImportProgress(80, "Atualizando validades"))
        val result = expiryStore.replace(rows)
        onProgress(PdfImportProgress(100, "Concluído"))
        return PdfImportResult(result.first, result.second, "Importação de validades concluída")
    }
}
