package com.organizador.estoque

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.organizador.estoque.data.InventoryDb
import com.organizador.estoque.data.InventoryRepository
import com.organizador.estoque.ui.ModernInventoryApp

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = InventoryRepository(InventoryDb(applicationContext))
        setContent { ModernInventoryApp(repository) }
    }
}
