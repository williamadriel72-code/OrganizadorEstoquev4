package com.organizador.estoque.data

import android.content.Context
import android.net.Uri

class PdfImportService(context: Context, private val repository: InventoryRepository) {
    private val appContext = context.applicationContext
    private val reader = PdfTextReader(appContext)
    private val expiryStore = ExpiryImportStore(appContext)

    fun importStock(uri: Uri): PdfImportResult {
        val products = StockPdfParser.parse(reader.read(uri))
        if (products.isEmpty()) throw IllegalArgumentException("PDF de estoque sem produtos reconhecidos")
        repository.replaceInventory(products)
        return PdfImportResult(products.size, 0, "Importação de estoque concluída")
    }

    fun importExpiries(uri: Uri): PdfImportResult {
        val rows = ExpiryPdfParser.parse(reader.read(uri))
        if (rows.isEmpty()) throw IllegalArgumentException("PDF de validades sem linhas reconhecidas")
        val result = expiryStore.replace(rows)
        return PdfImportResult(result.first, result.second, "Importação de validades concluída")
    }
}
