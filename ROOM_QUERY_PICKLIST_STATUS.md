# Room Database Query untuk Picklist Status

## 🎯 Solusi yang Diimplementasikan

Berdasarkan saran user untuk menggunakan query langsung dari Room database, saya telah mengimplementasikan solusi yang lebih efisien untuk menghitung status picklist.

## 🔧 Perubahan yang Dilakukan

### 1. **Data Class untuk Hasil Query**
```kotlin
// PicklistDao.kt
data class PicklistCompletionStatus(
    val no_picklist: String,
    val total_qty: Int,
    val scanned_qty: Int,
    val remaining_qty: Int,
    val is_scanned: Int // 1 = true, 0 = false
)
```

### 2. **Query Room yang Efisien**
```sql
-- Menggunakan kolom qty dari tabel picklist untuk total qty
-- Menghitung scanned qty dari tabel picklist_scan berdasarkan EPC unik
SELECT 
    p.no_picklist,
    COALESCE(SUM(p.qty), 0) as total_qty,
    COALESCE(COUNT(DISTINCT ps.epc), 0) as scanned_qty,
    COALESCE(SUM(p.qty), 0) - COALESCE(COUNT(DISTINCT ps.epc), 0) as remaining_qty,
    CASE 
        WHEN COALESCE(COUNT(DISTINCT ps.epc), 0) > 0 THEN 1 
        ELSE 0 
    END as is_scanned
FROM picklist p
LEFT JOIN picklist_scan ps ON p.no_picklist = ps.no_picklist 
    AND p.article_id = ps.article_id 
    AND (p.size = ps.size OR (p.size IS NULL AND ps.size IS NULL))
GROUP BY p.no_picklist
ORDER BY p.no_picklist ASC
```

### 3. **Method DAO Baru**
```kotlin
// PicklistDao.kt
suspend fun getAllPicklistCompletionStatuses(): List<PicklistCompletionStatus>
suspend fun getPicklistCompletionStatuses(picklistNumbers: List<String>): List<PicklistCompletionStatus>
suspend fun getPicklistCompletionStatus(picklistNo: String): PicklistCompletionStatus?
```

### 4. **Repository Method Baru**
```kotlin
// Repository.kt
suspend fun getAllPicklistCompletionStatusesFromRoom(): List<PicklistStatus>
```

### 5. **ViewModel Method Baru**
```kotlin
// ScanViewModel.kt
fun loadPicklistStatusesFromRoom()
fun refreshPicklistStatuses() // Updated to use Room query
```

### 6. **Activity Update**
```kotlin
// HalamanAwalActivity.kt
override fun onResume() {
    // Menggunakan Room query untuk performa yang lebih baik
    viewModel.loadPicklistStatusesFromRoom()
}

private fun showPicklistSelectionModal() {
    // Refresh menggunakan Room query sebelum menampilkan modal
    viewModel.loadPicklistStatusesFromRoom()
}
```

## 🚀 Keuntungan Solusi Room Query

### **1. Performa Lebih Baik**
- ✅ **Single SQL Query**: Menghitung semua status dalam satu query
- ✅ **Database Level**: Perhitungan dilakukan di level database, bukan di aplikasi
- ✅ **Batch Processing**: Mengambil semua picklist sekaligus

### **2. Akurasi Data**
- ✅ **Real-time**: Data langsung dari database tanpa cache
- ✅ **Konsisten**: Menggunakan kolom `qty` yang sudah ada di tabel picklist
- ✅ **Sinkron**: Selalu menggunakan data terbaru

### **3. Efisiensi Resource**
- ✅ **Memory Efficient**: Tidak perlu load semua data ke memory
- ✅ **CPU Efficient**: Database engine yang dioptimalkan untuk query
- ✅ **Network Efficient**: Tidak perlu fetch dari Supabase berulang kali

## 📊 Perbandingan Performa

### **Sebelum (ViewModel Calculation)**
```kotlin
// Load semua picklist items ke memory
val items = repository.getPicklistItemsBatch(picklistNumbers)
// Hitung di aplikasi
val totalQty = items.sumOf { it.qtyPl }
val scannedQty = items.sumOf { it.qtyScan }
```

### **Sesudah (Room Query)**
```sql
-- Hitung langsung di database
SELECT SUM(p.qty) as total_qty, COUNT(DISTINCT ps.epc) as scanned_qty
FROM picklist p LEFT JOIN picklist_scan ps ON ...
```

## 🔍 Cara Kerja Query

### **1. Total Qty**
```sql
COALESCE(SUM(p.qty), 0) as total_qty
```
- Menggunakan kolom `qty` dari tabel `picklist`
- `COALESCE` memastikan hasil tidak null

### **2. Scanned Qty**
```sql
COALESCE(COUNT(DISTINCT ps.epc), 0) as scanned_qty
```
- Menghitung EPC unik yang sudah di-scan
- `DISTINCT` memastikan tidak ada duplikasi

### **3. Remaining Qty**
```sql
COALESCE(SUM(p.qty), 0) - COALESCE(COUNT(DISTINCT ps.epc), 0) as remaining_qty
```
- Selisih antara total dan scanned
- Bisa negatif jika ada overscan

### **4. Is Scanned**
```sql
CASE 
    WHEN COALESCE(COUNT(DISTINCT ps.epc), 0) > 0 THEN 1 
    ELSE 0 
END as is_scanned
```
- 1 = sudah pernah di-scan
- 0 = belum pernah di-scan

## 🎯 Hasil yang Diharapkan

Setelah implementasi ini:

1. **Modal akan selalu menampilkan status yang akurat** karena menggunakan data langsung dari database
2. **Performa lebih cepat** karena menggunakan SQL query yang dioptimalkan
3. **Memory usage lebih rendah** karena tidak perlu load semua data ke memory
4. **Status konsisten** antara modal dan saat picklist dibuka

## 🧪 Testing

Untuk memverifikasi solusi ini:

1. **Scan beberapa RFID** pada picklist
2. **Kembali ke halaman awal** dan buka modal
3. **Verifikasi status** picklist menunjukkan data yang akurat
4. **Monitor log** untuk melihat query Room yang dijalankan

## 📝 Log yang Diharapkan

```
🔥 Loading picklist statuses using Room query
🚀 Getting all picklist completion statuses using Room query
✅ Retrieved 5 picklist completion statuses using Room query
📊 2 271025.0841 SPLT_ 4: scanned=true, total=46, scanned=46, remaining=0
```

Solusi ini mengatasi masalah "meskipun di log false tapi saat di buka picklist sudah sesuai" dengan menggunakan data langsung dari database yang selalu ter-update.













