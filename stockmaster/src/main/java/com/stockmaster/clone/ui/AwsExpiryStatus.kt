package com.stockmaster.clone.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class AwsExpiryStatus(val label: String) {
    EXPIRED("VENCIDO"),
    TODAY("VENCE HOJE"),
    NEAR("PRÓXIMO DO VENCIMENTO"),
    OK("DENTRO DA VALIDADE"),
    UNKNOWN("VALIDADE NÃO RECONHECIDA")
}

fun awsExpiryStatus(expiry: String, today: LocalDate = LocalDate.now()): AwsExpiryStatus {
    val clean = expiry.trim()
    val date = runCatching { LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE) }
        .recoverCatching { LocalDate.parse(clean, DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
        .getOrNull()
        ?: return AwsExpiryStatus.UNKNOWN

    return when {
        date.isBefore(today) -> AwsExpiryStatus.EXPIRED
        date.isEqual(today) -> AwsExpiryStatus.TODAY
        !date.isAfter(today.plusDays(30)) -> AwsExpiryStatus.NEAR
        else -> AwsExpiryStatus.OK
    }
}
