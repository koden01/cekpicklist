# 📋 **DOKUMENTASI CACHE MANAGER - Sinkronisasi Data Baru dan Penghapusan**

## 🎯 **OVERVIEW**

`BarcodeCacheManager` menangani sinkronisasi data dengan Supabase melalui dua mekanisme:
1. **Direct Operations** - Operasi langsung dari aplikasi (optimistic updates)
2. **Background Sync** - Sinkronisasi otomatis setiap 60 detik untuk mendeteksi perubahan di Supabase

---

## ✅ **MENANGANI DATA BARU**

### **1. Via Direct Operations (Optimistic Update)**

#### **`addResiRecord(record)`**
```kotlin
// Lokasi: BarcodeCacheManager.kt line 255-272
suspend fun addResiRecord(record: BarcodeScanRecord) {
    cacheMutex.withLock {
        // ✅ VALIDASI: Hanya tambahkan jika dalam window 4 hari terakhir
        if (!isWithinLast4Days(record.created)) {
            Log.d(TAG, "⚠️ Skipping resi outside 4-day window")
            return@withLock
        }
        
        // ✅ TAMBAHKAN KE CACHE
        val normalizedResi = record.Resi.trim().uppercase()
        processedResiSet.add(normalizedResi)
        resiRecordsCache[normalizedResi] = record
        lastResiCacheUpdate = System.currentTimeMillis()
        
        // ✅ AUTO-SAVE ke persistent storage
        saveCacheToStorage()
    }
}
```

**Dipanggil oleh:**
- `EnhancedRepository.saveBarcodeResiRecord()` - Saat user scan barcode baru
- `EnhancedRepository.detectAndRemoveDeletedResi()` - Background sync (data baru dari Supabase)

**Validasi:**
- ✅ Cek `created` dalam window 4 hari terakhir
- ✅ Normalisasi Resi (trim + uppercase)
- ✅ Thread-safe dengan `cacheMutex`

---

#### **`addExpedisiRecord(record)`**
```kotlin
// Lokasi: BarcodeCacheManager.kt line 301-326
suspend fun addExpedisiRecord(record: BarcodeSessionRecord) {
    cacheMutex.withLock {
        // ✅ VALIDASI: Hanya cache data dengan flag = "NO"
        if (record.flag.uppercase() != "NO") {
            Log.d(TAG, "⚠️ Skipping expedisi record with flag != 'NO'")
            return@withLock
        }
        
        // ✅ TAMBAHKAN KE CACHE
        val normalizedResino = record.resino.trim().uppercase()
        processedResinoSet.add(normalizedResino)
        expedisiRecordsCache[normalizedResino] = record
        
        // ✅ TAMBAHKAN KE ORDERNO CACHE (jika ada)
        if (orderno.isNotEmpty()) {
            expedisiByOrdernoCache[orderno] = record
        }
        
        lastExpedisiCacheUpdate = System.currentTimeMillis()
        
        // ✅ AUTO-SAVE ke persistent storage
        saveCacheToStorage()
    }
}
```

**Dipanggil oleh:**
- `EnhancedRepository.saveBarcodeExpedisiRecord()` - Saat ada session baru
- `EnhancedRepository.detectAndRemoveDeletedExpedisi()` - Background sync (data baru dari Supabase)

**Validasi:**
- ✅ Cek `flag == "NO"` (hanya cache data yang belum diproses)
- ✅ Normalisasi resino (trim + uppercase)
- ✅ Thread-safe dengan `cacheMutex`

---

### **2. Via Background Sync (Setiap 60 Detik)**

#### **`EnhancedRepository.detectAndRemoveDeletedResi()`**

```kotlin
// Lokasi: EnhancedRepository.kt line 658-714
suspend fun detectAndRemoveDeletedResi() {
    // 1. Ambil cache dan Supabase
    val cachedResi = BarcodeCacheManager.getAllResiRecords()
    val supabaseResi = barcodeSupabaseService.getAllBarcodeResi()
    
    // 2. Deteksi data baru: ada di Supabase tapi tidak ada di cache
    val newResi = supabaseResiSet - cachedResiSet
    
    // 3. Tambahkan data baru ke cache
    if (newResi.isNotEmpty()) {
        val newResiRecords = supabaseResi.filter { 
            newResi.contains(it.Resi.trim().uppercase()) 
        }
        
        newResiRecords.forEach { record ->
            // ✅ Filter hanya data dalam window 4 hari
            if (isWithinLast4Days(record.created)) {
                BarcodeCacheManager.addResiRecord(record)  // ✅ Panggil addResiRecord
            }
        }
    }
}
```

**Kapan dijalankan:**
- Setiap 60 detik di `BackgroundSyncManager.performSync()`

**Proses:**
1. ✅ Ambil semua data dari cache (4 hari terakhir)
2. ✅ Ambil semua data dari Supabase (4 hari terakhir)
3. ✅ Bandingkan untuk menemukan data baru
4. ✅ Filter hanya data dalam window 4 hari
5. ✅ Panggil `addResiRecord()` untuk setiap data baru

---

## ❌ **MENANGANI DATA YANG DIHAPUS**

### **1. Via Direct Operations (Optimistic Delete)**

#### **`removeResiRecord(resi)`**
```kotlin
// Lokasi: BarcodeCacheManager.kt line 360-379
suspend fun removeResiRecord(resi: String) {
    cacheMutex.withLock {
        val normalizedResi = resi.trim().uppercase()
        
        // ✅ HAPUS DARI SEMUA CACHE MAPS
        resiRecordsCache.remove(normalizedResi)
        processedResiSet.remove(normalizedResi)
        
        Log.d(TAG, "🗑️ Removed resi record from cache")
        
        // ✅ AUTO-SAVE ke persistent storage
        saveCacheToStorage()
    }
}
```

**Dipanggil oleh:**
- `EnhancedRepository.deleteBarcodeResiRecord()` - Saat user swipe-to-delete
- `EnhancedRepository.detectAndRemoveDeletedResi()` - Background sync (data dihapus di Supabase)

---

#### **`removeExpedisiRecord(resino)`**
```kotlin
// Lokasi: BarcodeCacheManager.kt line 331-355
suspend fun removeExpedisiRecord(resino: String) {
    cacheMutex.withLock {
        val normalizedResino = resino.trim().uppercase()
        
        // ✅ HAPUS DARI SEMUA CACHE MAPS
        val record = expedisiRecordsCache.remove(normalizedResino)
        processedResinoSet.remove(normalizedResino)
        
        // ✅ HAPUS DARI ORDERNO CACHE (jika ada)
        record?.orderno?.let { orderno ->
            expedisiByOrdernoCache.remove(orderno.trim())
        }
        
        Log.d(TAG, "🗑️ Removed expedisi record from cache")
        
        // ✅ AUTO-SAVE ke persistent storage
        saveCacheToStorage()
    }
}
```

**Dipanggil oleh:**
- `EnhancedRepository.updateExpedisiFlag()` - Saat flag berubah ke "YES"
- `EnhancedRepository.detectAndRemoveDeletedExpedisi()` - Background sync (data dihapus di Supabase)

---

### **2. Via Background Sync (Setiap 60 Detik)**

#### **`EnhancedRepository.detectAndRemoveDeletedResi()`**

```kotlin
// Lokasi: EnhancedRepository.kt line 658-714
suspend fun detectAndRemoveDeletedResi() {
    // 1. Ambil cache dan Supabase
    val cachedResi = BarcodeCacheManager.getAllResiRecords()
    val supabaseResi = barcodeSupabaseService.getAllBarcodeResi()
    
    // 2. Deteksi data yang dihapus: ada di cache tapi tidak ada di Supabase
    val deletedResi = cachedResiSet - supabaseResiSet
    
    // 3. Hapus dari cache
    if (deletedResi.isNotEmpty()) {
        deletedResi.forEach { resi ->
            BarcodeCacheManager.removeResiRecord(resi)  // ✅ Panggil removeResiRecord
        }
    }
}
```

**Kapan dijalankan:**
- Setiap 60 detik di `BackgroundSyncManager.performSync()`

**Proses:**
1. ✅ Ambil semua data dari cache (4 hari terakhir)
2. ✅ Ambil semua data dari Supabase (4 hari terakhir)
3. ✅ Bandingkan untuk menemukan data yang dihapus
4. ✅ Panggil `removeResiRecord()` untuk setiap data yang dihapus

---

## 🔄 **ALUR SINKRONISASI LENGKAP**

### **Skenario 1: Data Baru Ditambahkan di Supabase (oleh User Lain)**

```
1. User lain menambahkan data baru di Supabase
   ↓
2. BackgroundSyncManager.performSync() (setiap 60 detik)
   ↓
3. EnhancedRepository.detectAndRemoveDeletedResi()
   ↓
4. Bandingkan cache vs Supabase
   ↓
5. Deteksi: newResi = supabaseResiSet - cachedResiSet
   ↓
6. Untuk setiap data baru:
   - Filter: isWithinLast4Days(record.created) ✅
   - BarcodeCacheManager.addResiRecord(record) ✅
   - Auto-save ke SharedPreferences ✅
```

### **Skenario 2: Data Dihapus di Supabase (oleh User Lain)**

```
1. User lain menghapus data di Supabase
   ↓
2. BackgroundSyncManager.performSync() (setiap 60 detik)
   ↓
3. EnhancedRepository.detectAndRemoveDeletedResi()
   ↓
4. Bandingkan cache vs Supabase
   ↓
5. Deteksi: deletedResi = cachedResiSet - supabaseResiSet
   ↓
6. Untuk setiap data yang dihapus:
   - BarcodeCacheManager.removeResiRecord(resi) ✅
   - Auto-save ke SharedPreferences ✅
```

### **Skenario 3: Data Baru Ditambahkan dari Aplikasi Ini**

```
1. User scan barcode baru
   ↓
2. EnhancedRepository.saveBarcodeResiRecord()
   ↓
3. Optimistic Update: BarcodeCacheManager.addResiRecord() ✅
   (Cache update INSTAN - sebelum Supabase POST)
   ↓
4. POST ke Supabase
   ↓
5. Auto-save ke SharedPreferences ✅
```

### **Skenario 4: Data Dihapus dari Aplikasi Ini**

```
1. User swipe-to-delete
   ↓
2. EnhancedRepository.deleteBarcodeResiRecord()
   ↓
3. Optimistic Delete: BarcodeCacheManager.removeResiRecord() ✅
   (Cache update INSTAN - sebelum Supabase DELETE)
   ↓
4. DELETE ke Supabase
   ↓
5. Auto-save ke SharedPreferences ✅
```

---

## 🔒 **THREAD SAFETY**

Semua operasi cache menggunakan **`cacheMutex`** untuk memastikan thread-safe:

```kotlin
suspend fun addResiRecord(record: BarcodeScanRecord) {
    cacheMutex.withLock {  // ✅ Thread-safe lock
        // Operasi cache
    }
}
```

**Keuntungan:**
- ✅ Tidak ada race condition
- ✅ Konsistensi data terjamin
- ✅ Safe untuk concurrent access

---

## 💾 **PERSISTENT STORAGE**

Setiap perubahan cache otomatis disimpan ke SharedPreferences:

### **Auto-Save Mechanism:**
```kotlin
// Setelah setiap operasi add/remove
kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
    saveCacheToStorage()  // ✅ Auto-save
}
```

**Data yang disimpan:**
- ✅ Resi records (Map<String, BarcodeScanRecord>)
- ✅ Expedisi records (Map<String, BarcodeSessionRecord>)
- ✅ Courier names (List<String>)
- ✅ Timestamps (lastResiCacheUpdate, lastExpedisiCacheUpdate, dll)

**Auto-Load:**
- ✅ Cache otomatis di-load saat `BarcodeCacheManager.init(context)` dipanggil
- ✅ Filter expiration: Hanya load jika cache belum expired (< 15 jam)
- ✅ Filter window: Hanya load data dalam 4 hari terakhir (untuk resi)
- ✅ Filter flag: Hanya load data dengan flag="NO" (untuk expedisi)

---

## 📊 **RINGKASAN MEKANISME**

| Operasi | Method | Validasi | Auto-Save | Dipanggil Oleh |
|---------|--------|----------|-----------|----------------|
| **Add Resi** | `addResiRecord()` | Window 4 hari ✅ | ✅ Ya | Direct ops + Background sync |
| **Add Expedisi** | `addExpedisiRecord()` | Flag="NO" ✅ | ✅ Ya | Direct ops + Background sync |
| **Remove Resi** | `removeResiRecord()` | - | ✅ Ya | Direct ops + Background sync |
| **Remove Expedisi** | `removeExpedisiRecord()` | - | ✅ Ya | Direct ops + Background sync |

---

## ⚙️ **KONFIGURASI**

- **Cache Expiration**: 15 jam
- **Sync Interval**: 60 detik
- **Data Window**: 4 hari terakhir (untuk resi)
- **Flag Filter**: Hanya flag="NO" (untuk expedisi)
- **Storage**: SharedPreferences (persistent)
- **Thread Safety**: Mutex-based locking

---

## ✅ **KESIMPULAN**

`BarcodeCacheManager` menangani data baru dan penghapusan dengan:

1. ✅ **Direct Operations**: Optimistic updates untuk UI yang responsif
2. ✅ **Background Sync**: Deteksi perubahan setiap 60 detik
3. ✅ **Validasi**: Filter window 4 hari dan flag="NO"
4. ✅ **Persistent Storage**: Auto-save/load dengan SharedPreferences
5. ✅ **Thread Safety**: Mutex untuk concurrent access
6. ✅ **Sinkronisasi Dua Arah**: Deteksi penambahan dan penghapusan

Cache selalu sinkron dengan Supabase melalui mekanisme ini!

