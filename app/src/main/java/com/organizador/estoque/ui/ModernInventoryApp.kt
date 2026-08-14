package com.organizador.estoque.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    var productFilter by rememberSaveable { mutableStateOf("all") }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = screen != "home") { screen = "home" }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF1677FF), background = Color(0xFF061321), surface = Color(0xFF10243A))) {
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = screen == "home", onClick = { screen = "home" }, icon = {}, label = { Text("Início") })
                NavigationBarItem(selected = screen == "products", onClick = { productFilter = "all"; screen = "products" }, icon = {}, label = { Text("Produtos") })
                NavigationBarItem(selected = screen == "entry", onClick = { screen = "entry" }, icon = {}, label = { Text("Entrada") })
                NavigationBarItem(selected = screen == "exit", onClick = { screen = "exit" }, icon = {}, label = { Text("Saída") })
            }
        }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                holder.SaveableStateProvider(screen) {
                    when (screen) {
                        "products" -> ProductsV2(repository, refresh, productFilter)
                        "expiries" -> ExpiryV2()
                        "entry" -> MovementV2(repository, true) { refresh++ }
                        "exit" -> MovementV2(repository, false) { refresh++ }
                        else -> DashboardV2(
                            repository,
                            refresh,
                            openProducts = { filter -> productFilter = filter; screen = "products" },
                            openExpiries = { screen = "expiries" }
                        )
                    }
                }
            }
        }
    }
}
