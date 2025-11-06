# Fix: Filter Scan untuk ONTIME Only

## 📋 Requirement yang Benar

Dari penjelasan user, nilai di summary info harus:

### Total
- ✅ Total resi dengan expedisi sesuai untuk hari ini
- ✅ Filter: `couriername = expedition`, `created = today`, `flag = NO`
- ✅ **STATUS: SUDAH BENAR**

### Scan
- ❌ Total resi yang sudah di scan hari ini **HANYA untuk schedule="ontime"**
- ❌ Filter sebelumnya: `Keterangan = expedition`, `created = today` (SEMUA schedule)
- ❌ **STATUS: PERLU FIX**

### Sisa
- ✅ Sisa = Total - Scan
- ✅ **STATUS: OTOMATIS BENAR jika Scan benar**

## 🐛 Masalah Sebelumnya

### Implementasi Lama (Line 1110-1121):

```kotlin
// Scan: dari cache tbl_resi hari ini dengan Keterangan = expedition
val resiRecords = enhancedRepository.getBarcodeResiRecords()
val scanned = resiRecords.count { rec ->
    val dateOk = try {
        val created = rec.created
        if (created.isNullOrBlank()) false else {
            val datePart = if (created.length >= 10) created.substring(0, 10) else created
            java.time.LocalDate.parse(datePart) == todayDate
        }
    } catch (_: Exception) { false }
    dateOk && rec.Keterangan == expedition  // ❌ TIDAK filter schedule!
}
```

**Masalah**:
- ❌ Menghitung SEMUA scan (ontime, late, batal)
- ❌ Tidak filter berdasarkan `schedule`

**Contoh**:
```
JNE hari ini:
- 100 resi total (flag=NO)
- 50 resi di-scan ontime
- 10 resi di-scan late
- 5 resi di-scan batal

Hasil lama: Total=100, Scan=65 (ontime+late+batal) ❌ SALAH
Yang benar: Total=100, Scan=50 (ontime only) ✅
```

## ✅ Solusi Implementasi

### Implementasi Baru (Fixed):

```kotlin
// Scan: dari cache tbl_resi hari ini dengan Keterangan = expedition DAN schedule = "ontime"
val resiRecords = enhancedRepository.getBarcodeResiRecords()
val scanned = resiRecords.count { rec ->
    val dateOk = try {
        val created = rec.created
        if (created.isNullOrBlank()) false else {
            val datePart = if (created.length >= 10) created.substring(0, 10) else created
            java.time.LocalDate.parse(datePart) == todayDate
        }
    } catch (_: Exception) { false }
    // **PERBAIKAN**: Filter HANYA schedule="ontime"
    val isOntime = rec.schedule?.lowercase() == "ontime"
    dateOk && rec.Keterangan == expedition && isOntime  // ✅ Filter schedule!
}
```

**Perubahan**:
- ✅ Tambah filter: `val isOntime = rec.schedule?.lowercase() == "ontime"`
- ✅ Kondisi: `dateOk && rec.Keterangan == expedition && isOntime`

**Hasil Sekarang**:
```
JNE hari ini:
- 100 resi total (flag=NO)
- 50 resi di-scan ontime
- 10 resi di-scan late  
- 5 resi di-scan batal

Hasil baru: Total=100, Scan=50 (ontime only) ✅ BENAR!
Sisa: 100 - 50 = 50 ✅ BENAR!
```

## 📝 Update Logging

Logging juga diupdate untuk lebih jelas:

```kotlin
Log.d(TAG, "📊   - TOTAL resi (couriername='$expedition', today, flag=NO): $finalTotal")
Log.d(TAG, "📊   - SCANNED resi (Keterangan='$expedition', today, schedule=ontime): $scanned")
Log.d(TAG, "📊   - REMAINING (Total - Scanned): $remaining")
```

## 🎯 Hasil

Setelah perbaikan ini:

### UI Display:
```
Total: 100 - Scan: 50 - Sisa: 50
```

**Artinya**:
- **Total (100)**: JNE hari ini, flag=NO
- **Scan (50)**: Sudah di-scan hari ini, schedule=ontime only
- **Sisa (50)**: Yang belum di-scan atau di-scan tapi bukan ontime

## 🧪 Testing

Test dengan:
1. Scan resi dengan schedule="ontime" → Scan +1, Sisa -1 ✅
2. Scan resi dengan schedule="late" → Scan tidak berubah, Sisa tidak berubah ✅
3. Scan resi dengan schedule="batal" → Scan tidak berubah, Sisa tidak berubah ✅

## 📊 File Modified

- ✅ `app/src/main/java/com/example/cekpicklist/viewmodel/BarcodeScannerViewModel.kt`
  - Line 1110-1123: Add filter `schedule="ontime"`
  - Line 1151: Update logging message

---

**Perbaikan ini memastikan nilai Scan hanya menghitung resi yang di-scan dengan schedule=ontime!** ✅

