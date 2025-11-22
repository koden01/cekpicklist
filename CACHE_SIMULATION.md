# 🔄 **SIMULASI PENGISIAN DAN UPDATE CACHE**

## 🎯 **SKENARIO SIMULASI**

### **SKENARIO 1: Initial Load (Cache Kosong)**

**Kondisi:**
- Aplikasi pertama kali dibuka
- Cache resi: **KOSONG**
- Cache expedisi: **KOSONG**

**Alur:**

```
1. User buka aplikasi
   → BarcodeScannerViewModel.initializeCacheAsync()
   → EnhancedRepository.getBarcodeResiRecords()

2. Check cache resi
   → cachedRecords.isEmpty() = TRUE
   → Log: "🔥 [INITIAL SYNC] Cache empty, fetching 7 days data (blocking)..."

3. Fetch data 7 hari dari Supabase
   → sevenDaysAgo = "2024-01-04" (hari ini: 2024-01-10)
   → Query: getBarcodeResiSince("2024-01-04")
   → Hasil: 500 records (7 hari terakhir)

4. Simpan ke cache resi
   → BarcodeCacheManager.initializeCache(500 records, emptyList())
   → lastResiSyncTime = 2024-01-10 08:00:00

5. Fetch data expedisi flag="NO"
   → EnhancedRepository.getBarcodeExpedisiRecords()
   → cachedRecords.isEmpty() = TRUE
   → Query: getAllBarcodeExpedisi() (flag="NO")
   → Hasil: 1000 records

6. Simpan ke cache expedisi
   → BarcodeCacheManager.initializeCache(emptyList(), 1000 records)
   → lastExpedisiSyncTime = 2024-01-10 08:00:00

7. Return data ke UI
   → UI menampilkan 500 resi records
   → UI menampilkan 1000 expedisi records (flag="NO")
```

**Hasil:**
- ✅ Cache resi: 500 records
- ✅ Cache expedisi: 1000 records
- ✅ Bandwidth: ~1.75 MB (sekali)

---

### **SKENARIO 2: Daily Sync (Incremental - Cache Sudah Ada)**

**Kondisi:**
- Cache resi: 500 records (last sync: 2024-01-10 08:00:00)
- Cache expedisi: 1000 records (last sync: 2024-01-10 08:00:00)
- Waktu sekarang: 2024-01-10 08:15:00 (15 menit kemudian)

**Alur:**

```
1. Background sync (setiap 15 menit)
   → performDailyResiSync()
   → performDailyExpedisiSync()

2. Resi Sync:
   → lastSyncTime = 2024-01-10 08:00:00
   → sinceDate = "2024-01-10T08:00:00Z"
   → Query: getBarcodeResiSince("2024-01-10T08:00:00Z")
   → Hasil: 5 records baru (dibuat dalam 15 menit terakhir)

3. Update cache resi
   → BarcodeCacheManager.addResiRecord(record1) // Upsert
   → BarcodeCacheManager.addResiRecord(record2) // Upsert
   → ... (5 records)
   → lastResiSyncTime = 2024-01-10 08:15:00

4. Expedisi Sync:
   → lastSyncTime = 2024-01-10 08:00:00
   → sinceDate = "2024-01-10T08:00:00Z"
   → Query: getBarcodeExpedisiSince("2024-01-10T08:00:00Z")
   → Hasil: 3 records baru (flag="NO"), 2 records updated (flag berubah ke "NO")

5. Update cache expedisi
   → BarcodeCacheManager.addExpedisiRecord(record1) // Tambah baru
   → BarcodeCacheManager.addExpedisiRecord(record2) // Tambah baru
   → BarcodeCacheManager.addExpedisiRecord(record3) // Tambah baru
   → BarcodeCacheManager.addExpedisiRecord(record4) // Update (flag="NO")
   → BarcodeCacheManager.addExpedisiRecord(record5) // Update (flag="NO")
   → lastExpedisiSyncTime = 2024-01-10 08:15:00
```

**Hasil:**
- ✅ Cache resi: 505 records (+5 baru)
- ✅ Cache expedisi: 1003 records (+3 baru, +2 updated)
- ✅ Bandwidth: ~15 KB (incremental)

---

### **SKENARIO 3: Scan Barcode (Update Cache)**

**Kondisi:**
- User scan barcode: "SPX123456"
- Cache resi: 505 records
- Cache expedisi: 1003 records

**Alur:**

```
1. User scan barcode "SPX123456"
   → BarcodeScannerViewModel.processBarcode("SPX123456")
   → EnhancedRepository.saveBarcodeResiRecord(record)

2. Validasi resi di cache expedisi
   → BarcodeCacheManager.getExpedisiRecordByResino("SPX123456")
   → Hasil: FOUND (flag="NO")

3. Tambah ke cache resi (optimistic)
   → BarcodeCacheManager.addResiRecord(record)
   → Cache resi: 506 records (+1)

4. POST ke Supabase (background)
   → barcodeSupabaseService.saveBarcodeScan(record)
   → Hasil: SUCCESS (201 Created)

5. ❌ TIDAK update flag (trigger sudah handle)
   → Trigger di Supabase otomatis update flag menjadi "YES"
   → Tidak perlu update flag manual

6. Hapus dari cache expedisi (karena flag="YES" oleh trigger)
   → BarcodeCacheManager.removeExpedisiRecord("SPX123456")
   → Cache expedisi: 1002 records (-1)
```

**Hasil:**
- ✅ Cache resi: 506 records (+1)
- ✅ Cache expedisi: 1002 records (-1)
- ✅ Bandwidth: ~1 KB (hanya POST, tidak ada UPDATE)

---

### **SKENARIO 4: Delete Detection (Cache-Based)**

**Kondisi:**
- Cache resi: 506 records (termasuk "SPX999999")
- Cache expedisi: 1002 records
- Resi "SPX999999" dihapus dari Supabase (oleh admin)
- Trigger Supabase: Otomatis ubah flag menjadi "NO" di expedisi

**Alur:**

```
1. Background sync (setiap 15 menit)
   → detectAndRemoveDeletedResi()

2. Sync expedisi untuk dapat data terbaru
   → performDailyExpedisiSync()
   → Query: getBarcodeExpedisiSince(lastSyncTime)
   → Hasil: "SPX999999" dengan flag="NO" (sudah di-update trigger)

3. Update cache expedisi
   → BarcodeCacheManager.addExpedisiRecord("SPX999999", flag="NO")
   → Cache expedisi: 1003 records (+1)

4. Compare cache resi vs cache expedisi
   → cachedResiSet = {SPX123456, SPX123457, ..., SPX999999, ...}
   → expedisiResiSet = {SPX123456, SPX123457, ..., SPX999999, ...}
   → deletedResi = cachedResiSet - expedisiResiSet
   → Hasil: {} (kosong, karena SPX999999 ada di expedisi)

5. Jika resi benar-benar dihapus (tidak ada di expedisi juga):
   → deletedResi = {SPX999999}
   → Validasi: Pastikan resi benar-benar ada di cache sebelum hapus
   → existsInCache = TRUE
   → BarcodeCacheManager.removeResiRecord("SPX999999")
   → Cache resi: 505 records (-1)
```

**Hasil:**
- ✅ Cache resi: 505 records (-1 jika benar-benar dihapus dan ada di cache)
- ✅ Cache expedisi: 1003 records (+1 jika flag di-update trigger)
- ✅ Bandwidth: ~5 KB (incremental sync)

---

### **SKENARIO 5: Cleanup Data Lama (> 7 Hari)**

**Kondisi:**
- Cache resi: 505 records
- Beberapa records sudah > 7 hari (created: 2024-01-02)
- Hari ini: 2024-01-10

**Alur:**

```
1. Background sync
   → performDailyResiSync()
   → BarcodeCacheManager.cleanupOldData()

2. Check setiap record di cache
   → record1.created = "2024-01-10" → KEEP (dalam 7 hari)
   → record2.created = "2024-01-09" → KEEP (dalam 7 hari)
   → record3.created = "2024-01-02" → REMOVE (> 7 hari)
   → record4.created = "2024-01-01" → REMOVE (> 7 hari)

3. Hapus data > 7 hari
   → BarcodeCacheManager.removeResiRecord(record3)
   → BarcodeCacheManager.removeResiRecord(record4)
   → Cache resi: 503 records (-2)
```

**Hasil:**
- ✅ Cache resi: 503 records (-2 data lama)
- ✅ Cache tetap bersih (hanya 7 hari terakhir)

---

### **SKENARIO 6: Multiple Scans (Batch Update)**

**Kondisi:**
- User scan 10 barcode sekaligus
- Cache resi: 503 records
- Cache expedisi: 1003 records

**Alur:**

```
1. User scan 10 barcode
   → SPX001, SPX002, ..., SPX010

2. Untuk setiap barcode:
   → Validasi di cache expedisi
   → Tambah ke cache resi (optimistic)
   → POST ke Supabase (background)
   → Update flag di Supabase
   → Hapus dari cache expedisi

3. Update cache:
   → Cache resi: 513 records (+10)
   → Cache expedisi: 993 records (-10)

4. Background sync (setelah 15 menit)
   → performDailyResiSync()
   → Query: getBarcodeResiSince(lastSyncTime)
   → Hasil: 10 records baru (sudah ada di cache)
   → Upsert: Tidak ada perubahan (sudah ada)
```

**Hasil:**
- ✅ Cache resi: 513 records (+10)
- ✅ Cache expedisi: 993 records (-10)
- ✅ Bandwidth: ~20 KB (10 POST + 10 UPDATE)

---

## 📊 **RINGKASAN BANDWIDTH**

| Skenario | Bandwidth | Frekuensi | Total per Hari |
|----------|-----------|-----------|----------------|
| **Initial Load** | ~1.75 MB | Sekali | ~1.75 MB |
| **Daily Sync** | ~15 KB | 96x (15 menit) | ~1.44 MB |
| **Scan Barcode** | ~1 KB | 100x (estimasi) | ~100 KB |
| **Delete Detection** | ~5 KB | 96x (15 menit) | ~480 KB |
| **Cleanup** | 0 KB | 96x (15 menit) | 0 KB |
| **TOTAL** | - | - | **~3.77 MB per hari** ✅ |

---

## 🎯 **KESIMPULAN**

**Cache Management:**
- ✅ **Initial Load**: Ambil 7 hari (sekali, saat cache kosong)
- ✅ **Daily Sync**: Hanya incremental (data baru/modified)
- ✅ **Scan Barcode**: Update cache optimistic (instant)
- ✅ **Delete Detection**: Cache-based (tidak perlu query)
- ✅ **Cleanup**: Otomatis hapus data > 7 hari

**Bandwidth:**
- ✅ **~3.87 MB per hari** (sangat hemat)
- ✅ **~116 MB per bulan** (sustainable untuk mobile data)

**Performance:**
- ✅ **Instant**: Cache-based operations
- ✅ **Efficient**: Hanya sync data yang berubah
- ✅ **Reliable**: Always sync dengan Supabase

