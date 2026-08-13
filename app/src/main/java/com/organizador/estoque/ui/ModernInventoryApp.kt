package com.organizador.estoque.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.organizador.estoque.data.InventoryRepository

@Composable
fun ModernInventoryApp(repository: InventoryRepository) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    BackHandler(screen != "home") { screen = "home" }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF1677FF), background = Color(0xFF061321), surface = Color(0xFF10243A))) {
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(screen == "home", { screen = "home" }, {}, { Text("Início") })
                NavigationBarItem(screen == "products", { screen = "products" }, {}, { Text("Produtos") })
                NavigationBarItem(screen == "entry", { screen = "entry" }, {}, { Text("Entrada") })
                NavigationBarItem(screen == "exit", { screen = "exit" }, {}, { Text("Saída") })
            }
        }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                holder.SaveableStateProvider(screen) {
                    when (screen) {
                        "products" -> ProductsV2(repository, refresh)
                        "entry" -> MovementV2(repository, true) { refresh++ }
                        "exit" -> MovementV2(repository, false) { refresh++ }
                        else -> DashboardV2(repository, refresh) { screen = "products" }
                    }
                }
            }
        }
    }
}
