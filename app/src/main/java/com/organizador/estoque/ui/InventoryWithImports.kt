package com.organizador.estoque.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.organizador.estoque.data.InventoryRepository

@Composable
fun InventoryWithImports(repository: InventoryRepository) {
    Column(Modifier.fillMaxSize()) {
        PdfImportBar(repository)
        Box(Modifier.weight(1f)) {
            InventoryApp(repository)
        }
    }
}
