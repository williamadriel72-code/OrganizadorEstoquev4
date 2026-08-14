package com.organizador.estoque.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.organizador.estoque.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardV2(repository: InventoryRepository, refreshKey: Int, openProducts: (String)->Unit, openExpiries: ()->Unit) {
    val insights = remember { InventoryInsights(LocalContext.current) }
    var s by remember { mutableStateOf(DashboardStats()) }
    var neg by remember { mutableLongStateOf(0L) }
    LaunchedEffect(refreshKey) {
        s = withContext(Dispatchers.IO) { repository.dashboardStats() }
        neg = withContext(Dispatchers.IO) { insights.negativeCount() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Organizador de Estoque", fontSize=27.sp, fontWeight=FontWeight.ExtraBold) }
        item { Button({openProducts("all")}, Modifier.fillMaxWidth()) { Text("PESQUISAR PRODUTO") } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            DCard("Produtos",s.products,Color(0xFF40A0FF),Modifier.weight(1f)){openProducts("all")}
            DCard("Estoque Baixo",s.lowStock,Color(0xFFFFB938),Modifier.weight(1f)){openProducts("low")}
        }}
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            DCard("Zerados",s.zeroStock,Color(0xFFFF5368),Modifier.weight(1f)){openProducts("zero")}
            DCard("Negativos",neg,Color(0xFFFF7A59),Modifier.weight(1f)){openProducts("negative")}
        }}
        item { DCard("Sem Endereço",s.withoutAddress,Color(0xFF8D63F6),Modifier.fillMaxWidth()){openProducts("no_address")} }
        item { Text("Validades",fontSize=20.sp,fontWeight=FontWeight.Bold) }
        item { Card(onClick=openExpiries,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Acompanhar validades",fontWeight=FontWeight.Bold)
                Text("Toque para ver quais produtos e lotes",color=Color(0xFF9FB0C4))
                Spacer(Modifier.height(8.dp))
                Text("Vencidos ${formatIntegerBr(s.expired)}  •  7 dias ${formatIntegerBr(s.expiring7)}  •  30 dias ${formatIntegerBr(s.expiring30)}  •  60 dias ${formatIntegerBr(s.expiring60)}")
            }
        }}
        item { Text("Importar dados",fontSize=20.sp,fontWeight=FontWeight.Bold) }
        item { PdfImportBar(repository) }
    }
}

@Composable private fun DCard(t:String,v:Long,c:Color,m:Modifier,on:()->Unit){
    Card(onClick=on,modifier=m.height(115.dp),shape=RoundedCornerShape(18.dp)){
        Column(Modifier.fillMaxSize().padding(15.dp),verticalArrangement=Arrangement.SpaceBetween){
            Text(t,color=Color(0xFF9FB0C4));Text(formatIntegerBr(v),color=c,fontSize=26.sp,fontWeight=FontWeight.ExtraBold)
        }
    }
}
