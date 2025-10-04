package com.example.cekpicklist.api

import com.google.gson.annotations.SerializedName

/**
 * Data classes untuk response API Nirwana menggunakan Gson
 */

/**
 * Response utama dari API Nirwana batch lookup
 */
data class NirwanaBatchResponse(
    @SerializedName("length")
    val length: Int,
    
    @SerializedName("data")
    val data: List<NirwanaProductData>
)

/**
 * Data produk individual dari API Nirwana
 */
data class NirwanaProductData(
    @SerializedName("index")
    val index: Int,
    
    @SerializedName("product_id")
    val productId: String,
    
    @SerializedName("product_name")
    val productName: String,
    
    @SerializedName("article_id")
    val articleId: String,
    
    @SerializedName("article_name")
    val articleName: String,
    
    @SerializedName("brand")
    val brand: String,
    
    @SerializedName("category")
    val category: String,
    
    @SerializedName("sub_category")
    val subCategory: String,
    
    @SerializedName("color")
    val color: String,
    
    @SerializedName("gender")
    val gender: String,
    
    @SerializedName("size")
    val size: String,
    
    @SerializedName("warehouse")
    val warehouse: String,
    
    @SerializedName("tag_status")
    val tagStatus: String,
    
    @SerializedName("qty")
    val qty: Int,
    
    @SerializedName("rfid_list")
    val rfidList: List<String>
)

/**
 * Response untuk single EPC lookup
 */
data class NirwanaSingleResponse(
    @SerializedName("length")
    val length: Int,
    
    @SerializedName("data")
    val data: List<NirwanaProductData>
)

