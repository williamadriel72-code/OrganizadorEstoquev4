package com.aws.gestaoestoque.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val AwsNavy = Color(0xFF071A3A)
internal val AwsNavyDeep = Color(0xFF04112A)
internal val AwsPurple = Color(0xFF7447E8)
internal val AwsPurpleBright = Color(0xFF9B63FF)
internal val AwsBlue = Color(0xFF4878F3)
internal val AwsBackground = Color(0xFFF4F6FB)
internal val AwsCard = Color(0xFFFFFFFF)
internal val AwsText = Color(0xFF151A25)
internal val AwsMuted = Color(0xFF697386)
internal val AwsGreen = Color(0xFF24965A)
internal val AwsOrange = Color(0xFFF39A31)
internal val AwsRed = Color(0xFFE14D4D)
internal val AwsTeal = Color(0xFF2D9C95)

internal val AwsPrimaryGradient = Brush.horizontalGradient(
    listOf(AwsPurple, AwsPurpleBright)
)

internal val AwsHeaderGradient = Brush.verticalGradient(
    listOf(AwsNavyDeep, AwsNavy)
)

private val AwsColorScheme = lightColorScheme(
    primary = AwsPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0E9FF),
    onPrimaryContainer = Color(0xFF24134D),
    secondary = AwsBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EEFF),
    onSecondaryContainer = Color(0xFF17264F),
    tertiary = AwsOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBD1),
    onTertiaryContainer = Color(0xFF5A3510),
    error = AwsRed,
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF681B1B),
    background = AwsBackground,
    onBackground = AwsText,
    surface = AwsCard,
    onSurface = AwsText,
    surfaceVariant = Color(0xFFEFF1F6),
    onSurfaceVariant = AwsMuted,
    outline = Color(0xFFD8DCE6)
)

@Composable
internal fun AwsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AwsColorScheme,
        typography = Typography(),
        content = content
    )
}

@Composable
internal fun AwsGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        shadowElevation = if (enabled) 5.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (enabled) AwsPrimaryGradient else Brush.horizontalGradient(listOf(Color(0xFFB7B8BF), Color(0xFFC7C8CE))))
                .padding(horizontal = 18.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun AwsBrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(78.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF233E76), AwsPurple))),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text("◇", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
    }
}

internal data class AwsModuleVisual(
    val symbol: String,
    val tint: Color,
    val soft: Color,
    val subtitle: String
)

internal fun awsModuleVisual(id: String): AwsModuleVisual = when (id) {
    "price" -> AwsModuleVisual("⌕", AwsPurple, Color(0xFFF0E9FF), "Consulte preços de produtos")
    "restock" -> AwsModuleVisual("↻", AwsPurple, Color(0xFFF0E9FF), "Sugestão e controle de reposição")
    "prevenda" -> AwsModuleVisual("▣", AwsPurple, Color(0xFFF0E9FF), "Atendimento e orçamento de vendas")
    "inventory" -> AwsModuleVisual("▤", AwsBlue, Color(0xFFE8EEFF), "Contagem e gestão de estoque")
    "conference" -> AwsModuleVisual("✓", AwsGreen, Color(0xFFE2F5EA), "Conferir mercadorias e volumes")
    "print" -> AwsModuleVisual("▥", AwsBlue, Color(0xFFE8EEFF), "Imprimir etiquetas e relatórios")
    "damage" -> AwsModuleVisual("!", AwsOrange, Color(0xFFFFEBD1), "Registro e controle de avarias")
    "movement" -> AwsModuleVisual("⇄", AwsTeal, Color(0xFFE0F5F2), "Transferências e ajustes de estoque")
    "expiry" -> AwsModuleVisual("▦", AwsPurple, Color(0xFFF0E9FF), "Acompanhe validade dos produtos e lotes")
    else -> AwsModuleVisual("•", AwsPurple, Color(0xFFF0E9FF), "")
}

@Composable
internal fun AwsModuleIcon(visual: AwsModuleVisual, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(visual.soft),
        contentAlignment = Alignment.Center
    ) {
        Text(visual.symbol, color = visual.tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}
