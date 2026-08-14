package com.organizador.estoque.data

data class PdfImportResult(
    val imported: Int,
    val skipped: Int,
    val message: String
)

data class PdfImportProgress(
    val percent: Int,
    val stage: String
)

data class ExpiryImportRow(
    val productRef: String,
    val expiryDate: String,
    val quantity: Double
)
