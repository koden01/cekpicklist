# 🔗 Supabase Configuration Alignment

## 📋 Overview

Konfigurasi Supabase untuk barcode scanner sekarang **sesuai** dengan aplikasi `azure-chinchilla-swoop` yang sudah berjalan baik.

## 🔍 Analysis dari azure-chinchilla-swoop

### **Konfigurasi Web App**
```typescript
// src/integrations/supabase/client.ts
const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL;
const SUPABASE_PUBLISHABLE_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY;

export const supabase = createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY);
```

### **Environment Variables**
```typescript
// src/vite-env.d.ts
interface ImportMetaEnv {
  readonly VITE_SUPABASE_URL: string;
  readonly VITE_SUPABASE_ANON_KEY: string;
}
```

## 🔧 Current Configuration

### **CekPicklist (RFID Data)**
```kotlin
// SupabaseConfig.kt
object SupabaseConfig {
    const val SUPABASE_URL = "https://ngsuhouodaejwkqdxebk.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5nc3Vob3VvZGFlandrcWR4ZWJrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIxMTk2ODAsImV4cCI6MjA2NzY5NTY4MH0.r9HISpDXkY5wiTzO5EoNQuqPS3KePc4SScoapepj4h0"
}
```

### **Barcode Scanner (Sesuai azure-chinchilla-swoop)**
```kotlin
// BarcodeSupabaseConfig.kt
object BarcodeSupabaseConfig {
    // Menggunakan database yang sama dengan aplikasi web azure-chinchilla-swoop
    const val SUPABASE_URL = SupabaseConfig.SUPABASE_URL
    const val SUPABASE_ANON_KEY = SupabaseConfig.SUPABASE_ANON_KEY
}
```

## 🗄️ Database Structure

### **Shared Database** (sesuai azure-chinchilla-swoop)
```
Database: ngsuhouodaejwkqdxebk.supabase.co

Tables:
├── tbl_resi (dari azure-chinchilla-swoop)
├── tbl_expedisi (dari azure-chinchilla-swoop)
├── picklist (dari cekpicklist)
├── picklist_scan (dari cekpicklist)
├── barcode_scan (baru untuk barcode scanner)
├── barcode_session (baru untuk barcode scanner)
└── barcode_performance (baru untuk barcode scanner)
```

## 🔄 Data Flow

### **RFID Data Flow**
```
CekPicklistActivity → SupabaseService → SupabaseConfig → Shared Database
```

### **Barcode Data Flow**
```
BarcodeScannerActivity → BarcodeSupabaseService → BarcodeSupabaseConfig → Shared Database
```

### **Cross-Reference Flow**
```
BarcodeScannerActivity → ExpedisiValidator → SupabaseService → Shared Database (tbl_resi, tbl_expedisi)
```

## ✅ Benefits dari Shared Database

### **1. Data Consistency**
- ✅ Semua data dalam satu database
- ✅ Konsistensi referensi antar tabel
- ✅ Single source of truth

### **2. Cross-Table Queries**
- ✅ Validasi barcode terhadap `tbl_expedisi`
- ✅ Cross-reference dengan `tbl_resi`
- ✅ Unified reporting

### **3. Simplified Management**
- ✅ Single database backup
- ✅ Unified security policies
- ✅ Single monitoring dashboard

### **4. Cost Efficiency**
- ✅ Single Supabase project
- ✅ Shared resources
- ✅ Reduced complexity

## 🔧 Configuration Options

### **Option 1: Shared Database (CURRENT - Recommended)**
```kotlin
// BarcodeSupabaseConfig.kt
const val SUPABASE_URL = SupabaseConfig.SUPABASE_URL
const val SUPABASE_ANON_KEY = SupabaseConfig.SUPABASE_ANON_KEY
```
**Benefits:**
- ✅ Sesuai dengan azure-chinchilla-swoop
- ✅ Data consistency
- ✅ Cross-table validation
- ✅ Simplified architecture

### **Option 2: Separate Database**
```kotlin
// BarcodeSupabaseConfig.kt
const val SUPABASE_URL = "https://your-barcode-supabase-project.supabase.co"
const val SUPABASE_ANON_KEY = "your-barcode-supabase-anon-key"
```
**Benefits:**
- ✅ Data isolation
- ✅ Independent scaling
- ✅ Separate security policies

## 🚀 Setup Instructions

### **1. Current Setup (Shared Database)**
```sql
-- Run setup_barcode_database.sql di database yang sama
-- Database: ngsuhouodaejwkqdxebk.supabase.co
-- Tables: barcode_scan, barcode_session, barcode_performance
```

### **2. Verification**
```kotlin
// Check configuration
Log.d("Config", "RFID Database: ${SupabaseConfig.SUPABASE_URL}")
Log.d("Config", "Barcode Database: ${BarcodeSupabaseConfig.SUPABASE_URL}")
Log.d("Config", "Shared Database: ${BarcodeSupabaseConfig.isSeparateDatabase()}")
```

## 📊 Data Integration

### **Barcode Validation Flow**
```kotlin
// ExpedisiValidator menggunakan shared database
class ExpedisiValidator(private val supabaseService: SupabaseService) {
    
    suspend fun validateBarcode(barcode: String): ValidationResult {
        // 1. Check in tbl_resi (shared database)
        val resiDetails = checkResiInDatabase(barcode)
        
        // 2. Check in tbl_expedisi (shared database)
        val expedisiRecord = checkExpedisiInDatabase(barcode)
        
        // 3. Validate expedition mismatch
        // 4. Determine schedule
    }
}
```

### **Background Sync Flow**
```kotlin
// BackgroundSyncManager menggunakan shared database
class BackgroundSyncManager {
    
    private suspend fun processScanOperation(operation: PendingOperation): Boolean {
        // Save to barcode_scan table (shared database)
        val record = BarcodeSupabaseService.BarcodeScanRecord(...)
        return barcodeSupabaseService.saveBarcodeScan(record)
    }
}
```

## 🔐 Security Considerations

### **Shared Database Security**
- ✅ Single RLS policy management
- ✅ Unified access control
- ✅ Consistent security model
- ✅ Single audit trail

### **Data Isolation**
- ✅ Table-level isolation
- ✅ Separate service classes
- ✅ Independent validation logic
- ✅ Modular architecture

## 📈 Performance Benefits

### **1. Reduced Latency**
- ✅ Single database connection
- ✅ No cross-database queries
- ✅ Optimized validation flow

### **2. Data Consistency**
- ✅ ACID transactions
- ✅ Referential integrity
- ✅ Consistent timestamps

### **3. Simplified Caching**
- ✅ Single cache strategy
- ✅ Unified data model
- ✅ Efficient memory usage

## 🎯 Current Status

### **✅ Implemented**
- ✅ Shared database configuration
- ✅ BarcodeSupabaseService using shared database
- ✅ ExpedisiValidator using shared database
- ✅ Background sync with shared database
- ✅ Cross-table validation

### **🔄 Data Flow**
```
1. Barcode Scanner → BarcodeSupabaseService → Shared Database (barcode_scan)
2. Validation → ExpedisiValidator → Shared Database (tbl_resi, tbl_expedisi)
3. Background Sync → Shared Database (all tables)
4. Reporting → Shared Database (cross-table queries)
```

## 📝 Migration Notes

### **From Separate to Shared**
1. ✅ **Configuration Updated**: BarcodeSupabaseConfig now uses shared database
2. ✅ **Service Updated**: BarcodeSupabaseService uses shared configuration
3. ✅ **Validation Updated**: ExpedisiValidator uses shared database
4. ✅ **Sync Updated**: BackgroundSyncManager uses shared database

### **Database Schema**
```sql
-- Existing tables (from azure-chinchilla-swoop)
tbl_resi
tbl_expedisi

-- Existing tables (from cekpicklist)
picklist
picklist_scan

-- New tables (for barcode scanner)
barcode_scan
barcode_session
barcode_performance
```

---

**✅ Konfigurasi Supabase untuk barcode scanner sekarang sesuai dengan aplikasi azure-chinchilla-swoop yang sudah berjalan baik!**

