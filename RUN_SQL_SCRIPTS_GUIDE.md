# 🚀 Panduan Menjalankan Script SQL

## 📋 Urutan Menjalankan Script

### **1. Buka Supabase Dashboard**
- Buka browser dan login ke [Supabase Dashboard](https://supabase.com/dashboard)
- Pilih project `ngsuhouodaejwkqdxebk`
- Klik **SQL Editor** di sidebar kiri

### **2. Jalankan Script Berurutan**

#### **Step 1: Standardisasi Nama Tabel**
```sql
-- Copy dan paste script dari file: standardize_table_names.sql
-- Klik "Run" untuk menjalankan
```

#### **Step 2: Hapus Tabel Lama**
```sql
-- Copy dan paste script dari file: cleanup_old_tables_complete.sql
-- Klik "Run" untuk menjalankan
```

#### **Step 3: Migration Lengkap**
```sql
-- Copy dan paste script dari file: database_migration_complete_v8.sql
-- Klik "Run" untuk menjalankan
```

#### **Step 4: Verifikasi Hasil**
```sql
-- Copy dan paste script dari file: verify_remaining_tables.sql
-- Klik "Run" untuk menjalankan
```

## ⚠️ Peringatan Penting

1. **BACKUP DATA** sebelum menjalankan script
2. **Jalankan di development** terlebih dahulu jika memungkinkan
3. **Monitor log** untuk error
4. **Jangan skip step** - jalankan berurutan

## 🔍 Cara Backup Data

Sebelum menjalankan script, backup data penting:

```sql
-- Backup tbl_resi
SELECT * FROM tbl_resi;

-- Backup tbl_expedisi  
SELECT * FROM tbl_expedisi;

-- Backup picklist (sebelum di-rename)
SELECT * FROM picklist;

-- Backup picklist_scan (sebelum di-rename)
SELECT * FROM picklist_scan;
```

## 📊 Verifikasi Setelah Script

Setelah semua script dijalankan, pastikan:

1. ✅ **Tabel lama sudah dihapus**:
   - `barcode_scan` ❌
   - `barcode_session` ❌
   - `barcode_performance` ❌

2. ✅ **Tabel baru sudah ada**:
   - `tbl_resi` ✅
   - `tbl_expedisi` ✅
   - `picklist_items` ✅ (dari `picklist`)
   - `picklist_scans` ✅ (dari `picklist_scan`)

3. ✅ **Data tidak hilang**:
   - Cek jumlah record sebelum dan sesudah
   - Verifikasi data penting masih ada

## 🆘 Troubleshooting

Jika ada error:

1. **Cek log error** di Supabase SQL Editor
2. **Verifikasi permissions** user
3. **Restore dari backup** jika diperlukan
4. **Contact admin** untuk bantuan

## 📝 Log Progress

Catat progress di sini:

- [ ] Step 1: Standardisasi nama tabel
- [ ] Step 2: Hapus tabel lama  
- [ ] Step 3: Migration lengkap
- [ ] Step 4: Verifikasi hasil
- [ ] Test aplikasi

---

**⚠️ IMPORTANT**: Jalankan script dengan hati-hati dan pastikan backup data sudah dibuat!

