# 🗑️ Cleanup Old Barcode Tables

## 📋 Overview

Script ini digunakan untuk menghapus tabel lama yang sudah tidak digunakan di Supabase:
- `barcode_scan` - Tabel lama untuk barcode scan data
- `barcode_session` - Tabel lama untuk barcode session data
- `barcode_performance` - Tabel lama untuk performance data

## 🎯 Tujuan

1. **Membersihkan database** dari tabel yang tidak digunakan
2. **Menyederhanakan struktur** database
3. **Menghindari konflik** dengan tabel baru
4. **Mengurangi ukuran** database

## 📊 Tabel yang Dihapus

### ❌ Tabel Lama (Dihapus):
- `barcode_scan` - Data barcode scan lama
- `barcode_session` - Data session barcode lama  
- `barcode_performance` - Data performance barcode lama

### ✅ Tabel yang Tetap Ada:
- `tbl_resi` - Data barcode scan (tabel baru)
- `tbl_expedisi` - Data expedisi dengan flag (tabel baru)
- `picklist` - Data picklist
- `picklist_scan` - Data scan RFID

## 🚀 Cara Menjalankan

### 1. **Cleanup Tabel Lama**
```sql
-- Jalankan di Supabase SQL Editor
\i cleanup_old_barcode_tables.sql
```

### 2. **Verifikasi Hasil**
```sql
-- Jalankan di Supabase SQL Editor
\i verify_remaining_tables.sql
```

## ⚠️ Peringatan

- **BACKUP DATA** sebelum menjalankan script
- **Pastikan tidak ada aplikasi** yang masih menggunakan tabel lama
- **Test di environment development** terlebih dahulu

## 🔍 Verifikasi

Setelah cleanup, pastikan:

1. ✅ Tabel lama sudah dihapus
2. ✅ Tabel baru masih ada
3. ✅ Data penting tidak hilang
4. ✅ Aplikasi masih berfungsi normal

## 📝 Log

- **Tanggal**: 2025-10-25
- **Alasan**: Cleanup tabel lama yang tidak digunakan
- **Status**: Ready to execute

## 🆘 Troubleshooting

Jika ada masalah:

1. **Cek log error** di Supabase
2. **Verifikasi permissions** user
3. **Restore dari backup** jika diperlukan
4. **Contact admin** untuk bantuan

---

**⚠️ IMPORTANT**: Jalankan script ini dengan hati-hati dan pastikan backup data sudah dibuat!

