# 🗄️ Supabase Configuration - Separate Databases

## 📋 Overview

Aplikasi sekarang menggunakan **konfigurasi Supabase yang terpisah** untuk `cekpicklist` (RFID data) dan `scanbarcode` (barcode scanner data) sesuai dengan kebutuhan yang berbeda.

## 🔧 Configuration Files

### 1. **SupabaseConfig.kt** - Untuk CekPicklist (RFID)
```kotlin
object SupabaseConfig {
    // ✅ KONFIGURASI SUPABASE UNTUK CEKPICKLIST (RFID DATA)
    const val SUPABASE_URL = "https://ngsuhouodaejwkqdxebk.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 2. **BarcodeSupabaseConfig.kt** - Untuk Barcode Scanner
```kotlin
object BarcodeSupabaseConfig {
    // 🔧 KONFIGURASI SUPABASE UNTUK BARCODE SCANNER
    const val SUPABASE_URL = "https://your-barcode-supabase-project.supabase.co"
    const val SUPABASE_ANON_KEY = "your-barcode-supabase-anon-key"
    
    // Database info
    const val DATABASE_NAME = "barcode_scanner_db"
    const val DATABASE_VERSION = "1.0"
    
    object Tables {
        const val BARCODE_SCAN = "barcode_scan"
        const val BARCODE_SESSION = "barcode_session"
        const val BARCODE_PERFORMANCE = "barcode_performance"
    }
}
```

## 🏗️ Database Architecture

### **CekPicklist Database** (RFID Data)
```
Database: ngsuhouodaejwkqdxebk.supabase.co
Tables:
├── picklist
├── picklist_scan
├── tbl_resi (dari azure-chinchilla-swoop)
└── tbl_expedisi (dari azure-chinchilla-swoop)
```

### **Barcode Scanner Database** (Barcode Data)
```
Database: your-barcode-supabase-project.supabase.co
Tables:
├── barcode_scan
├── barcode_session
└── barcode_performance
```

## 🔄 Service Mapping

### **SupabaseService.kt** - RFID Operations
```kotlin
class SupabaseService {
    // Menggunakan SupabaseConfig
    private val supabaseUrl = SupabaseConfig.SUPABASE_URL
    private val supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
    
    // Operations untuk RFID data
    suspend fun getPicklistItems(): List<PicklistItem>
    suspend fun insertPicklistScan(scanData: PicklistScanData): Boolean
    // ... RFID operations
}
```

### **BarcodeSupabaseService.kt** - Barcode Operations
```kotlin
class BarcodeSupabaseService {
    // Menggunakan BarcodeSupabaseConfig
    private val supabaseUrl = BarcodeSupabaseConfig.SUPABASE_URL
    private val supabaseKey = BarcodeSupabaseConfig.SUPABASE_ANON_KEY
    
    // Operations untuk barcode data
    suspend fun saveBarcodeScan(record: BarcodeScanRecord): Boolean
    suspend fun saveBarcodeSession(record: BarcodeSessionRecord): String?
    // ... Barcode operations
}
```

## ⚙️ Configuration Options

### **Option 1: Separate Databases (Recommended)**
```kotlin
// BarcodeSupabaseConfig.kt
const val SUPABASE_URL = "https://your-barcode-supabase-project.supabase.co"
const val SUPABASE_ANON_KEY = "your-barcode-supabase-anon-key"
```
**Benefits:**
- ✅ Data isolation
- ✅ Independent scaling
- ✅ Separate security policies
- ✅ Different backup strategies

### **Option 2: Same Database, Different Tables**
```kotlin
// BarcodeSupabaseConfig.kt
const val SUPABASE_URL = SupabaseConfig.SUPABASE_URL
const val SUPABASE_ANON_KEY = SupabaseConfig.SUPABASE_ANON_KEY
```
**Benefits:**
- ✅ Single database management
- ✅ Shared authentication
- ✅ Easier cross-table queries

### **Option 3: Same Database, Same Tables**
```kotlin
// BarcodeSupabaseConfig.kt
const val SUPABASE_URL = SupabaseConfig.SUPABASE_URL
const val SUPABASE_ANON_KEY = SupabaseConfig.SUPABASE_ANON_KEY
```
**Benefits:**
- ✅ Unified data model
- ✅ Single source of truth
- ✅ Simplified architecture

## 🚀 Setup Instructions

### **1. Create Separate Supabase Project (Option 1)**
```bash
# 1. Go to https://supabase.com
# 2. Create new project for barcode scanner
# 3. Get project URL and anon key
# 4. Update BarcodeSupabaseConfig.kt
```

### **2. Run Database Setup**
```sql
-- Run setup_barcode_database.sql in new Supabase project
-- This creates barcode_scan, barcode_session, barcode_performance tables
```

### **3. Update Configuration**
```kotlin
// BarcodeSupabaseConfig.kt
const val SUPABASE_URL = "https://your-new-project.supabase.co"
const val SUPABASE_ANON_KEY = "your-new-anon-key"
```

## 🔍 Configuration Validation

### **Check Database Separation**
```kotlin
// Check if using separate databases
val isSeparate = BarcodeSupabaseConfig.isSeparateDatabase()
Log.d("Config", BarcodeSupabaseConfig.getDatabaseInfo())
```

### **Service Initialization**
```kotlin
// In BarcodeScannerActivity
val barcodeSupabaseService = BarcodeSupabaseService() // Uses BarcodeSupabaseConfig
val supabaseService = SupabaseService() // Uses SupabaseConfig

// Initialize ViewModel
viewModel.initializeBackgroundSync(barcodeSupabaseService)
viewModel.initializeExpedisiValidator(supabaseService) // For validation against RFID data
```

## 📊 Data Flow

### **RFID Data Flow**
```
CekPicklistActivity → SupabaseService → SupabaseConfig → RFID Database
```

### **Barcode Data Flow**
```
BarcodeScannerActivity → BarcodeSupabaseService → BarcodeSupabaseConfig → Barcode Database
```

### **Cross-Reference Flow**
```
BarcodeScannerActivity → ExpedisiValidator → SupabaseService → RFID Database (for validation)
```

## 🔐 Security Considerations

### **Separate Databases**
- ✅ Independent RLS policies
- ✅ Separate API keys
- ✅ Isolated access control
- ✅ Different backup schedules

### **Same Database**
- ⚠️ Shared RLS policies
- ⚠️ Same API key
- ⚠️ Unified access control
- ⚠️ Single backup schedule

## 🎯 Benefits of Separation

### **1. Data Isolation**
- RFID data dan barcode data terpisah
- Tidak ada konflik data
- Independent data management

### **2. Performance**
- Optimized queries untuk masing-masing use case
- Independent scaling
- Reduced cross-table dependencies

### **3. Security**
- Separate access controls
- Independent security policies
- Isolated data breaches

### **4. Maintenance**
- Independent updates
- Separate backup strategies
- Easier troubleshooting

## 🔧 Migration Guide

### **From Single Database to Separate**
1. **Create new Supabase project** untuk barcode scanner
2. **Run setup script** di project baru
3. **Update BarcodeSupabaseConfig.kt** dengan URL dan key baru
4. **Test connectivity** dan data operations
5. **Migrate existing data** jika diperlukan

### **Configuration Testing**
```kotlin
// Test both configurations
fun testConfigurations() {
    // Test RFID database
    val rfidService = SupabaseService()
    // Test connection...
    
    // Test barcode database
    val barcodeService = BarcodeSupabaseService()
    // Test connection...
}
```

## 📝 Current Status

### **✅ Implemented**
- ✅ Separate configuration files
- ✅ BarcodeSupabaseService using BarcodeSupabaseConfig
- ✅ SupabaseService using SupabaseConfig
- ✅ Documentation and setup guide

### **🔄 Next Steps**
1. **Create new Supabase project** untuk barcode scanner
2. **Update BarcodeSupabaseConfig.kt** dengan credentials baru
3. **Run setup_barcode_database.sql** di project baru
4. **Test data operations** di kedua database

---

**✅ Konfigurasi Supabase sekarang terpisah untuk cekpicklist dan scanbarcode sesuai kebutuhan!**

