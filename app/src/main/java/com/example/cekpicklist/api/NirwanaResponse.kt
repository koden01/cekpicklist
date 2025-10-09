package com.example.cekpicklist.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data classes untuk response API Nirwana menggunakan org.json
 */

/**
 * Response utama dari API Nirwana batch lookup
 */
data class NirwanaBatchResponse(
    val length: Int,
    val data: List<NirwanaProductData>
) {
    companion object {
        fun fromJson(jsonObject: JSONObject): NirwanaBatchResponse {
            val length = jsonObject.getInt("length")
            val dataArray = jsonObject.getJSONArray("data")
            val data = mutableListOf<NirwanaProductData>()
            
            for (i in 0 until dataArray.length()) {
                val itemJson = dataArray.getJSONObject(i)
                data.add(NirwanaProductData.fromJson(itemJson))
            }
            
            return NirwanaBatchResponse(length, data)
        }
    }
}

/**
 * Data produk individual dari API Nirwana
 */
data class NirwanaProductData(
    val index: Int,
    val productId: String,
    val productName: String,
    val articleId: String,
    val articleName: String,
    val brand: String,
    val category: String,
    val subCategory: String,
    val color: String,
    val gender: String,
    val size: String,
    val warehouse: String,
    val tagStatus: String,
    val qty: Int,
    val rfidList: List<String>
) {
    companion object {
        fun fromJson(jsonObject: JSONObject): NirwanaProductData {
            val rfidArray = jsonObject.getJSONArray("rfid_list")
            val rfidList = mutableListOf<String>()
            
            for (i in 0 until rfidArray.length()) {
                rfidList.add(rfidArray.getString(i))
            }
            
            return NirwanaProductData(
                index = jsonObject.getInt("index"),
                productId = jsonObject.getString("product_id"),
                productName = jsonObject.getString("product_name"),
                articleId = jsonObject.getString("article_id"),
                articleName = jsonObject.getString("article_name"),
                brand = jsonObject.getString("brand"),
                category = jsonObject.getString("category"),
                subCategory = jsonObject.getString("sub_category"),
                color = jsonObject.getString("color"),
                gender = jsonObject.getString("gender"),
                size = jsonObject.getString("size"),
                warehouse = jsonObject.getString("warehouse"),
                tagStatus = jsonObject.getString("tag_status"),
                qty = jsonObject.getInt("qty"),
                rfidList = rfidList
            )
        }
    }
}

/**
 * Response untuk single EPC lookup
 */
data class NirwanaSingleResponse(
    val length: Int,
    val data: List<NirwanaProductData>
) {
    companion object {
        fun fromJson(jsonObject: JSONObject): NirwanaSingleResponse {
            val length = jsonObject.getInt("length")
            val dataArray = jsonObject.getJSONArray("data")
            val data = mutableListOf<NirwanaProductData>()
            
            for (i in 0 until dataArray.length()) {
                val itemJson = dataArray.getJSONObject(i)
                data.add(NirwanaProductData.fromJson(itemJson))
            }
            
            return NirwanaSingleResponse(length, data)
        }
    }
}

