package com.organizador.estoque.data

data class Product(
    val code: String,
    val ean: String?,
    val description: String,
    val groupCode: String?,
    val category: String?,
    val stock: Double,
    val controlsExpiry: Boolean,
    val active: Boolean = true
)

data class ExpiryBatch(
    val id: Long = 0,
    val productCode: String,
    val expiryDate: String,
    val quantity: Double
)

data class DashboardStats(
    val products: Long = 0,
    val totalStock: Double = 0.0,
    val lowStock: Long = 0,
    val zeroStock: Long = 0,
    val negativeStock: Long = 0,
    val withoutAddress: Long = 0,
    val expired: Long = 0,
    val expiring7: Long = 0,
    val expiring30: Long = 0,
    val expiring60: Long = 0
)
