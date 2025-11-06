# 📊 **OVERVIEW: Data yang Di-Update Secara Background dengan Supabase**

## 🎯 **JENIS DATA YANG DI-SYNC BACKGROUND**

Aplikasi ini melakukan **background sync** untuk beberapa jenis data ke Supabase secara otomatis. Berikut daftar lengkapnya:

---

## 📦 **1. BARCODE RESI RECORD (tbl_resi)**

### **Kapan Di-Update:**
- ✅ Saat user **scan barcode** baru
- ✅ Saat **background sync retry** (jika gagal sebelumnya)

### **Cara Kerja:**
```kotlin
// EnhancedRepository.saveBarcodeResiRecord()
1. Save ke cache INSTAN (local-first)
2. POST ke Supabase di background (non-blocking)
3. Jika gagal → Background sync akan retry otomatis
```

### **Data yang Di-Save:**
- `Resi` - Nomor resi (barcode)
- `created` - Timestamp (ISO 8601)
- `Keterangan` - Courier name atau scanner type
- `nokarung` - Nomor karung (opsional)
- `schedule` - Schedule status (opsional)

### **Ukuran Data:**
- ~500 bytes per record (JSON)

### **Background Sync:**
- ✅ **POST langsung** saat scan (non-blocking)
- ✅ **Retry otomatis** via background sync (setiap 60 detik)
- ✅ **Timestamp-based fetch** untuk optimasi bandwidth

---

## 📦 **2. BARCODE EXPEDISI RECORD (tbl_expedisi)**

### **Kapan Di-Update:**
- ✅ Saat ada **session expedisi baru**
- ✅ Saat **update flag** dari "NO" ke "YES"

### **Cara Kerja:**
```kotlin
// EnhancedRepository.saveBarcodeExpedisiRecord()
1. POST ke Supabase (saveBarcodeSession)
2. Update cache setelah berhasil (optimistic update)
```

### **Data yang Di-Save:**
- `resino` - Nomor resi
- `orderno` - Nomor order
- `couriername` - Nama courier
- `flag` - Status ("NO" atau "YES")
- `created` - Timestamp (opsional)

### **Ukuran Data:**
- ~300 bytes per record (JSON)

### **Background Sync:**
- ✅ **POST langsung** saat create session
- ✅ **Update flag** saat resi di-scan

---

## 📦 **3. PICKLIST SCANS (RFID) (tbl_picklist_scan)**

### **Kapan Di-Update:**
- ✅ Saat user **scan RFID** dan klik "Clear"
- ✅ Saat **batch save** valid data

### **Cara Kerja:**
```kotlin
// ScanViewModel.saveValidDataToSupabaseBatch()
1. Filter data valid (skip overscan & non-picklist)
2. Batch save ke Supabase (savePicklistScansBatch)
3. Reload data dari database setelah save
```

### **Data yang Di-Save:**
- `picklistNo` - Nomor picklist
- `articleId` - ID artikel
- `epc` - EPC RFID tag
- `productId` - ID produk
- `articleName` - Nama artikel
- `size` - Ukuran (opsional)
- `notrans` - Nomor transaksi (opsional)

### **Validasi:**
- ✅ **Skip overscan** (qtyScan > qtyPl)
- ✅ **Skip non-picklist** (tidak ada di picklist items)
- ✅ **Skip duplikasi** (EPC sudah ada di database)

### **Ukuran Data:**
- ~400 bytes per record (JSON)
- Batch save: ~10-100 records per batch

### **Background Sync:**
- ✅ **Batch save** saat Clear (GlobalScope, non-blocking)
- ✅ **Validasi ketat** sebelum save

---

## 🔄 **4. BACKGROUND SYNC (Setiap 60 Detik)**

### **Fungsi Background Sync:**

#### **A. Sync Pending Operations**
```kotlin
// BackgroundSyncManager.performSync()
1. Ambil pending operations dari PendingOperationsManager
2. Process setiap operation
3. Retry jika gagal (max 3 retries)
```

#### **B. Detect Deleted Resi (tbl_resi)**
```kotlin
// EnhancedRepository.detectAndRemoveDeletedResi()
1. Fetch data dari Supabase (timestamp-based, hanya perubahan)
2. Bandingkan dengan cache
3. Retry save untuk data yang gagal sync
4. Remove dari cache jika dihapus di Supabase
```

#### **C. Detect Deleted Expedisi (tbl_expedisi)**
```kotlin
// EnhancedRepository.detectAndRemoveDeletedExpedisi()
1. Fetch data dari Supabase (timestamp-based, hanya perubahan)
2. Bandingkan dengan cache
3. Remove dari cache jika dihapus di Supabase
```

#### **D. Cleanup Old Data (> 4 hari)**
```kotlin
// BarcodeCacheManager.cleanupOldData()
1. Check apakah perlu cleanup (setiap 24 jam)
2. Hapus data > 4 hari dari cache
3. Save ke persistent storage
```

---

## 📊 **RINGKASAN DATA YANG DI-SYNC**

| Data Type | Tabel Supabase | Trigger | Background Sync | Bandwidth |
|-----------|----------------|---------|-----------------|-----------|
| **Barcode Resi** | `tbl_resi` | Scan barcode | ✅ POST langsung + Retry | ~500 bytes/scan |
| **Barcode Expedisi** | `tbl_expedisi` | Create session | ✅ POST langsung | ~300 bytes/session |
| **Picklist Scans (RFID)** | `tbl_picklist_scan` | Clear scan | ✅ Batch save | ~400 bytes/scan |
| **Sync Two-Way** | `tbl_resi`, `tbl_expedisi` | Background sync (60s) | ✅ Fetch + Compare | ~5-10 KB/sync |
| **Cleanup** | Cache local | Background sync (24h) | ✅ Auto cleanup | Minimal |

---

## 🔄 **ALUR BACKGROUND SYNC**

### **1. Saat Scan Barcode:**
```
User scan barcode
  ↓
Save ke cache (instant)
  ↓
POST ke Supabase (background, non-blocking)
  ├─ Success → ✅ Log success
  └─ Failed → ⚠️ Background sync akan retry
```

### **2. Saat Scan RFID + Clear:**
```
User scan RFID → Click Clear
  ↓
Filter data valid (skip overscan & non-picklist)
  ↓
Batch save ke Supabase (background, GlobalScope)
  ↓
Reload data dari database
```

### **3. Background Sync (Setiap 60 Detik):**
```
BackgroundSyncManager.performSync()
  ├─ 1. Sync pending operations
  ├─ 2. Detect deleted resi (timestamp-based)
  ├─ 3. Detect deleted expedisi (timestamp-based)
  └─ 4. Cleanup old data (> 4 hari, setiap 24 jam)
```

---

## ✅ **OPTIMASI BANDWIDTH**

### **1. Timestamp-Based Fetch**
- ✅ Fetch **HANYA perubahan** sejak lastSyncTime
- ✅ Initial sync: ~800 KB (1x saja)
- ✅ Incremental sync: ~5-10 KB per sync
- ✅ Penghematan: **99.98%** (dari 28.8 GB → 5.8 MB/bulan)

### **2. Local-First Strategy**
- ✅ Save ke cache INSTAN (tidak blocking)
- ✅ POST ke Supabase di background (non-blocking)
- ✅ Retry otomatis jika gagal

### **3. Batch Processing**
- ✅ Batch save untuk RFID scans
- ✅ Validasi sebelum save (skip invalid data)
- ✅ Duplikasi check sebelum save

---

## 📈 **ESTIMASI BANDWIDTH**

### **Per Hari (100 scan barcode + 50 RFID scans):**
- Barcode Resi: 100 × 500 bytes = **50 KB**
- Picklist Scans: 50 × 400 bytes = **20 KB**
- Background Sync: 24 × 10 KB = **240 KB**
- **Total: ~310 KB/hari**

### **Per Bulan:**
- User operations: 310 KB × 30 = **9.3 MB**
- Background sync: **5.8 MB**
- **Total: ~15 MB/bulan**

---

## 🎯 **KESIMPULAN**

**Data yang di-update secara background dengan Supabase:**

1. ✅ **Barcode Resi Record** - POST saat scan (non-blocking)
2. ✅ **Barcode Expedisi Record** - POST saat create session
3. ✅ **Picklist Scans (RFID)** - Batch save saat Clear
4. ✅ **Background Sync** - Sync dua arah setiap 60 detik
5. ✅ **Cleanup Old Data** - Auto cleanup setiap 24 jam

**Semua operasi menggunakan:**
- ✅ Local-first strategy (cache first)
- ✅ Non-blocking background sync
- ✅ Auto-retry mechanism
- ✅ Bandwidth optimization (timestamp-based)

**Total bandwidth: ~15 MB/bulan** (sangat hemat!) 🚀✅

