package com.example.cekpicklist.config

/**
 * Barcode Supabase Configuration
 * Konfigurasi terpisah untuk barcode scanner
 * Menggunakan database yang berbeda dari cekpicklist
 */
object BarcodeSupabaseConfig {
    
    // 🔧 KONFIGURASI SUPABASE UNTUK BARCODE SCANNER
    
    // ⚠️ PILIHAN KONFIGURASI - Hapus comment untuk mengaktifkan:
    
    // Option 1: Database terpisah untuk barcode scanner (ACTIVE)
    // Database khusus untuk barcode scanner
    const val SUPABASE_URL = "https://yaarzoafxwfcjdpmojxd.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlhYXJ6b2FmeHdmY2pkcG1vanhkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDgwNDcyNDEsImV4cCI6MjA2MzYyMzI0MX0.qdYB__h0YLU4iLKRquyEB44-iyZhBRoJ7lc3pYIkUOI"
    
    // Option 2: Database yang sama dengan cekpicklist (DISABLED)
    // const val SUPABASE_URL = SupabaseConfig.SUPABASE_URL
    // const val SUPABASE_ANON_KEY = SupabaseConfig.SUPABASE_ANON_KEY
    
    // Option 3: Database yang sama dengan konfigurasi eksplisit
    // const val SUPABASE_URL = "https://ngsuhouodaejwkqdxebk.supabase.co"
    // const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5nc3Vob3VvZGFlandrcWR4ZWJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIxMTk2ODAsImV4cCI6MjA2NzY5NTY4MH0.r9HISpDXkY5wiTzO5EoNQuqPS3KePc4SScoapepj4h0"
    
    /**
     * Database configuration info
     */
    const val DATABASE_NAME = "barcode_scanner_db"
    const val DATABASE_VERSION = "1.0"
    const val PROJECT_ID = "yaarzoafxwfcjdpmojxd"
    
    /**
     * Table names untuk barcode scanner
     */
    object Tables {
        const val BARCODE_SCAN = "barcode_scan"
        const val BARCODE_SESSION = "barcode_session"
        const val BARCODE_PERFORMANCE = "barcode_performance"
    }
    
    /**
     * Check if barcode scanner uses separate database
     */
    fun isSeparateDatabase(): Boolean {
        return SUPABASE_URL != SupabaseConfig.SUPABASE_URL
    }
    
    /**
     * Get database info for logging
     */
    fun getDatabaseInfo(): String {
        return if (isSeparateDatabase()) {
            "Barcode Scanner menggunakan database terpisah: $SUPABASE_URL"
        } else {
            "Barcode Scanner menggunakan database yang sama dengan CekPicklist: $SUPABASE_URL"
        }
    }
}
