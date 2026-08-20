package com.organizador.estoque.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

class PdfTextReader(context: Context) {
    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    fun read(uri: Uri): String {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir o PDF selecionado.")
        return input.use { stream ->
            PDDocument.load(stream).use { document ->
                PDFTextStripper().apply { sortByPosition = true }.getText(document)
            }
        }
    }
}
