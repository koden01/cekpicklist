package com.example.cekpicklist.data

data class ScanResult(
    val rfid: String,
    val productId: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val rssi: Int = 0,  // Tambahkan RSSI dengan default 0
    val warehouse: String? = null, // warehouse untuk item NA
    val tagStatus: String? = null // tag_status untuk item NA
)

data class NirwanaAuthRequest(
    val username: String,
    val password: String
)

data class NirwanaAuthResponse(
    val access_token: String
)

data class NirwanaScanRequest(
    val rfid_list: String,
    val limit: String = "100"  // Default limit 100
)

data class NirwanaScanResponse(
    val rfid: String,
    val product_id: String,
    val article_id: String,
    val article_name: String,
    val size: String
)

// Response wrapper untuk API Nirwana
data class NirwanaScanResponseWrapper(
    val length: Int,
    val data: List<NirwanaScanResponseItem>
)

// Item dalam response data
data class NirwanaScanResponseItem(
    val index: Int,
    val product_id: String,
    val product_name: String,
    val article_id: String,
    val article_name: String,
    val brand: String,
    val category: String,
    val sub_category: String,
    val color: String,
    val gender: String,
    val size: String,
    val warehouse: String,
    val tag_status: String,
    val qty: Int,
    val rfid_list: List<String>
)

// Data class untuk tabel picklist sesuai struktur yang diberikan
data class Picklist(
    val id: String, // uuid
    val noPicklist: String, // no_picklist
    val articleId: String, // article_id
    val size: String?, // size
    val productId: String?, // product_id
    val qty: Int, // qty
    val createdAt: String?, // created_at
    val articleName: String? // article_name
)

// Updated to match actual picklist_scan table structure
// PicklistScan moved to separate file
