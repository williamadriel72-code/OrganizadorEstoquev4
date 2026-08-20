package com.organizador.estoque.data

import android.content.Context
import android.net.Uri

class PdfImportService(context: Context, private val repository: InventoryRepository) {
    private val appContext = context.applicationContext
    private val reader = PdfTextReader(appContext)
    private val expiryStore = ExpiryImportStore(appContext)
    private val priceStore = CatalogPriceStore(appContext)
    private val snapshotInstaller = InventorySnapshotInstaller(appContext)

    fun importStock(
        uri: Uri,
        onProgress: (PdfImportProgress) -> Unit = {}
    ): PdfImportResult {
        onProgress(PdfImportProgress(5, "Lendo PDF"))
        val text = reader.read(uri)

        onProgress(PdfImportProgress(35, "Processando produtos, valores e validades"))
        val snapshot = StockPdfParser.parseSnapshot(text)
        val products = snapshot.products
        if (products.isEmpty()) throw IllegalArgumentException("PDF de estoque sem produtos reconhecidos")

        onProgress(PdfImportProgress(70, "Substituindo base de produtos"))
        snapshotInstaller.replace(products)

        onProgress(PdfImportProgress(85, "Atualizando preços"))
        priceStore.replace(snapshot.prices)

        onProgress(PdfImportProgress(92, "Atualizando validades"))
        val expiryResult = expiryStore.replace(snapshot.expiries)

        onProgress(PdfImportProgress(100, "Concluído"))
        return PdfImportResult(
            imported = products.size,
            skipped = expiryResult.second,
            message = "Estoque, preços e validades atualizados • ${snapshot.invalidExpiryCount} sem validade informada"
        )
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

        onProgress(PdfImportProgress(80, "Vinculando validades aos produtos"))
        val result = expiryStore.replace(rows)
        if (result.first == 0) {
            throw IllegalArgumentException(
                "Nenhuma validade foi vinculada aos produtos. Confira se o PDF contém o código ou EAN dos produtos cadastrados."
            )
        }

        onProgress(PdfImportProgress(100, "Concluído"))
        return PdfImportResult(result.first, result.second, "Importação de validades concluída")
    }
}
