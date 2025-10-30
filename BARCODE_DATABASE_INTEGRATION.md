# 🗄️ Barcode Scanner Database Integration

## 📋 Overview

Aplikasi barcode scanner sekarang menggunakan struktur database yang **konsisten** dengan aplikasi `azure-chinchilla-swoop`, menggunakan kolom yang sama seperti `tbl_resi` dan `tbl_expedisi`.

## 🏗️ Database Structure

### Tabel `barcode_scan`
Menggunakan kolom yang konsisten dengan `tbl_resi` dan `tbl_expedisi`:

```sql
CREATE TABLE barcode_scan (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    -- Kolom konsisten dengan tbl_resi
    Resi TEXT NOT NULL,                    -- Barcode value
    orderno TEXT,                          -- Order number
    chanelsales TEXT,                      -- Channel sales
    nokarung TEXT,                         -- Karung number
    schedule TEXT DEFAULT 'ontime',        -- Schedule status: 'ontime', 'late', 'batal'
    Keterangan TEXT,                       -- Description/Scanner type
    created TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Kolom konsisten dengan tbl_expedisi
    resino TEXT,                           -- Alternative barcode identifier
    couriername TEXT,                      -- Courier/Scanner name
    datetrans TIMESTAMP WITH TIME ZONE,    -- Transaction date
    flag TEXT DEFAULT 'NO',                -- Flag status
    cekfu BOOLEAN DEFAULT false,           -- Check follow up
    
    -- Additional barcode-specific fields
    scanner_type TEXT NOT NULL DEFAULT 'built-in',
    scan_duration INTEGER DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'success',
    device_info TEXT,
    location_info TEXT
);
```

### Tabel `barcode_session`
Menggunakan kolom yang konsisten dengan `tbl_expedisi`:

```sql
CREATE TABLE barcode_session (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    -- Kolom konsisten dengan tbl_expedisi
    resino TEXT,                           -- Session identifier
    orderno TEXT,                          -- Order number
    chanelsales TEXT,                      -- Channel sales
    couriername TEXT,                      -- Courier/Scanner name
    created TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    datetrans TIMESTAMP WITH TIME ZONE,
    flag TEXT DEFAULT 'NO',
    cekfu BOOLEAN DEFAULT false,
    
    -- Additional session-specific fields
    session_name TEXT,
    scanner_type TEXT NOT NULL DEFAULT 'built-in',
    total_scans INTEGER DEFAULT 0,
    successful_scans INTEGER DEFAULT 0,
    failed_scans INTEGER DEFAULT 0,
    duplicate_scans INTEGER DEFAULT 0,
    avg_scan_time DECIMAL(10,2) DEFAULT 0.0,
    start_time TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    end_time TIMESTAMP WITH TIME ZONE,
    device_info TEXT
);
```

## 🔄 Data Mapping

### Barcode Scan Record
```kotlin
data class BarcodeScanRecord(
    val Resi: String,              // Barcode value (seperti tbl_resi)
    val resino: String? = null,    // Alternative barcode identifier (seperti tbl_expedisi)
    val orderno: String? = null,   // Order number (seperti tbl_resi)
    val chanelsales: String? = null, // Channel sales (seperti tbl_resi)
    val couriername: String? = null, // Courier/Scanner name (seperti tbl_expedisi)
    val flag: String = "NO",       // Flag status (seperti tbl_expedisi)
    val cekfu: Boolean = false,    // Check follow up (seperti tbl_expedisi)
    val nokarung: String? = null,  // Karung number (seperti tbl_resi)
    val schedule: String = "ontime", // Schedule status (seperti tbl_resi)
    val Keterangan: String? = null, // Description/Scanner type (seperti tbl_resi)
    // Additional fields
    val scannerType: String,
    val scanDuration: Long,
    val status: String,
    val deviceInfo: String? = null,
    val locationInfo: String? = null
)
```

### Scan Result & History
```kotlin
data class ScanResult(
    val status: String,
    val message: String,
    val Resi: String,              // Barcode value
    val resino: String? = null,    // Set same as Resi for barcode
    val couriername: String? = null, // Use scanner type as courier name
    val flag: String = "YES",      // Mark as processed
    val schedule: String = "ontime", // Default to ontime
    val Keterangan: String? = null, // Use scanner type as description
    val timestamp: Long,
    val scanDuration: Long,
    val scannerType: String
)
```

## 🚀 Implementation

### 1. Database Setup
Jalankan script `setup_barcode_database.sql` di Supabase SQL Editor untuk membuat tabel dan struktur yang diperlukan.

### 2. Service Integration
```kotlin
// BarcodeSupabaseService.kt
class BarcodeSupabaseService {
    suspend fun saveBarcodeScan(record: BarcodeScanRecord): Boolean
    suspend fun saveBarcodeSession(record: BarcodeSessionRecord): String?
    suspend fun updateBarcodeSession(sessionId: String, record: BarcodeSessionRecord): Boolean
    suspend fun getPerformanceMetrics(date: String, scannerType: String? = null): List<BarcodePerformanceRecord>
    suspend fun getBarcodeScanHistory(date: String, limit: Int = 100): List<BarcodeScanRecord>
}
```

### 3. ViewModel Integration
```kotlin
// BarcodeScannerViewModel.kt
class BarcodeScannerViewModel {
    fun processScannedBarcode(barcode: String, scanDuration: Long = 0L, scannerType: String = "built-in")
    fun getScanHistory(): List<ScanHistoryItem>
    fun exportScanHistory(): String
}
```

## 📊 Data Flow

1. **Scan Barcode** → `BarcodeScannerActivity`
2. **Process Data** → `BarcodeScannerViewModel.processScannedBarcode()`
3. **Create Record** → `BarcodeScanRecord` dengan kolom konsisten
4. **Save to Database** → `BarcodeSupabaseService.saveBarcodeScan()`
5. **Update UI** → LiveData updates

## 🔍 Key Features

### ✅ Konsistensi Database
- Menggunakan kolom yang sama dengan `tbl_resi` dan `tbl_expedisi`
- Struktur data yang familiar untuk tim development
- Mudah untuk integrasi dengan sistem existing

### ✅ Data Mapping
- `Resi` → Barcode value
- `resino` → Alternative barcode identifier
- `couriername` → Scanner type (built-in/camera)
- `flag` → Processing status (YES/NO)
- `schedule` → Scan status (ontime/late/batal)
- `Keterangan` → Scanner description

### ✅ Performance Tracking
- `scan_duration` → Waktu scan dalam milliseconds
- `scanner_type` → Jenis scanner (built-in/camera)
- `status` → Status scan (success/error/duplicate)

### ✅ Session Management
- Tracking session scanning
- Performance metrics per session
- Historical data analysis

## 🎯 Benefits

1. **Konsistensi**: Struktur database yang sama dengan aplikasi existing
2. **Integrasi**: Mudah untuk integrasi dengan sistem lain
3. **Maintenance**: Familiar dengan struktur kolom yang sudah ada
4. **Scalability**: Dapat dikembangkan untuk fitur yang lebih kompleks
5. **Reporting**: Mudah untuk membuat laporan dan analisis data

## 📝 Usage Example

```kotlin
// Save barcode scan
val record = BarcodeScanRecord(
    Resi = "1234567890123",
    resino = "1234567890123",
    couriername = "Built-in Scanner",
    flag = "YES",
    schedule = "ontime",
    Keterangan = "Built-in Scanner Test",
    scannerType = "built-in",
    scanDuration = 150L,
    status = "success",
    deviceInfo = "Device: Test Scanner"
)

val success = barcodeSupabaseService.saveBarcodeScan(record)
```

## 🔧 Configuration

Pastikan konfigurasi Supabase sudah benar di `SupabaseConfig`:
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

## 📈 Monitoring

Database menyediakan:
- Performance metrics per hari
- Success rate tracking
- Scanner type comparison
- Historical data analysis
- Automatic cleanup untuk data lama

---

**✅ Database barcode scanner sekarang menggunakan struktur yang konsisten dengan aplikasi `azure-chinchilla-swoop`!**

