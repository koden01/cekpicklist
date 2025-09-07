package com.example.cekpicklist.api

import com.example.cekpicklist.data.*
import retrofit2.Response
import retrofit2.http.*

interface NirwanaApiService {
    @POST("auth/token")
    suspend fun authenticate(
        @Body request: NirwanaAuthRequest
    ): Response<NirwanaAuthResponse>
    
    @POST("tag/products")
    suspend fun getProductInfo(
        @Header("Authorization") token: String,
        @Body request: NirwanaScanRequest
    ): Response<NirwanaScanResponseWrapper>
}
