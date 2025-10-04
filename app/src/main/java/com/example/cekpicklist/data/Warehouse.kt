package com.example.cekpicklist.data

/**
 * Data class untuk Warehouse dari API /master/warehouses
 */
data class Warehouse(
    val warehouseId: String,
    val warehouseName: String,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val isActive: Boolean = true
)

/**
 * Data class untuk response API /master/warehouses
 */
data class WarehouseResponse(
    val success: Boolean,
    val message: String,
    val data: List<Warehouse>? = null
)
