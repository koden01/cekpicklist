# 📋 **OVERVIEW: Cara CekPicklist Menangani Data Baru dan Penghapusan**

## 🎯 **DUA SISTEM CACHE TERPISAH**

Aplikasi `cekpicklist` menggunakan **2 sistem cache terpisah** untuk data yang berbeda:

### **1. BarcodeCacheManager** - Untuk Barcode Data
- **Data**: `tbl_resi` dan `tbl_expedisi` (barcode scanner)
- **Storage**: SharedPreferences (persistent, 15 jam expiration)
- **Window**: 4 hari terakhir
- **Sinkronisasi**: Dua arah (setiap 60 detik)

### **2. CacheManager** - Untuk Picklist/RFID Data
- **Data**: Picklist items, RFID scans, processed EPCs
- **Storage**: SharedPreferences (persistent, 15 jam expiration)
- **Sinkronisasi**: Invalidate-on-change + Incremental update

---

## 🔵 **SISTEM 1: BARCODE DATA (BarcodeCacheManager)**

### **✅ MENANGANI DATA BARU**

#### **A. Via Direct Operations (Optimistic Update)**

**1. `addResiRecord(record)`**
```kotlin
// Saat user scan barcode baru dari aplikasi ini
EnhancedRepository.saveBarcodeResiRecord()
  → BarcodeCacheManager.addResiRecord() ✅
  → Validasi: Window 4 hari ✅
  → Update cache INSTAN
  → Auto-save ke SharedPreferences ✅
  → POST ke Supabase (background)
```

**2. `addExpedisiRecord(record)`**
```kotlin
// Saat ada session baru
EnhancedRepository.saveBarcodeExpedisiRecord()
  → BarcodeCacheManager.addExpedisiRecord() ✅
  → Validasi: flag="NO" ✅
  → Update cache INSTAN
  → Auto-save ke SharedPreferences ✅
```

#### **B. Via Background Sync (Setiap 60 Detik)**

**`detectAndRemoveDeletedResi()` - Sekarang Sinkronisasi Dua Arah**
```kotlin
BackgroundSyncManager.performSync() (setiap 60 detik)
  → EnhancedRepository.detectAndRemoveDeletedResi()
  → Bandingkan: cache vs Supabase
  → Deteksi data baru: newResi = supabaseResiSet - cachedResiSet
  → Untuk setiap data baru:
      - Filter: isWithinLast4Days(record.created) ✅
      - BarcodeCacheManager.addResiRecord(record) ✅
```

**Hasil:**
- ✅ Data baru ditambahkan di Supabase → Otomatis masuk ke cache dalam 60 detik
- ✅ Data baru dari aplikasi ini → Langsung masuk ke cache (optimistic)

---

### **❌ MENANGANI PENGHAPUSAN**

#### **A. Via Direct Operations (Optimistic Delete)**

**1. `removeResiRecord(resi)`**
```kotlin
// Saat user swipe-to-delete
EnhancedRepository.deleteBarcodeResiRecord()
  → BarcodeCacheManager.removeResiRecord() ✅
  → Hapus dari cache INSTAN
  → Auto-save ke SharedPreferences ✅
  → DELETE ke Supabase (background)
```

**2. `removeExpedisiRecord(resino)`**
```kotlin
// Saat flag berubah ke "YES"
EnhancedRepository.updateExpedisiFlag(resino, "YES")
  → BarcodeCacheManager.removeExpedisiRecord() ✅
  → Hapus dari cache INSTAN
  → Auto-save ke SharedPreferences ✅
```

#### **B. Via Background Sync (Setiap 60 Detik)**

**`detectAndRemoveDeletedResi()`**
```kotlin
BackgroundSyncManager.performSync() (setiap 60 detik)
  → EnhancedRepository.detectAndRemoveDeletedResi()
  → Bandingkan: cache vs Supabase
  → Deteksi data yang dihapus: deletedResi = cachedResiSet - supabaseResiSet
  → Untuk setiap data yang dihapus:
      - BarcodeCacheManager.removeResiRecord(resi) ✅
```

**Hasil:**
- ✅ Data dihapus di Supabase → Otomatis dihapus dari cache dalam 60 detik
- ✅ Data dihapus dari aplikasi ini → Langsung dihapus dari cache (optimistic)

---

## 🟢 **SISTEM 2: PICKLIST/RFID DATA (CacheManager)**

### **✅ MENANGANI DATA BARU**

#### **A. Via Direct Operations**

**1. `smartUpdatePicklistItems(picklistNo, newItems)`**
```kotlin
// Saat fetch picklist items dari Supabase
Repository.getPicklistItems(picklistNo)
  → SupabaseService.getPicklistItems()
  → CacheManager.smartUpdatePicklistItems() ✅
  → Jika cache expired/kosong: Simpan fresh data
  → Jika cache valid: updatePicklistItemsIncremental() ✅
```

**2. `updatePicklistItemsIncremental(picklistNo, newItems)`**
```kotlin
// Merge data baru dengan data yang sudah ada
CacheManager.updatePicklistItemsIncremental()
  → mergePicklistItems(existing, new)
  → Update existing items (qtyScan, qtyPl, tagStatus)
  → Tambahkan item baru
  → Simpan ke cache
  → Auto-save ke SharedPreferences ✅
```

**3. `savePicklistScan()` - Incremental EPC Update**
```kotlin
// Saat user scan RFID baru
Repository.savePicklistScan(picklistNo, articleId, epc, ...)
  → SupabaseService.savePicklistScan() ✅
  → Update processed EPC cache INCREMENTAL:
      cachedEpcList = cachedEpcList + epc ✅
  → CacheManager.setProcessedEpcList(picklistNo, updatedEpcList) ✅
  → CacheManager.invalidatePicklist(picklistNo) ✅
      (Cache di-invalidate agar fetch fresh data next time)
```

#### **B. Via Background Refresh (On-Demand)**

**Background Incremental Update**
```kotlin
// Saat menggunakan data dari cache
Repository.getPicklistItems(picklistNo)
  → Jika ada di cache:
      - Return cache INSTAN ✅
      - Background refresh untuk update incremental:
          supabaseService.getPicklistItems()
          cacheManager.updatePicklistItemsIncremental() ✅
```

**Hasil:**
- ✅ Data baru ditambahkan di Supabase → Masuk ke cache saat fetch berikutnya (on-demand)
- ✅ Data baru dari aplikasi ini → Langsung update cache (incremental)

---

### **❌ MENANGANI PENGHAPUSAN**

#### **A. Via Invalidation (Cache Invalidate-on-Change)**

**1. `invalidatePicklist(picklistNo)`**
```kotlin
// Saat ada perubahan data (save/delete scan)
Repository.savePicklistScan()
  → CacheManager.invalidatePicklist(picklistNo) ✅
  → Hapus dari cache:
      - picklistItemsCache.remove(picklistNo)
      - picklistStatusCache.remove(picklistNo)
      - processedEpcCache.remove(picklistNo)
  → Auto-save ke SharedPreferences ✅
```

**2. `removeRfidForArticle()` - Remove RFID dari ViewModel**
```kotlin
// Saat user swipe-to-delete RFID
ScanViewModel.removeRfidForArticle(articleName, size)
  → Hapus dari _processedRfidData ✅
  → Hapus dari RfidScanManager (epcIndexMap, epcToProduct) ✅
  → Hapus dari uniqueSet (session duplicate tracking) ✅
  → Hapus dari processedEpcPerPicklist (persistent tracking) ✅
  → POST DELETE ke Supabase ✅
  → Cache di-invalidate untuk fetch fresh data
```

#### **B. Via Background Refresh (On-Demand)**

**Background Refresh akan Fetch Fresh Data**
```kotlin
// Saat data dihapus di Supabase dan user fetch lagi
Repository.getPicklistItems(picklistNo)
  → Cache di-invalidate sebelumnya
  → Fetch fresh dari Supabase ✅
  → Data yang dihapus otomatis tidak ada di hasil ✅
```

**Hasil:**
- ✅ Data dihapus dari aplikasi ini → Langsung dihapus dari cache + invalidate
- ✅ Data dihapus di Supabase → Masuk ke cache saat fetch berikutnya (on-demand)

---

## 📊 **PERBANDINGAN DUA SISTEM**

| Aspek | **BarcodeCacheManager** | **CacheManager** |
|-------|-------------------------|------------------|
| **Data Type** | Barcode (tbl_resi, tbl_expedisi) | Picklist/RFID |
| **Storage** | SharedPreferences | SharedPreferences |
| **Expiration** | 15 jam | 15 jam |
| **Data Baru** | ✅ Background sync (60s) + Direct ops | ✅ On-demand fetch + Incremental update |
| **Penghapusan** | ✅ Background sync (60s) + Direct ops | ✅ Invalidate-on-change |
| **Sinkronisasi** | ✅ Dua arah otomatis (60s) | ⚠️ On-demand (saat fetch) |
| **Window Filter** | ✅ 4 hari terakhir | ❌ Tidak ada |

---

## 🔄 **ALUR SINKRONISASI LENGKAP**

### **Skenario 1: Data Baru di Supabase (Barcode)**

```
User lain menambahkan data baru di Supabase
  ↓ (Tunggu max 60 detik)
BackgroundSyncManager.performSync()
  ↓
EnhancedRepository.detectAndRemoveDeletedResi()
  ↓
Bandingkan: cache vs Supabase
  ↓
Deteksi: newResi = supabaseResiSet - cachedResiSet
  ↓
BarcodeCacheManager.addResiRecord() untuk setiap data baru
  ↓
Auto-save ke SharedPreferences ✅
```

### **Skenario 2: Data Dihapus di Supabase (Barcode)**

```
User lain menghapus data di Supabase
  ↓ (Tunggu max 60 detik)
BackgroundSyncManager.performSync()
  ↓
EnhancedRepository.detectAndRemoveDeletedResi()
  ↓
Bandingkan: cache vs Supabase
  ↓
Deteksi: deletedResi = cachedResiSet - supabaseResiSet
  ↓
BarcodeCacheManager.removeResiRecord() untuk setiap data yang dihapus
  ↓
Auto-save ke SharedPreferences ✅
```

### **Skenario 3: Data Baru di Supabase (Picklist/RFID)**

```
User lain menambahkan scan RFID baru di Supabase
  ↓
User di aplikasi ini fetch picklist items
  ↓
Repository.getPicklistItems(picklistNo)
  ↓
Jika cache ada: Return cache INSTAN
  → Background refresh: supabaseService.getPicklistItems()
  → CacheManager.updatePicklistItemsIncremental() ✅
  → Merge data baru dengan existing ✅
```

### **Skenario 4: Data Dihapus di Supabase (Picklist/RFID)**

```
User lain menghapus scan RFID di Supabase
  ↓
User di aplikasi ini fetch picklist items
  ↓
Repository.getPicklistItems(picklistNo)
  ↓
Jika cache di-invalidate: Fetch fresh dari Supabase
  → Data yang dihapus otomatis tidak ada di hasil ✅
```

---

## 🎯 **KESIMPULAN**

### **Barcode Data (BarcodeCacheManager):**
- ✅ **Sinkronisasi Aktif**: Dua arah setiap 60 detik
- ✅ **Data Baru**: Otomatis masuk ke cache (max 60 detik delay)
- ✅ **Penghapusan**: Otomatis dihapus dari cache (max 60 detik delay)

### **Picklist/RFID Data (CacheManager):**
- ⚠️ **Sinkronisasi Pasif**: On-demand saat fetch
- ✅ **Data Baru**: Masuk ke cache saat fetch berikutnya (incremental merge)
- ✅ **Penghapusan**: Cache di-invalidate, data yang dihapus tidak muncul saat fetch berikutnya

### **Keseluruhan:**
- ✅ Kedua sistem menggunakan persistent storage (15 jam expiration)
- ✅ Auto-save ke SharedPreferences setelah setiap perubahan
- ✅ Thread-safe dengan Mutex
- ✅ Optimistic updates untuk UI yang responsif

