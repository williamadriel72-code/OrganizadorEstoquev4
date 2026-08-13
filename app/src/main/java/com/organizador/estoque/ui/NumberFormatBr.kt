package com.organizador.estoque.ui

import java.text.NumberFormat
import java.util.Locale

private val localeBr = Locale("pt", "BR")

fun formatNumberBr(value: Double): String = NumberFormat.getNumberInstance(localeBr).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}.format(value)

fun formatIntegerBr(value: Int): String = NumberFormat.getIntegerInstance(localeBr).format(value)
fun formatIntegerBr(value: Long): String = NumberFormat.getIntegerInstance(localeBr).format(value)

fun parseNumberBr(value: String): Double? = value.trim().replace(".", "").replace(",", ".").toDoubleOrNull()
