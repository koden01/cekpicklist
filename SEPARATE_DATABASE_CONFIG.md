# 🗄️ Separate Database Configuration

## 📋 Overview

Konfigurasi Supabase untuk barcode scanner sekarang menggunakan **database terpisah** sesuai dengan permintaan user.

## 🔧 Database Configuration

### **CekPicklist Database (RFID Data)**
```
URL: https://ngsuhouodaejwkqdxebk.supabase.co
Anon Key: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5nc3Vob3VvZGFlandrcWR4ZWJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIxMTk2ODAsImV4cCI6MjA2NzY5NTY4MH0.r9HISpDXkY5wiTzO5EoNQuqPS3KePc4SScoapepj4h0
Project ID: ngsuhouodaejwkqdxebk
```

### **Barcode Scanner Database**
```
URL: https://yaarzoafxwfcjdpmojxd.supabase.co
Anon Key: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlhYXJ6b2FmeHdmY2pkcG1vanhkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDgwNDcyNDEsImV4cCI6MjA2MzYyMzI0MX0.qdYB__h0YLU4iLKRquyEB44-iyZhBRoJ7lc3pYIkUOI
Project ID: yaarzoafxwfcjdpmojxd
```

## 📁 File Structure

### **Configuration Files**
```
app/src/main/java/com/example/cekpicklist/config/
├── SupabaseConfig.kt              # CekPicklist database config
└── BarcodeSupabaseConfig.kt       # Barcode scanner database config
```

### **Service Files**
```
app/src/main/java/com/example/cekpicklist/api/
├── SupabaseService.kt             # CekPicklist database service
├── BarcodeSupabaseService.kt      # Barcode scanner database service
└── BarcodeExpedisiService.kt      # Barcode expedisi validation service
```

## 🔄 Data Flow

### **RFID Data Flow**
```
CekPicklistActivity → SupabaseService → SupabaseConfig → ngsuhouodaejwkqdxebk.supabase.co
```

### **Barcode Data Flow**
```
BarcodeScannerActivity → BarcodeSupabaseService → BarcodeSupabaseConfig → yaarzoafxwfcjdpmojxd.supabase.co
```

### **Barcode Validation Flow**
```
BarcodeScannerActivity → ExpedisiValidator → BarcodeExpedisiService → yaarzoafxwfcjdpmojxd.supabase.co
```

## 🗄️ Database Schema

### **CekPicklist Database (ngsuhouodaejwkqdxebk)**
```sql
-- RFID related tables
picklist
picklist_scan
```

### **Barcode Scanner Database (yaarzoafxwfcjdpmojxd)**
```sql
-- Barcode scanner tables
barcode_scan
barcode_session
barcode_performance

-- Expedisi validation tables (from azure-chinchilla-swoop)
tbl_resi
tbl_expedisi
```

## 🔧 Configuration Details

### **SupabaseConfig.kt** (CekPicklist)
```kotlin
object SupabaseConfig {
    const val SUPABASE_URL = "https://ngsuhouodaejwkqdxebk.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5nc3Vob3VvZGFlandrcWR4ZWJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIxMTk2ODAsImV4cCI6MjA2NzY5NTY4MH0.r9HISpDXkY5wiTzO5EoNQuqPS3KePc4SScoapepj4h0"
}
```

### **BarcodeSupabaseConfig.kt** (Barcode Scanner)
```kotlin
object BarcodeSupabaseConfig {
    // Database terpisah untuk barcode scanner (ACTIVE)
    const val SUPABASE_URL = "https://yaarzoafxwfcjdpmojxd.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlhYXJ6b2FmeHdmY2pkcG1vanhkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDgwNDcyNDEsImV4cCI6MjA2MzYyMzI0MX0.qdYB__h0YLU4iLKRquyEB44-iyZhBRoJ7lc3pYIkUOI"
    
    const val PROJECT_ID = "yaarzoafxwfcjdpmojxd"
}
```

## 🔍 Service Implementation

### **BarcodeExpedisiService.kt**
```kotlin
class BarcodeExpedisiService {
    private val supabase = createSupabaseClient(
        supabaseUrl = BarcodeSupabaseConfig.SUPABASE_URL,
        supabaseKey = BarcodeSupabaseConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
    }
    
    // Methods untuk validasi expedisi
    suspend fun checkResiInDatabase(resi: String): ResiRecord?
    suspend fun checkExpedisiInDatabase(resi: String): ExpedisiRecord?
    suspend fun getAllScannedResiNumbers(): List<String>
    suspend fun getAllFlagNoExpedisiData(): List<ExpedisiRecord>
    suspend fun getAllFlagYesExpedisiResiNumbers(): List<String>
}
```

### **ExpedisiValidator.kt**
```kotlin
class ExpedisiValidator(private val barcodeExpedisiService: BarcodeExpedisiService) {
    suspend fun validateBarcode(
        barcode: String,
        selectedExpedisi: String? = null,
        selectedKarung: String? = null
    ): ValidationResult
}
```

## 🚀 Setup Instructions

### **1. Database Setup**
```sql
-- Setup barcode database di yaarzoafxwfcjdpmojxd.supabase.co
-- Run setup_barcode_database.sql
```

### **2. Configuration Verification**
```kotlin
// Check configuration
Log.d("Config", "CekPicklist Database: ${SupabaseConfig.SUPABASE_URL}")
Log.d("Config", "Barcode Database: ${BarcodeSupabaseConfig.SUPABASE_URL}")
Log.d("Config", "Separate Databases: ${BarcodeSupabaseConfig.isSeparateDatabase()}")
```

### **3. Service Initialization**
```kotlin
// BarcodeScannerActivity.kt
private fun setupViewModel() {
    viewModel = ViewModelProvider(this)[BarcodeScannerViewModel::class.java]
    
    // Initialize expedisi validator
    viewModel.initializeExpedisiValidator()
}
```

## ✅ Benefits dari Separate Database

### **1. Data Isolation**
- ✅ RFID data terpisah dari barcode data
- ✅ Independent scaling
- ✅ Separate security policies
- ✅ Isolated backup/restore

### **2. Performance**
- ✅ Dedicated resources untuk barcode scanner
- ✅ Optimized queries untuk masing-masing use case
- ✅ Reduced cross-database queries

### **3. Security**
- ✅ Separate access controls
- ✅ Independent RLS policies
- ✅ Isolated audit trails

### **4. Maintenance**
- ✅ Independent updates
- ✅ Separate monitoring
- ✅ Isolated troubleshooting

## 🔄 Data Integration

### **Barcode Validation Flow**
```kotlin
// ExpedisiValidator menggunakan barcode database
class ExpedisiValidator(private val barcodeExpedisiService: BarcodeExpedisiService) {
    
    suspend fun validateBarcode(barcode: String): ValidationResult {
        // 1. Check in tbl_resi (barcode database)
        val resiDetails = checkResiInDatabase(barcode)
        
        // 2. Check in tbl_expedisi (barcode database)
        val expedisiRecord = checkExpedisiInDatabase(barcode)
        
        // 3. Validate expedition mismatch
        // 4. Determine schedule
    }
}
```

### **Background Sync Flow**
```kotlin
// BackgroundSyncManager menggunakan barcode database
class BackgroundSyncManager {
    
    private suspend fun processScanOperation(operation: PendingOperation): Boolean {
        // Save to barcode_scan table (barcode database)
        val record = BarcodeSupabaseService.BarcodeScanRecord(...)
        return barcodeSupabaseService.saveBarcodeScan(record)
    }
}
```

## 📊 Database Tables

### **CekPicklist Database Tables**
```sql
-- RFID related
picklist
picklist_scan
```

### **Barcode Scanner Database Tables**
```sql
-- Barcode scanner specific
barcode_scan
barcode_session
barcode_performance

-- Expedisi validation (from azure-chinchilla-swoop)
tbl_resi
tbl_expedisi
```

## 🎯 Current Status

### **✅ Implemented**
- ✅ Separate database configuration
- ✅ BarcodeSupabaseService using separate database
- ✅ BarcodeExpedisiService using separate database
- ✅ ExpedisiValidator using separate database
- ✅ Background sync with separate database
- ✅ Cross-table validation within barcode database

### **🔄 Data Flow**
```
1. Barcode Scanner → BarcodeSupabaseService → yaarzoafxwfcjdpmojxd.supabase.co (barcode_scan)
2. Validation → ExpedisiValidator → yaarzoafxwfcjdpmojxd.supabase.co (tbl_resi, tbl_expedisi)
3. Background Sync → yaarzoafxwfcjdpmojxd.supabase.co (all barcode tables)
4. RFID Operations → SupabaseService → ngsuhouodaejwkqdxebk.supabase.co (picklist, picklist_scan)
```

## 📝 Migration Notes

### **From Shared to Separate**
1. ✅ **Configuration Updated**: BarcodeSupabaseConfig now uses separate database
2. ✅ **Service Updated**: BarcodeSupabaseService uses separate configuration
3. ✅ **Validation Updated**: ExpedisiValidator uses BarcodeExpedisiService
4. ✅ **Sync Updated**: BackgroundSyncManager uses separate database

### **Database Schema**
```sql
-- CekPicklist Database (ngsuhouodaejwkqdxebk)
picklist
picklist_scan

-- Barcode Scanner Database (yaarzoafxwfcjdpmojxd)
barcode_scan
barcode_session
barcode_performance
tbl_resi
tbl_expedisi
```

---

**✅ Konfigurasi database terpisah untuk barcode scanner sudah selesai!** 🎉

