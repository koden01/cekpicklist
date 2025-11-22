# 🔍 **LOGIKA DELETE DETECTION**

## 🎯 **KONSEP**

### **Kondisi:**
- **Cache Resi**: Menyimpan semua resi yang ada di `tbl_resi` (7 hari terakhir)
- **Cache Expedisi**: Hanya menyimpan resi dengan `flag="NO"` dari `tbl_expedisi`
- **Trigger Supabase**: Otomatis ubah flag menjadi "NO" saat resi dihapus dari `tbl_resi`

---

## 📊 **SKENARIO DELETE DETECTION**

### **SKENARIO 1: Resi Dihapus dari tbl_resi**

**Kondisi:**
- Resi "SPX123" ada di cache resi
- Resi "SPX123" dihapus dari `tbl_resi` (oleh admin)
- Trigger Supabase: Otomatis ubah flag menjadi "NO" di `tbl_expedisi`

**Alur:**

```
1. Background sync
   → performDailyExpedisiSync()
   → Fetch data flag="NO" dari Supabase
   → Hasil: "SPX123" dengan flag="NO" (sudah di-update trigger)

2. Update cache expedisi
   → BarcodeCacheManager.addExpedisiRecord("SPX123", flag="NO")
   → Cache expedisi: "SPX123" ada (flag="NO")

3. Compare cache resi vs cache expedisi
   → cachedResiSet = {SPX123, SPX456, ...}
   → expedisiResiSet = {SPX123, SPX456, ...}
   → deletedResi = cachedResiSet - expedisiResiSet
   → Hasil: {} (kosong, karena SPX123 ada di expedisi)

4. Kesimpulan
   → Resi "SPX123" TIDAK dihapus (masih ada di expedisi dengan flag="NO")
   → Cache resi: TIDAK dihapus
```

**Hasil:**
- ✅ Resi "SPX123" tetap ada di cache resi (karena masih ada di expedisi)

---

### **SKENARIO 2: Resi Benar-Benar Dihapus (Tidak Ada di Expedisi)**

**Kondisi:**
- Resi "SPX999" ada di cache resi
- Resi "SPX999" dihapus dari `tbl_resi` (oleh admin)
- Resi "SPX999" juga tidak ada di `tbl_expedisi` (tidak pernah ada atau sudah dihapus)

**Alur:**

```
1. Background sync
   → performDailyExpedisiSync()
   → Fetch data flag="NO" dari Supabase
   → Hasil: "SPX999" TIDAK ada di expedisi

2. Update cache expedisi
   → Cache expedisi: "SPX999" TIDAK ada

3. Compare cache resi vs cache expedisi
   → cachedResiSet = {SPX123, SPX456, SPX999, ...}
   → expedisiResiSet = {SPX123, SPX456, ...} (SPX999 tidak ada)
   → deletedResi = cachedResiSet - expedisiResiSet
   → Hasil: {SPX999}

4. Validasi: Pastikan resi benar-benar ada di cache
   → existsInCache = TRUE (SPX999 ada di cache resi)

5. Hapus dari cache resi
   → BarcodeCacheManager.removeResiRecord("SPX999")
   → Cache resi: SPX999 dihapus
```

**Hasil:**
- ✅ Resi "SPX999" dihapus dari cache resi (karena tidak ada di expedisi)

---

## 🎯 **LOGIKA YANG BENAR**

### **Aturan:**
1. ✅ **Hapus dari cache resi** jika:
   - Resi ada di cache resi
   - Resi TIDAK ada di cache expedisi (tidak ada di `tbl_expedisi` dengan flag="NO")
   - Resi benar-benar ada di cache sebelum hapus

2. ❌ **TIDAK hapus dari cache resi** jika:
   - Resi ada di cache resi
   - Resi ada di cache expedisi (flag="NO")
   - Resi masih ada di `tbl_expedisi` (tidak dihapus)

### **Rumus:**
```
deletedResi = cacheResiSet - expedisiResiSet

Jika deletedResi tidak kosong:
  Untuk setiap resi di deletedResi:
    Jika resi ada di cache resi:
      Hapus dari cache resi
```

---

## 🔧 **IMPLEMENTASI SAAT INI**

```kotlin
// 1. Ambil cache resi
val cachedResi = BarcodeCacheManager.getAllResiRecords()
val cachedResiSet = cachedResi.map { it.Resi.trim().uppercase() }.toSet()

// 2. Sync expedisi untuk dapat data terbaru
performDailyExpedisiSync()

// 3. Ambil cache expedisi setelah sync
val cachedExpedisi = BarcodeCacheManager.getAllExpedisiRecords()
val expedisiResiSet = cachedExpedisi.map { it.resino.trim().uppercase() }.toSet()

// 4. Compare: Resi yang ada di cache resi tapi TIDAK ada di cache expedisi
val deletedResi = cachedResiSet - expedisiResiSet

// 5. Hapus dari cache resi (jika ada)
if (deletedResi.isNotEmpty()) {
    deletedResi.forEach { resi ->
        // Validasi: Pastikan resi benar-benar ada di cache
        val existsInCache = cachedResi.any { 
            it.Resi.trim().uppercase() == resi 
        }
        if (existsInCache) {
            BarcodeCacheManager.removeResiRecord(resi)
        }
    }
}
```

---

## 📝 **KESIMPULAN**

**Logika Delete Detection:**
- ✅ **Hapus dari cache resi** jika resi ada di cache resi tapi **TIDAK ada di cache expedisi**
- ✅ **TIDAK hapus** jika resi ada di cache resi **DAN** ada di cache expedisi (flag="NO")

**Alasan:**
- Cache expedisi hanya menyimpan flag="NO"
- Jika resi ada di expedisi (flag="NO"), berarti resi masih ada (tidak dihapus)
- Jika resi TIDAK ada di expedisi, berarti resi benar-benar dihapus atau tidak pernah ada

Apakah logika ini sudah benar sesuai kebutuhan Anda?

