package com.organizador.estoque

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.organizador.estoque.data.InventoryDb
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.ui.ModernInventoryApp
import com.organizador.estoque.ui.PdfImportBar

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = InventoryRepository(InventoryDb(applicationContext))
        setContent {
            Column(Modifier.fillMaxSize()) {
                PdfImportBar(repository)
                ModernInventoryApp(repository)
            }
        }
    }
}
