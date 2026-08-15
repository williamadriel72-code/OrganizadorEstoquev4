package com.stockmaster.clone.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ExpiryStatusKind {
    EXPIRED,
    TODAY,
    NEAR,
    VALID,
    UNKNOWN
}

data class ExpiryStatus(
    val label: String,
    val kind: ExpiryStatusKind,
    val daysUntilExpiry: Long?
)

/**
 * Classifica a validade usando a data local do aparelho.
 * Considera "próximo do vencimento" quando faltam de 1 a 30 dias.
 */
fun expiryStatus(value: String, today: LocalDate = LocalDate.now()): ExpiryStatus {
    val date = parseExpiryLocalDate(value)
        ?: return ExpiryStatus("VALIDADE INVÁLIDA", ExpiryStatusKind.UNKNOWN, null)

    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> ExpiryStatus("VENCIDO", ExpiryStatusKind.EXPIRED, days)
        days == 0L -> ExpiryStatus("VENCE HOJE", ExpiryStatusKind.TODAY, days)
        days <= 30L -> ExpiryStatus("PRÓXIMO DO VENCIMENTO", ExpiryStatusKind.NEAR, days)
        else -> ExpiryStatus("DENTRO DA VALIDADE", ExpiryStatusKind.VALID, days)
    }
}

private fun parseExpiryLocalDate(value: String): LocalDate? {
    val clean = value.trim()

    Regex("""(\d{4})-(\d{2})-(\d{2}).*""").matchEntire(clean)?.let { m ->
        return runCatching {
            LocalDate.of(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].toInt()
            )
        }.getOrNull()
    }

    Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""").matchEntire(clean)?.let { m ->
        return runCatching {
            LocalDate.of(
                m.groupValues[3].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[1].toInt()
            )
        }.getOrNull()
    }

    return null
}
