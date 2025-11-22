# 🔄 **PENJELASAN ALUR CACHE MANAGER YANG DISEDERHANAKAN**

## 🎯 **ATURAN BARU (SIMPLE & CLEAR)**

### **1. Cache Expedisi (Flag="NO")**

**Aturan:**
- ✅ **Mengambil**: Semua data dengan `flag="NO"` dari `tbl_expedisi`
- ✅ **Selalu Sync**: Cache expedisi selalu sama dengan Supabase (flag="NO")
- ✅ **Update**: Jika flag berubah ke "YES" → hapus dari cache
- ✅ **Auto Add**: Jika flag berubah ke "NO" (oleh trigger) → tambahkan ke cache

**Alur:**
```
1. Fetch semua data flag="NO" dari Supabase
2. Replace cache expedisi dengan data terbaru
3. Pastikan cache selalu sinkron dengan Supabase
```

---

### **2. Cache Resi (7 Hari Terakhir)**

**Aturan:**
- ✅ **Mengambil**: Data 7 hari terakhir dari `tbl_resi`
- ✅ **Cleanup**: Data > 7 hari dihapus, diganti dengan data hari ini saja
- ✅ **Initial Load**: Jika cache kosong → ambil semua data 7 hari
- ✅ **Daily Sync**: Hari berikutnya → ambil data hari ini saja (atau data yang belum ada di cache)

**Alur:**
```
1. Cleanup data > 7 hari dari cache
2. Fetch data hari ini dari Supabase
3. Merge ke cache (upsert - tambah jika baru, update jika sudah ada)
```

---

### **3. Deteksi Perubahan**

**Aturan:**
- ✅ **Tambah/Update**: Deteksi data yang ditambahkan atau diupdate
- ✅ **Hapus**: Jika data dihapus dari `tbl_resi`:
  - Supabase trigger otomatis ubah flag menjadi "NO"
  - Di cache: hapus dari cache resi
  - Sync expedisi untuk dapat flag="NO" yang sudah di-update trigger

**Alur:**
```
1. Compare cache resi vs Supabase (7 hari)
2. Deteksi data yang "missing" (ada di cache tapi tidak di Supabase)
3. Batch verification untuk memastikan benar-benar dihapus
4. Jika benar-benar dihapus:
   - Hapus dari cache resi
   - ❌ TIDAK update flag (trigger sudah handle)
   - Sync expedisi cache untuk dapat flag="NO"
```

---

## 📊 **ALUR LENGKAP (STEP BY STEP)**

### **SCENARIO 1: Initial Load (Cache Kosong)**

**Kondisi:** Aplikasi pertama kali dibuka, cache resi kosong

**Alur:**
1. ✅ **Check Cache**: Cache resi kosong
2. ✅ **Fetch 7 Hari**: Ambil semua data 7 hari terakhir dari `tbl_resi`
3. ✅ **Simpan Cache**: Simpan ke cache resi
4. ✅ **Fetch Expedisi**: Ambil semua data `flag="NO"` dari `tbl_expedisi`
5. ✅ **Simpan Cache**: Simpan ke cache expedisi

**Waktu:** ~1-2 detik (non-blocking, background)

---

### **SCENARIO 2: Daily Sync (Hari Berikutnya)**

**Kondisi:** Cache resi sudah ada (7 hari terakhir), hari baru dimulai

**Alur:**
1. ✅ **Cleanup**: Hapus data > 7 hari dari cache resi
2. ✅ **Fetch Hari Ini**: Ambil data hari ini saja dari `tbl_resi`
3. ✅ **Merge**: Tambahkan/update data hari ini ke cache resi
4. ✅ **Sync Expedisi**: Sync semua data `flag="NO"` dari `tbl_expedisi`

**Waktu:** ~200-500ms (non-blocking, background)

---

### **SCENARIO 3: Background Sync (Setiap 60 Detik)**

**Kondisi:** Aplikasi sedang berjalan, background sync aktif

**Alur Resi:**
1. ✅ **Cleanup**: Hapus data > 7 hari dari cache resi
2. ✅ **Fetch Hari Ini**: Ambil data hari ini dari `tbl_resi`
3. ✅ **Merge**: Upsert data hari ini ke cache resi

**Alur Expedisi:**
1. ✅ **Fetch Flag NO**: Ambil semua data `flag="NO"` dari `tbl_expedisi`
2. ✅ **Replace Cache**: Replace cache expedisi dengan data terbaru
3. ✅ **Sinkron**: Pastikan cache selalu sama dengan Supabase

**Waktu:** ~200-500ms (non-blocking)

---

### **SCENARIO 4: Deteksi Penghapusan**

**Kondisi:** Resi dihapus dari Supabase (oleh user/admin)

**Alur:**
1. ✅ **Trigger Supabase**: Otomatis ubah flag menjadi "NO" di `tbl_expedisi`
2. ✅ **Background Sync**: Detect resi yang "missing" (ada di cache tapi tidak di Supabase)
3. ✅ **Batch Verification**: Cek apakah resi benar-benar tidak ada di Supabase
4. ✅ **Jika Benar-Benar Dihapus**:
   - Hapus dari cache resi
   - ❌ TIDAK update flag (trigger sudah handle)
   - Sync expedisi cache untuk dapat flag="NO" yang sudah di-update trigger

**Waktu:** ~200-500ms (non-blocking)

---

## 🔧 **PERUBAHAN YANG AKAN DILAKUKAN**

### **1. Simplify Initial Load**

**File:** `EnhancedRepository.kt:457-488`

**Sebelum:**
```kotlin
// Complex: Ambil data hari ini saja
val todayDate = getTodayDate()
val remoteRecords = barcodeSupabaseService.getBarcodeResiSince(todayDate)
```

**Sesudah:**
```kotlin
// ✅ SIMPLE: Ambil semua data 7 hari terakhir
val sevenDaysAgo = getSevenDaysAgoDate()
val remoteRecords = barcodeSupabaseService.getBarcodeResiSince(sevenDaysAgo)
BarcodeCacheManager.initializeCache(remoteRecords, emptyList())
```

---

### **2. Simplify Daily Sync**

**File:** `EnhancedRepository.kt:509-549`

**Sebelum:**
```kotlin
// Complex incremental sync dengan timestamp
val lastSyncTime = BarcodeCacheManager.getLastResiSyncTime()
val sinceDate = BarcodeCacheManager.timestampToIsoDate(lastSyncTime)
val deltaRecords = barcodeSupabaseService.getBarcodeResiSince(sinceDate)
```

**Sesudah:**
```kotlin
// ✅ SIMPLE: Cleanup + Fetch hari ini
private suspend fun performDailyResiSync() {
    // 1. Cleanup data > 7 hari
    BarcodeCacheManager.cleanupOldData()
    
    // 2. Fetch data hari ini
    val todayDate = getTodayDate()
    val todayRecords = barcodeSupabaseService.getBarcodeResiSince(todayDate)
    
    // 3. Merge ke cache (upsert)
    todayRecords.forEach { record ->
        BarcodeCacheManager.addResiRecord(record)
    }
}
```

---

### **3. Simplify Expedisi Sync**

**File:** `EnhancedRepository.kt:600-647`

**Sebelum:**
```kotlin
// Complex incremental sync dengan timestamp
val lastSyncTime = BarcodeCacheManager.getLastExpedisiSyncTime()
val sinceDate = BarcodeCacheManager.timestampToIsoDate(lastSyncTime)
val deltaRecords = barcodeSupabaseService.getBarcodeExpedisiSince(sinceDate)
```

**Sesudah:**
```kotlin
// ✅ SIMPLE: Ambil semua data flag="NO" (selalu sync dengan Supabase)
private suspend fun performDailyExpedisiSync() {
    // Fetch semua data flag="NO" dari Supabase
    val flagNoRecords = barcodeSupabaseService.getAllBarcodeExpedisi()
    
    // Replace cache expedisi dengan data terbaru
    val currentResiRecords = BarcodeCacheManager.getAllResiRecords()
    if (currentResiRecords.isEmpty()) {
        BarcodeCacheManager.initializeCache(emptyList(), flagNoRecords)
    } else {
        BarcodeCacheManager.refreshCache(emptyList(), flagNoRecords)
    }
}
```

---

### **4. Simplify Delete Detection**

**File:** `EnhancedRepository.kt:850-1050`

**Sebelum:**
```kotlin
// Complex: Retry, re-fetch, update flag manual
deletedResi.forEach { resi ->
    updateExpedisiFlag(resi, "NO")  // ❌ REDUNDANT
}
```

**Sesudah:**
```kotlin
// ✅ SIMPLE: Hapus dari cache resi, sync expedisi untuk dapat flag="NO"
confirmedDeleted.forEach { resi ->
    // 1. Hapus dari cache resi
    BarcodeCacheManager.removeResiRecord(resi)
    
    // 2. ❌ TIDAK update flag (trigger sudah handle)
    Log.d(TAG, "✅ Removed resi $resi from cache (flag updated by Supabase trigger)")
}

// 3. Sync expedisi untuk dapat flag="NO" yang sudah di-update trigger
performDailyExpedisiSync()
```

---

## 📝 **RINGKASAN ATURAN BARU**

### **Cache Expedisi:**
- ✅ Ambil semua `flag="NO"` dari Supabase
- ✅ Selalu sinkron dengan Supabase
- ✅ Tidak perlu incremental sync (selalu full sync)

### **Cache Resi:**
- ✅ 7 hari terakhir
- ✅ Cleanup data > 7 hari setiap hari
- ✅ Daily sync: ambil data hari ini saja
- ✅ Merge (upsert) ke cache

### **Delete Handling:**
- ✅ Hapus dari cache resi
- ✅ ❌ TIDAK update flag (trigger handle)
- ✅ Sync expedisi untuk dapat flag="NO"

---

## 🎯 **KEUNTUNGAN**

1. ✅ **Lebih Simple**: Alur lebih mudah dipahami
2. ✅ **Lebih Jelas**: Aturan jelas dan konsisten
3. ✅ **Lebih Efisien**: Tidak ada query yang tidak perlu
4. ✅ **Leverage Trigger**: Mengandalkan trigger Supabase

Apakah Anda setuju dengan alur yang disederhanakan ini? Jika ya, saya akan langsung mengimplementasikannya.

