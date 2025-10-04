package com.example.cekpicklist.test

import android.util.Log
import com.example.cekpicklist.api.NirwanaApiService
import kotlinx.coroutines.runBlocking

/**
 * Test class untuk menguji implementasi NirwanaApiService
 * dengan contoh data yang diberikan
 */
class NirwanaApiTest {
    
    companion object {
        private const val TAG = "NirwanaApiTest"
        
        // Contoh data dari spesifikasi API
        private const val TEST_EPC = "E2000020880F013520006C8F"
        private const val EXPECTED_ARTICLE_ID = "HM.HMN-SJ001"
        private const val EXPECTED_PRODUCT_NAME = "HUMAN GREATNESS LABS, T-SHIRT DARK GREY, XXL"
        private const val EXPECTED_SIZE = "XXL"
    }
    
    /**
     * Test authentication dengan API Nirwana
     */
    fun testAuthentication() = runBlocking {
        try {
            Log.d(TAG, "🔥 Testing Nirwana API Authentication...")
            
            val nirwanaService = NirwanaApiService()
            val isConnected = nirwanaService.testConnection()
            
            if (isConnected) {
                Log.d(TAG, "✅ Authentication test PASSED")
            } else {
                Log.e(TAG, "❌ Authentication test FAILED")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Authentication test ERROR: ${e.message}", e)
        }
    }
    
    /**
     * Test EPC lookup dengan contoh data
     */
    fun testEpcLookup() = runBlocking {
        try {
            Log.d(TAG, "🔥 Testing EPC Lookup with test data...")
            Log.d(TAG, "🔥 Test EPC: $TEST_EPC")
            Log.d(TAG, "🔥 Expected Article ID: $EXPECTED_ARTICLE_ID")
            
            val nirwanaService = NirwanaApiService()
            val articleId = nirwanaService.lookupEpcToArticleId(TEST_EPC)
            
            if (articleId == EXPECTED_ARTICLE_ID) {
                Log.d(TAG, "✅ EPC Lookup test PASSED")
                Log.d(TAG, "✅ Got expected article ID: $articleId")
            } else {
                Log.w(TAG, "⚠️ EPC Lookup test PARTIAL")
                Log.w(TAG, "⚠️ Expected: $EXPECTED_ARTICLE_ID, Got: $articleId")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ EPC Lookup test ERROR: ${e.message}", e)
        }
    }
    
    /**
     * Test product info retrieval dengan contoh data
     */
    fun testProductInfo() = runBlocking {
        try {
            Log.d(TAG, "🔥 Testing Product Info retrieval...")
            Log.d(TAG, "🔥 Test EPC: $TEST_EPC")
            
            val nirwanaService = NirwanaApiService()
            val productInfo = nirwanaService.getProductInfoByEpc(TEST_EPC)
            
            if (productInfo != null) {
                Log.d(TAG, "✅ Product Info test PASSED")
                Log.d(TAG, "✅ Product ID: ${productInfo.productId}")
                Log.d(TAG, "✅ Product Name: ${productInfo.productName}")
                Log.d(TAG, "✅ Article ID: ${productInfo.articleId}")
                Log.d(TAG, "✅ Article Name: ${productInfo.articleName}")
                Log.d(TAG, "✅ Brand: ${productInfo.brand}")
                Log.d(TAG, "✅ Category: ${productInfo.category}")
                Log.d(TAG, "✅ Sub Category: ${productInfo.subCategory}")
                Log.d(TAG, "✅ Color: ${productInfo.color}")
                Log.d(TAG, "✅ Gender: ${productInfo.gender}")
                Log.d(TAG, "✅ Size: ${productInfo.size}")
                Log.d(TAG, "✅ Warehouse: ${productInfo.warehouse}")
                Log.d(TAG, "✅ Tag Status: ${productInfo.tagStatus}")
                Log.d(TAG, "✅ Qty: ${productInfo.qty}")
                
                // Validasi data sesuai contoh
                if (productInfo.articleId == EXPECTED_ARTICLE_ID) {
                    Log.d(TAG, "✅ Article ID validation PASSED")
                } else {
                    Log.w(TAG, "⚠️ Article ID validation FAILED")
                }
                
                if (productInfo.productName.contains("HUMAN GREATNESS LABS")) {
                    Log.d(TAG, "✅ Product Name validation PASSED")
                } else {
                    Log.w(TAG, "⚠️ Product Name validation FAILED")
                }
                
                if (productInfo.size == EXPECTED_SIZE) {
                    Log.d(TAG, "✅ Size validation PASSED")
                } else {
                    Log.w(TAG, "⚠️ Size validation FAILED")
                }
                
            } else {
                Log.e(TAG, "❌ Product Info test FAILED - No data returned")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Product Info test ERROR: ${e.message}", e)
        }
    }
    
    /**
     * Test batch lookup untuk multiple EPCs
     */
    fun testBatchLookup() = runBlocking {
        try {
            Log.d(TAG, "🔥 Testing Batch EPC Lookup...")
            
            val testEpcs = listOf(
                TEST_EPC,
                "E2000020880F013520006C8G", // Contoh EPC lain
                "E2000020880F013520006C8H"  // Contoh EPC lain
            )
            
            val nirwanaService = NirwanaApiService()
            val results = nirwanaService.batchLookupEpcToArticleId(testEpcs)
            
            Log.d(TAG, "🔥 Batch lookup results:")
            results.forEach { (epc, articleId) ->
                Log.d(TAG, "🔥 EPC: $epc -> Article ID: $articleId")
            }
            
            val successCount = results.count { it.value != null }
            Log.d(TAG, "✅ Batch Lookup test completed: $successCount/${testEpcs.size} successful")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Batch Lookup test ERROR: ${e.message}", e)
        }
    }
    
    /**
     * Run semua test
     */
    fun runAllTests() {
        Log.d(TAG, "🔥 === NIRWANA API TEST SUITE START ===")
        
        testAuthentication()
        testEpcLookup()
        testProductInfo()
        testBatchLookup()
        
        Log.d(TAG, "🔥 === NIRWANA API TEST SUITE END ===")
    }
}
