package com.example.cekpicklist.data

data class PicklistScan(
    val id: Long,
    val noPicklist: String,
    val articleId: String,
    val epc: String,
    val qtyScan: Int,
    val createdAt: String
)
