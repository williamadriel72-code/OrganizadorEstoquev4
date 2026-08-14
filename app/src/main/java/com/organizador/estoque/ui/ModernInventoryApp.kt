package com.organizador.estoque.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizador.estoque.data.InventoryRepository

@Composable
fun ModernInventoryApp(repository: InventoryRepository) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var productFilter by rememberSaveable { mutableStateOf("all") }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = screen != "home") { screen = "home" }

    fun navigate(target: String) {
        if (target == "products") productFilter = "all"
        screen = target
    }

    val screenContent: @Composable () -> Unit = {
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

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1677FF),
            background = Color(0xFF061321),
            surface = Color(0xFF10243A)
        )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val desktop = maxWidth >= 900.dp

            if (desktop) {
                Row(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.width(230.dp).fillMaxHeight(),
                        color = Color(0xFF0B1A2B),
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Organizador",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Text(
                                "Estoque",
                                color = Color(0xFF9FB0C4),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Spacer(Modifier.height(18.dp))

                            NavigationDrawerItem(
                                label = { Text("Início") },
                                selected = screen == "home",
                                onClick = { navigate("home") }
                            )
                            NavigationDrawerItem(
                                label = { Text("Produtos") },
                                selected = screen == "products",
                                onClick = { navigate("products") }
                            )
                            NavigationDrawerItem(
                                label = { Text("Entrada") },
                                selected = screen == "entry",
                                onClick = { navigate("entry") }
                            )
                            NavigationDrawerItem(
                                label = { Text("Saída") },
                                selected = screen == "exit",
                                onClick = { navigate("exit") }
                            )
                            NavigationDrawerItem(
                                label = { Text("Validades") },
                                selected = screen == "expiries",
                                onClick = { navigate("expiries") }
                            )

                            Spacer(Modifier.weight(1f))
                            Text(
                                "Modo computador",
                                color = Color(0xFF6F8296),
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        screenContent()
                    }
                }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = screen == "home",
                                onClick = { navigate("home") },
                                icon = {},
                                label = { Text("Início") }
                            )
                            NavigationBarItem(
                                selected = screen == "products",
                                onClick = { navigate("products") },
                                icon = {},
                                label = { Text("Produtos") }
                            )
                            NavigationBarItem(
                                selected = screen == "entry",
                                onClick = { navigate("entry") },
                                icon = {},
                                label = { Text("Entrada") }
                            )
                            NavigationBarItem(
                                selected = screen == "exit",
                                onClick = { navigate("exit") },
                                icon = {},
                                label = { Text("Saída") }
                            )
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        screenContent()
                    }
                }
            }
        }
    }
}
