# 📊 **ANALISIS BANDWIDTH: performDailyResiSync**

## 🔍 **IMPLEMENTASI SAAT INI**

### **Alur performDailyResiSync():**

```
1. Cleanup data > 7 hari
2. Query data dari Supabase:
   - Initial: Fetch 7 hari (jika cache kosong)
   - Incremental: Fetch sejak lastSyncTime (jika cache ada)
3. Tambah/update data yang ada di Supabase
4. Hapus data yang tidak ada di Supabase (delete detection)
```

---

## 📊 **ANALISIS BANDWIDTH**

### **SKENARIO 1: Initial Sync (Cache Kosong)**

**Query:**
- Fetch 7 hari terakhir
- Bandwidth: ~1.75 MB (sekali)

**Delete Detection:**
- Compare cache (kosong) vs Supabase (7 hari)
- Tidak ada yang dihapus (cache kosong)
- Bandwidth: 0 KB (tidak ada operasi)

**Total:**
- ✅ **~1.75 MB** (sekali, saat initial load)

---

### **SKENARIO 2: Incremental Sync (Cache Ada)**

**Query:**
- Fetch hanya data baru/modified sejak lastSyncTime
- Contoh: lastSyncTime = 15 menit yang lalu
- Bandwidth: ~10-50 KB (hanya data baru)

**Delete Detection:**
- Compare cache (7 hari) vs Supabase (incremental)
- **MASALAH**: Incremental query hanya fetch data baru, tidak fetch data lama
- Jika resi dihapus sebelum lastSyncTime, tidak akan terdeteksi
- Bandwidth: 0 KB (tapi tidak akurat)

**Total:**
- ✅ **~10-50 KB per sync** (hemat)
- ⚠️ **TIDAK AKURAT** untuk delete detection (hanya detect delete pada data baru)

---

## ⚠️ **MASALAH: DELETE DETECTION TIDAK AKURAT**

### **Contoh Masalah:**

**Kondisi:**
- Last sync: 2 jam yang lalu
- Resi "SPX123" dihapus dari Supabase 1 jam yang lalu
- Cache: Masih ada "SPX123"

**Alur Incremental Sync:**
1. Query sejak lastSyncTime (2 jam yang lalu)
2. Hasil: Tidak ada "SPX123" (karena dihapus 1 jam yang lalu)
3. Compare: cache (ada SPX123) vs Supabase (tidak ada SPX123)
4. **SALAH**: Menganggap SPX123 dihapus, padahal mungkin masih ada di Supabase (tapi tidak ter-fetch karena di luar window incremental)

**Masalah:**
- ❌ Incremental query tidak fetch data lama
- ❌ Delete detection tidak akurat untuk data yang dihapus di luar window incremental

---

## ✅ **SOLUSI: OPTIMASI BANDWIDTH**

### **Opsi 1: Query 7 Hari untuk Delete Detection (Akurat, Tapi Lebih Besar)**

```kotlin
private suspend fun performDailyResiSync() {
    // 1. Incremental sync (data baru/modified)
    val lastSyncTime = BarcodeCacheManager.getLastResiSyncTime()
    val deltaRecords = barcodeSupabaseService.getBarcodeResiSince(sinceDate)
    // Upsert data baru...
    
    // 2. Query 7 hari untuk delete detection (akurat)
    val sevenDaysAgo = getSevenDaysAgoDate()
    val all7DaysRecords = barcodeSupabaseService.getBarcodeResiSince(sevenDaysAgo)
    val supabaseResiSet = all7DaysRecords.map { it.Resi.trim().uppercase() }.toSet()
    
    // 3. Compare untuk delete detection
    val deletedResi = cachedResiSet - supabaseResiSet
    // Hapus yang tidak ada...
}
```

**Bandwidth:**
- Incremental: ~10-50 KB
- Delete detection (7 hari): ~1.75 MB
- **Total: ~1.8 MB per sync** ❌ (tidak hemat)

---

### **Opsi 2: Batch Verification untuk Delete Detection (Hemat & Akurat)**

```kotlin
private suspend fun performDailyResiSync() {
    // 1. Incremental sync (data baru/modified)
    val deltaRecords = barcodeSupabaseService.getBarcodeResiSince(sinceDate)
    // Upsert data baru...
    
    // 2. Delete detection: Hanya verify resi yang "missing" dari incremental
    val cachedResi = BarcodeCacheManager.getAllResiRecords()
    val cachedResiSet = cachedResi.map { it.Resi.trim().uppercase() }.toSet()
    val deltaResiSet = deltaRecords.map { it.Resi.trim().uppercase() }.toSet()
    
    // Resi yang ada di cache tapi tidak ada di hasil incremental
    val potentiallyDeleted = cachedResiSet - deltaResiSet
    
    // 3. Batch verification: Cek apakah benar-benar tidak ada
    if (potentiallyDeleted.isNotEmpty()) {
        val existsInResi = barcodeSupabaseService.batchCheckResiExists(potentiallyDeleted.toList())
        // Hapus yang benar-benar tidak ada...
    }
}
```

**Bandwidth:**
- Incremental: ~10-50 KB
- Batch verification: ~5-20 KB (hanya untuk resi yang "missing")
- **Total: ~15-70 KB per sync** ✅ (hemat & akurat)

---

### **Opsi 3: Full Sync 7 Hari Sekali Per Hari (Paling Hemat)**

```kotlin
private suspend fun performDailyResiSync() {
    val lastFullSync = BarcodeCacheManager.getLastFullResiSyncTime()
    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
    
    if (lastFullSync < oneDayAgo) {
        // Full sync 7 hari (sekali per hari)
        val sevenDaysAgo = getSevenDaysAgoDate()
        val allRecords = barcodeSupabaseService.getBarcodeResiSince(sevenDaysAgo)
        // Upsert semua + delete detection
    } else {
        // Incremental sync (hanya data baru)
        val deltaRecords = barcodeSupabaseService.getBarcodeResiSince(sinceDate)
        // Upsert data baru (tidak ada delete detection untuk incremental)
    }
}
```

**Bandwidth:**
- Full sync (sekali per hari): ~1.75 MB
- Incremental (96x per hari): ~10-50 KB × 96 = ~960 KB - 4.8 MB
- **Total: ~2.7-6.5 MB per hari** ✅ (lebih hemat)

---

## 📊 **PERBANDINGAN BANDWIDTH**

| Opsi | Bandwidth per Sync | Bandwidth per Hari | Akurasi Delete Detection |
|------|-------------------|-------------------|-------------------------|
| **Saat Ini** | ~10-50 KB | ~960 KB - 4.8 MB | ❌ Tidak akurat (incremental) |
| **Opsi 1** | ~1.8 MB | ~172.8 MB | ✅ Akurat (7 hari) |
| **Opsi 2** | ~15-70 KB | ~1.4-6.7 MB | ✅ Akurat (batch verify) |
| **Opsi 3** | ~10-50 KB / ~1.75 MB | ~2.7-6.5 MB | ✅ Akurat (full sync sekali) |

---

## 🎯 **REKOMENDASI**

**Gunakan Opsi 3** karena:
1. ✅ **Paling Hemat**: ~2.7-6.5 MB per hari
2. ✅ **Akurat**: Full sync 7 hari sekali per hari untuk delete detection
3. ✅ **Efisien**: Incremental sync untuk update harian

**Atau Opsi 2** jika ingin lebih sering delete detection:
1. ✅ **Hemat**: ~1.4-6.7 MB per hari
2. ✅ **Akurat**: Batch verification untuk setiap sync
3. ✅ **Real-time**: Delete detection setiap sync

Apakah Anda ingin saya mengimplementasikan optimasi bandwidth ini?

