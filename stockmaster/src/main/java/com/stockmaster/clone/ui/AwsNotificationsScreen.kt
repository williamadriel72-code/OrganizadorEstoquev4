package com.aws.gestaoestoque.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aws.gestaoestoque.data.AwsDb
import com.aws.gestaoestoque.data.ExpiryRow
import com.aws.gestaoestoque.data.expiryNotifications
import com.aws.gestaoestoque.data.formatExpiryForDisplay

@Composable
internal fun AwsNotificationsScreen(db: AwsDb) {
    val alerts = remember { runCatching { db.expiryNotifications() }.getOrDefault(emptyList()) }
    val expired = alerts.count { awsExpiryStatus(it.expiry) == AwsExpiryStatus.EXPIRED }
    val today = alerts.count { awsExpiryStatus(it.expiry) == AwsExpiryStatus.TODAY }
    val near = alerts.count { awsExpiryStatus(it.expiry) == AwsExpiryStatus.NEAR }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AwsBackground),
        contentPadding = PaddingValues(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AwsHeaderGradient)
                    .padding(horizontal = 18.dp, vertical = 22.dp)
            ) {
                Column(Modifier.fillMaxWidth().widthIn(max = 760.dp).align(Alignment.Center)) {
                    Text("AWS", color = AwsPurpleBright, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                    Text("Notificações", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Alertas de validade dos próximos 30 dias", color = Color(0xFFC8D2E5))
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (alerts.isEmpty()) "Nenhum alerta no momento" else "${alerts.size} alerta(s) de validade",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = AwsText
                    )
                    if (alerts.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NotificationSummary("Vencidos", expired, AwsRed, Modifier.weight(1f))
                            NotificationSummary("Hoje", today, Color(0xFFE67822), Modifier.weight(1f))
                            NotificationSummary("Próximos", near, AwsOrange, Modifier.weight(1f))
                        }
                    } else {
                        Text("Quando houver produto vencido, vencendo hoje ou próximo do vencimento, ele aparecerá aqui.", color = AwsMuted)
                    }
                }
            }
        }

        if (alerts.isNotEmpty()) {
            items(alerts, key = { it.id }) { row ->
                NotificationCard(row)
            }
        }
    }
}

@Composable
private fun NotificationSummary(label: String, count: Int, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = tint.copy(alpha = 0.10f)) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), color = tint, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(label, color = AwsMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun NotificationCard(row: ExpiryRow) {
    val status = awsExpiryStatus(row.expiry)
    val tint = when (status) {
        AwsExpiryStatus.EXPIRED -> AwsRed
        AwsExpiryStatus.TODAY -> Color(0xFFE67822)
        AwsExpiryStatus.NEAR -> AwsOrange
        AwsExpiryStatus.OK -> AwsGreen
        AwsExpiryStatus.UNKNOWN -> AwsMuted
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.width(5.dp).height(66.dp).background(tint, RoundedCornerShape(5.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.description.ifBlank { "Produto ${row.productId}" }, fontWeight = FontWeight.ExtraBold, color = AwsText)
                Text("Código ${row.productId}", color = AwsMuted, style = MaterialTheme.typography.bodySmall)
                Text("Validade: ${formatExpiryForDisplay(row.expiry)}", color = AwsText, fontWeight = FontWeight.SemiBold)
                if (row.lot.isNotBlank()) Text("Lote: ${row.lot}", color = AwsMuted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = 0.12f)) {
                Text(status.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = tint, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
