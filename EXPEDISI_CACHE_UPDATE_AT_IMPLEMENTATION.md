# Implementasi Cache Invalidation dengan Kolom `update_at` untuk `tbl_expedisi`

## 📋 Overview

Dokumentasi ini menjelaskan implementasi mekanisme cache invalidation untuk `tbl_expedisi` menggunakan kolom `update_at` dari Supabase. Dengan implementasi ini, cache akan otomatis diperbarui ketika ada data yang diupdate di Supabase.

## 🎯 Tujuan

Mengatasi masalah cache `tbl_expedisi` yang tidak diperbarui ketika data di Supabase diupdate, dengan memanfaatkan kolom `update_at` yang sudah ditambahkan di Supabase.

## 🔧 Perubahan yang Dilakukan

### 1. **Update Data Model: `BarcodeSessionRecord`**

**File**: `app/src/main/java/com/example/cekpicklist/api/BarcodeSupabaseService.kt`

**Perubahan**:
- Menambahkan field `update_at: String?` di data class `BarcodeSessionRecord`

```kotlin
data class BarcodeSessionRecord(
    val id: String? = null,
    val orderno: String,
    val datetrans: String,
    val chanelsales: String? = null,
    val couriername: String? = null,
    val resino: String,
    val created: String? = null,
    val flag: String = "NO",
    val cekfu: Boolean = false,
    val update_at: String? = null // ✨ BARU: Update timestamp untuk cache invalidation
)
```

### 2. **Update Query Supabase untuk Mengambil `update_at`**

**File**: `app/src/main/java/com/example/cekpicklist/api/BarcodeSupabaseService.kt`

**Perubahan**:
- Update fungsi `getAllBarcodeExpedisi()` untuk mengambil kolom `update_at`
- Update fungsi `checkExpedisiByResinoDirect()` untuk mengambil kolom `update_at`

```kotlin
// Sebelumnya
val selectCols = "orderno,datetrans,chanelsales,couriername,resino,created,flag,cekfu"

// Sekarang
val selectCols = "orderno,datetrans,chanelsales,couriername,resino,created,flag,cekfu,update_at"
```

**Parsing JSON**:
```kotlin
val record = BarcodeSessionRecord(
    // ... field lainnya ...
    update_at = jsonObject.optString("update_at")?.takeIf { it.isNotEmpty() }
)
```

### 3. **Fungsi Baru: `getLatestExpedisiUpdateAt()`**

**File**: `app/src/main/java/com/example/cekpicklist/api/BarcodeSupabaseService.kt`

**Fungsi baru** untuk mendapatkan timestamp `update_at` terbaru dari Supabase:

```kotlin
suspend fun getLatestExpedisiUpdateAt(): String? = withContext(Dispatchers.IO) {
    // Query: SELECT update_at FROM tbl_expedisi 
    //        WHERE flag='NO' 
    //        ORDER BY update_at DESC 
    //        LIMIT 1
    
    val url = URL("${BarcodeSupabaseConfig.SUPABASE_URL}/rest/v1/tbl_expedisi?select=update_at&flag=eq.NO&order=update_at.desc&limit=1")
    // ... implementasi HTTP request ...
}
```

**Karakteristik**:
- Timeout cepat (10 detik) untuk performa
- Hanya mengambil 1 record dengan `update_at` terbesar
- Hanya untuk data dengan `flag='NO'`

### 4. **Update Cache Manager: Track `update_at` Terbaru**

**File**: `app/src/main/java/com/example/cekpicklist/cache/BarcodeCacheManager.kt`

#### a. Tambah Variable dan Key untuk Storage

```kotlin
// Variable untuk menyimpan timestamp update_at terbaru
private var lastExpedisiUpdateAt: String? = null

// Key untuk SharedPreferences
private const val KEY_LAST_EXPEDISI_UPDATE_AT = "last_expedisi_update_at"
```

#### b. Update `initializeCache()` dan `refreshCache()`

Menambahkan logika untuk track timestamp `update_at` terbaru:

```kotlin
// Populate expedisi cache - HANYA data dengan flag = "NO"
var latestUpdateAt: String? = null
expedisiRecords.forEach { record ->
    // ... validasi dan cache data ...
    
    // Track timestamp update_at terbaru
    if (record.update_at != null) {
        if (latestUpdateAt == null || record.update_at > latestUpdateAt) {
            latestUpdateAt = record.update_at
        }
    }
}

// Simpan timestamp terbaru
lastExpedisiUpdateAt = latestUpdateAt
```

#### c. Tambah Getter dan Setter

```kotlin
/**
 * Get last update_at timestamp dari tbl_expedisi (untuk cache invalidation)
 */
fun getLastExpedisiUpdateAt(): String? {
    return lastExpedisiUpdateAt
}

/**
 * Set last update_at timestamp dari tbl_expedisi (untuk cache invalidation)
 */
suspend fun setLastExpedisiUpdateAt(updateAt: String?) {
    cacheMutex.withLock {
        lastExpedisiUpdateAt = updateAt
    }
    withContext(Dispatchers.IO) {
        saveCacheToStorage()
    }
}
```

#### d. Update Persistent Storage

**Load dari SharedPreferences**:
```kotlin
lastExpedisiUpdateAt = sharedPreferences!!.getString(KEY_LAST_EXPEDISI_UPDATE_AT, null)
```

**Save ke SharedPreferences**:
```kotlin
sharedPreferences!!.edit()
    .putString(KEY_LAST_EXPEDISI_UPDATE_AT, lastExpedisiUpdateAt)
    .apply()
```

### 5. **Update Repository: Validasi Cache dengan `update_at`**

**File**: `app/src/main/java/com/example/cekpicklist/repository/EnhancedRepository.kt`

**Fungsi**: `getBarcodeExpedisiRecords()`

**Logika Baru**:

```kotlin
suspend fun getBarcodeExpedisiRecords(): List<BarcodeSupabaseService.BarcodeSessionRecord> {
    // 1. Check cache first
    val cachedRecords = BarcodeCacheManager.getAllExpedisiRecords()
    
    if (cachedRecords.isNotEmpty() && BarcodeCacheManager.isExpedisiCacheValid()) {
        // 2. **VALIDASI UPDATE_AT**: Cek timestamp di Supabase
        val cachedUpdateAt = BarcodeCacheManager.getLastExpedisiUpdateAt()
        val latestUpdateAt = barcodeSupabaseService.getLatestExpedisiUpdateAt()
        
        // 3. Bandingkan timestamp
        if (latestUpdateAt != null && latestUpdateAt != cachedUpdateAt) {
            // Cache invalidated - fetch from Supabase
            Log.d(TAG, "🔄 Cache invalidated: update_at changed")
        } else {
            // Cache valid - gunakan cache
            Log.d(TAG, "📦 Using cached data")
            return cachedRecords
        }
    }
    
    // 4. Fetch from Supabase dan update cache
    val remoteRecords = barcodeSupabaseService.getAllBarcodeExpedisi()
    BarcodeCacheManager.refreshCache(emptyList(), remoteRecords)
    return remoteRecords
}
```

## 🔄 Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  User Request: getBarcodeExpedisiRecords()                      │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │ Check Cache  │
                    └──────┬───────┘
                           │
                ┌──────────┴──────────┐
                │                     │
                ▼                     ▼
         ┌─────────────┐       ┌────────────┐
         │ Cache Empty │       │ Cache Exists│
         └──────┬──────┘       └──────┬─────┘
                │                     │
                │                     ▼
                │              ┌─────────────────────┐
                │              │ Get cached update_at│
                │              └──────┬──────────────┘
                │                     │
                │                     ▼
                │              ┌──────────────────────────┐
                │              │ Query Supabase:          │
                │              │ getLatestExpedisiUpdateAt│
                │              └──────┬───────────────────┘
                │                     │
                │              ┌──────┴──────┐
                │              │             │
                │              ▼             ▼
                │       ┌────────────┐  ┌─────────────┐
                │       │ Same       │  │ Different   │
                │       │ Timestamp  │  │ Timestamp   │
                │       └──────┬─────┘  └──────┬──────┘
                │              │                │
                │              ▼                │
                │       ┌──────────────┐       │
                │       │ Use Cache ✓  │       │
                │       └──────────────┘       │
                │                              │
                └──────────────┬───────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Fetch from Supabase │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Update Cache with   │
                    │ new update_at       │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Return Data         │
                    └─────────────────────┘
```

## 📊 Keuntungan Implementasi

### 1. **Real-time Cache Invalidation**
- Cache otomatis diperbarui saat ada perubahan di Supabase
- Tidak perlu manual refresh atau clear cache

### 2. **Performa Optimal**
- Tetap menggunakan cache untuk data yang tidak berubah
- Query `getLatestExpedisiUpdateAt()` sangat cepat (hanya 1 record)
- Timeout 10 detik untuk responsiveness

### 3. **Consistency**
- Data di aplikasi selalu sinkron dengan Supabase
- Menghindari data stale di cache

### 4. **Fallback Mechanism**
- Jika gagal cek `update_at`, tetap gunakan cache
- Error handling yang robust

## 🔍 Testing Scenario

### Scenario 1: Cache Fresh (No Update)
```
1. User membuka aplikasi
2. Cache check: cachedUpdateAt = "2025-01-15T10:00:00Z"
3. Supabase check: latestUpdateAt = "2025-01-15T10:00:00Z"
4. Result: ✅ Gunakan cache (FAST)
```

### Scenario 2: Data Updated di Supabase
```
1. User membuka aplikasi
2. Cache check: cachedUpdateAt = "2025-01-15T10:00:00Z"
3. Supabase check: latestUpdateAt = "2025-01-15T15:30:00Z"
4. Result: 🔄 Invalidate cache, fetch from Supabase
5. Cache updated dengan lastExpedisiUpdateAt = "2025-01-15T15:30:00Z"
```

### Scenario 3: Network Error
```
1. User membuka aplikasi (offline/network issue)
2. Cache check: cachedUpdateAt = "2025-01-15T10:00:00Z"
3. Supabase check: ❌ Error (timeout/no connection)
4. Result: ⚠️ Fallback to cache (graceful degradation)
```

## 🎯 Persyaratan Supabase

### Kolom `update_at` di `tbl_expedisi`

Pastikan kolom `update_at` sudah ada dan configured dengan:

```sql
-- Kolom update_at dengan auto-update trigger
ALTER TABLE tbl_expedisi 
ADD COLUMN IF NOT EXISTS update_at TIMESTAMP WITH TIME ZONE 
DEFAULT NOW();

-- Trigger untuk auto-update
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_tbl_expedisi_modtime
BEFORE UPDATE ON tbl_expedisi
FOR EACH ROW
EXECUTE FUNCTION update_modified_column();
```

## 📝 Catatan Penting

1. **Kolom `update_at` Harus Ada**: Pastikan kolom sudah ada di Supabase sebelum deploy
2. **Trigger Auto-Update**: Trigger otomatis update `update_at` saat ada perubahan data
3. **Backward Compatible**: Jika `update_at` null, cache tetap bekerja normal
4. **Performance**: Query `getLatestExpedisiUpdateAt()` sangat ringan (1 record only)

## 🚀 Deployment Checklist

- [x] Update data model dengan field `update_at`
- [x] Update query Supabase untuk include `update_at`
- [x] Implementasi `getLatestExpedisiUpdateAt()`
- [x] Update BarcodeCacheManager untuk track `update_at`
- [x] Update EnhancedRepository dengan validasi logic
- [x] Test linting: No errors
- [ ] Pastikan kolom `update_at` ada di Supabase
- [ ] Pastikan trigger auto-update aktif di Supabase
- [ ] Test end-to-end dengan data real

## 🎉 Hasil

Dengan implementasi ini, cache `tbl_expedisi` akan **otomatis diperbarui** ketika ada data yang diupdate di Supabase, memastikan data di aplikasi selalu fresh dan konsisten! 🚀


