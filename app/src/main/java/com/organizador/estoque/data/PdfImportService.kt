package com.organizador.estoque.data

import android.content.Context
import android.net.Uri

class PdfImportService(context: Context, private val repository: InventoryRepository) {
    private val appContext = context.applicationContext
    private val reader = PdfTextReader(appContext)

    fun importStock(uri: Uri): PdfImportResult = PdfImportResult(0, 0, "")
    fun importExpiries(uri: Uri): PdfImportResult = PdfImportResult(0, 0, "")
}
