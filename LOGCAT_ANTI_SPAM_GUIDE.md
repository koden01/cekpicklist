# 🚦 **GUIDE: Mengatasi Log Berulang (Anti-Spam Log)**

## 🎯 **MASALAH**
Logcat sering menghasilkan log yang berulang-ulang, membanjiri console dan membuat sulit untuk melihat log penting.

**Contoh masalah:**
- Log yang sama muncul berkali-kali dalam beberapa detik
- Terlalu banyak log per detik (spam)
- Log debug yang tidak penting membanjiri console

---

## ✅ **SOLUSI: Fitur Anti-Spam Log**

Script logcat sekarang memiliki **4 fitur utama** untuk mengurangi spam log:

### **1. 🚦 Rate Limiting**
Membatasi jumlah log yang ditampilkan per detik.

### **2. 🔄 Deduplication**
Mengabaikan log yang sama jika muncul kembali dalam waktu tertentu.

### **3. 🔇 Silent Mode**
Hanya menampilkan log error/warning penting, menyembunyikan log debug/info.

### **4. 📊 Log Level Filtering**
Filter log berdasarkan level (Error, Warning, Info, Debug).

---

## 🚀 **CARA PENGGUNAAN**

### **📋 Parameter yang Tersedia**

#### **Script Robust V2:**
```powershell
.\run_logcat_robust_v2.ps1 `
    -PackageName "com.example.cekpicklist" `
    -Silent $false `
    -MaxLogsPerSecond 10 `
    -DeduplicationWindow 5 `
    -MinLogLevel "Debug"
```

#### **Script Simple:**
```powershell
.\run_logcat_simple.ps1 `
    -PackageName "com.example.cekpicklist" `
    -Silent $false `
    -MaxLogsPerSecond 10 `
    -DeduplicationWindow 5 `
    -MinLogLevel "Debug"
```

---

## 📖 **PENJELASAN PARAMETER**

### **1. `-Silent` (Switch)**
**Default:** `$false`

**Fungsi:** Hanya tampilkan error/warning penting, sembunyikan log debug/info.

**Kapan digunakan:**
- ✅ Ketika hanya ingin melihat error/warning
- ✅ Ketika console terlalu ramai dengan log debug
- ✅ Untuk monitoring production yang lebih clean

**Contoh:**
```powershell
# Silent mode - hanya tampilkan errors/warnings
.\run_logcat_robust_v2.ps1 -Silent

# Normal mode - tampilkan semua log
.\run_logcat_robust_v2.ps1
```

**Catatan:** Semua log tetap disimpan ke file, hanya console output yang di-filter.

---

### **2. `-MaxLogsPerSecond` (Integer)**
**Default:** `10`

**Fungsi:** Batasi jumlah log yang ditampilkan per detik.

**Kapan digunakan:**
- ✅ Ketika log terlalu banyak per detik (spam)
- ✅ Ketika ingin mengurangi beban console output
- ✅ Untuk fokus pada log penting saja

**Contoh:**
```powershell
# Maksimal 5 log per detik (lebih ketat)
.\run_logcat_robust_v2.ps1 -MaxLogsPerSecond 5

# Maksimal 20 log per detik (lebih longgar)
.\run_logcat_robust_v2.ps1 -MaxLogsPerSecond 20

# Default: 10 log per detik
.\run_logcat_robust_v2.ps1
```

**Tips:**
- **5 logs/detik**: Untuk monitoring production yang sangat ketat
- **10 logs/detik**: Default, balance antara detail dan readability
- **20+ logs/detik**: Untuk debugging yang membutuhkan lebih banyak detail

---

### **3. `-DeduplicationWindow` (Integer, detik)**
**Default:** `5`

**Fungsi:** Abaikan log yang sama jika muncul kembali dalam waktu N detik.

**Kapan digunakan:**
- ✅ Ketika log yang sama muncul berulang-ulang
- ✅ Ketika ada loop atau retry yang menghasilkan log identik
- ✅ Untuk mengurangi duplikasi log

**Contoh:**
```powershell
# Skip log yang sama jika muncul dalam 3 detik terakhir
.\run_logcat_robust_v2.ps1 -DeduplicationWindow 3

# Skip log yang sama jika muncul dalam 10 detik terakhir (lebih ketat)
.\run_logcat_robust_v2.ps1 -DeduplicationWindow 10

# Default: 5 detik
.\run_logcat_robust_v2.ps1
```

**Tips:**
- **3 detik**: Untuk mengurangi log yang sangat cepat berulang
- **5 detik**: Default, balance yang baik
- **10+ detik**: Untuk menghapus log yang muncul dalam interval yang lebih lama

---

### **4. `-MinLogLevel` (String)**
**Default:** `"Debug"`

**Fungsi:** Hanya tampilkan log dengan level minimal yang ditentukan.

**Level yang tersedia (dari tertinggi ke terendah):**
1. **`"Error"`** - Hanya error
2. **`"Warning"`** - Error + Warning
3. **`"Info"`** - Error + Warning + Info
4. **`"Debug"`** - Semua level (default)

**Kapan digunakan:**
- ✅ Ketika hanya ingin melihat error/warning
- ✅ Untuk mengurangi log debug yang tidak penting
- ✅ Untuk monitoring production

**Contoh:**
```powershell
# Hanya tampilkan Error
.\run_logcat_robust_v2.ps1 -MinLogLevel "Error"

# Tampilkan Error + Warning
.\run_logcat_robust_v2.ps1 -MinLogLevel "Warning"

# Tampilkan Error + Warning + Info
.\run_logcat_robust_v2.ps1 -MinLogLevel "Info"

# Tampilkan semua (default)
.\run_logcat_robust_v2.ps1 -MinLogLevel "Debug"
```

---

## 🎯 **CONTOH KASUS PENGGUNAAN**

### **Kasus 1: Log Terlalu Banyak (Spam)**
**Masalah:** Log membanjiri console, sulit melihat log penting.

**Solusi:**
```powershell
# Kombinasi: Rate limiting + Deduplication + Log level
.\run_logcat_robust_v2.ps1 `
    -MaxLogsPerSecond 5 `
    -DeduplicationWindow 5 `
    -MinLogLevel "Warning"
```

**Hasil:** Hanya 5 log per detik, skip duplikasi, hanya error/warning.

---

### **Kasus 2: Log Berulang (Duplikasi)**
**Masalah:** Log yang sama muncul berkali-kali dalam beberapa detik.

**Solusi:**
```powershell
# Deduplication window lebih besar
.\run_logcat_robust_v2.ps1 -DeduplicationWindow 10
```

**Hasil:** Log yang sama akan diabaikan jika muncul dalam 10 detik terakhir.

---

### **Kasus 3: Hanya Perlu Error/Warning**
**Masalah:** Hanya ingin melihat error/warning, tidak perlu debug.

**Solusi:**
```powershell
# Silent mode + Log level Warning
.\run_logcat_robust_v2.ps1 -Silent -MinLogLevel "Warning"
```

**Hasil:** Hanya tampilkan error/warning penting, console lebih bersih.

---

### **Kasus 4: Production Monitoring**
**Masalah:** Monitoring production, perlu log yang clean dan fokus.

**Solusi:**
```powershell
# Kombinasi optimal untuk production
.\run_logcat_robust_v2.ps1 `
    -Silent `
    -MaxLogsPerSecond 5 `
    -DeduplicationWindow 10 `
    -MinLogLevel "Warning"
```

**Hasil:** 
- Hanya error/warning
- Maksimal 5 log/detik
- Skip duplikasi 10 detik
- Console sangat bersih

---

### **Kasus 5: Debugging Detail**
**Masalah:** Perlu semua log untuk debugging, tapi tetap ingin mengurangi spam.

**Solusi:**
```powershell
# Rate limiting saja, tetap tampilkan semua level
.\run_logcat_robust_v2.ps1 `
    -MaxLogsPerSecond 15 `
    -DeduplicationWindow 3
```

**Hasil:** Semua log level, tapi dibatasi 15 log/detik dan skip duplikasi 3 detik.

---

## 📊 **STATISTIK LOG**

Script akan menampilkan statistik setiap 1000 baris dan di akhir:

```
📊 Processed 1000 lines | Total: 1500 | Shown: 120 | Filtered: 800 | Deduped: 500 | Rate-limited: 80
```

**Penjelasan:**
- **Total**: Total log yang diproses
- **Shown**: Log yang ditampilkan di console
- **Filtered**: Log yang difilter berdasarkan level
- **Deduplicated**: Log yang diabaikan karena duplikasi
- **Rate-limited**: Log yang diabaikan karena rate limit

**Final Stats** di akhir script:
```
📊 Final Stats: Total processed: 5000 | Shown: 450 | Filtered: 2000 | Deduped: 2000 | Rate-limited: 550
```

---

## ⚙️ **CARA KERJA INTERNAL**

### **1. Rate Limiting**
- Menggunakan sliding window 1 detik
- Reset window setiap detik
- Log yang melebihi limit diabaikan (tapi tetap disimpan ke file)

### **2. Deduplication**
- Menggunakan hash dari log line
- Menyimpan timestamp terakhir setiap log
- Abaikan log yang muncul kembali dalam window time
- Auto-cleanup entries yang lebih tua dari 2x window

### **3. Silent Mode**
- Skip console output untuk log non-critical
- Tetap tampilkan error/warning/EOF/timeout
- Semua log tetap disimpan ke file

### **4. Log Level Filtering**
- Detect log level dari format logcat (E/W/I/D/V)
- Filter berdasarkan priority level
- Unknown level dianggap sebagai Info

---

## 💡 **TIPS & BEST PRACTICES**

### **✅ Untuk Development:**
```powershell
# Default settings - balance antara detail dan readability
.\run_logcat_robust_v2.ps1
```

### **✅ Untuk Production Monitoring:**
```powershell
# Silent mode + Warning level + Rate limiting
.\run_logcat_robust_v2.ps1 -Silent -MinLogLevel "Warning" -MaxLogsPerSecond 5
```

### **✅ Untuk Debugging Spesifik:**
```powershell
# Rate limiting lebih longgar, tetap semua level
.\run_logcat_robust_v2.ps1 -MaxLogsPerSecond 20 -DeduplicationWindow 3
```

### **✅ Untuk Mengatasi Log Berulang:**
```powershell
# Deduplication window lebih besar
.\run_logcat_robust_v2.ps1 -DeduplicationWindow 10
```

---

## 📝 **CATATAN PENTING**

1. **Semua log tetap disimpan ke file** - Filter hanya untuk console output
2. **File log tetap lengkap** - Bisa dibuka untuk analisis detail
3. **Rate limiting berbasis sliding window** - Reset setiap detik
4. **Deduplication menggunakan hash** - Log yang identik akan diabaikan
5. **Silent mode hanya hide console output** - Tidak menghapus log dari file

---

## 🎉 **KESIMPULAN**

Dengan fitur anti-spam log ini, Anda bisa:
- ✅ Mengurangi spam log di console
- ✅ Fokus pada log penting (error/warning)
- ✅ Mengurangi duplikasi log
- ✅ Mengontrol volume log per detik
- ✅ Tetap memiliki log lengkap di file untuk debugging

**Gunakan kombinasi parameter yang sesuai dengan kebutuhan Anda!** 🚀

