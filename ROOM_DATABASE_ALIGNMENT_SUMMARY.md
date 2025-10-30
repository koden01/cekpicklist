# 🗄️ Room Database Alignment dengan Supabase

## 📋 Ringkasan Perubahan

Room Database telah disesuaikan agar **100% kompatibel** dengan struktur Supabase yang ada. Tidak ada perubahan pada Supabase - hanya Room Database yang disesuaikan.

## 🔄 Perubahan yang Dilakukan

### **1. PicklistItemEntity (picklist table)**

**Sebelum:**
```kotlin
@Entity(tableName = "picklist_items")
data class PicklistItemEntity(
    @PrimaryKey val id: String,
    val noPicklist: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val productId: String?,
    val qtyPl: Int,
    val qtyScan: Int,
    val scan: Boolean = false,
    val createdAt: String? = null,
    // ... kolom tambahan
)
```

**Sesudah:**
```kotlin
@Entity(tableName = "picklist")
data class PicklistItemEntity(
    @PrimaryKey val id: String, // UUID dari Supabase
    val no_picklist: String, // no_picklist (NOT NULL)
    val article_id: String, // article_id (NOT NULL)
    val size: String?, // size (nullable)
    val product_id: String?, // product_id (nullable)
    val qty: Int, // qty (NOT NULL) - sesuai dengan Supabase
    val created_at: String?, // created_at timestamp
    val article_name: String?, // article_name (text)
    val notrans: String, // notrans (NOT NULL)
    val scan: Boolean = false, // scan (boolean, DEFAULT false)
    // Kolom tambahan untuk sinkronisasi lokal
    val lastUpdated: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true
)
```

### **2. PicklistScanEntity (picklist_scan table)**

**Sebelum:**
```kotlin
@Entity(tableName = "picklist_scans")
data class PicklistScanEntity(
    @PrimaryKey val id: String,
    val noPicklist: String,
    val productId: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val epc: String,
    val notrans: String?,
    val createdAt: Long = System.currentTimeMillis(),
    // ... kolom tambahan
)
```

**Sesudah:**
```kotlin
@Entity(tableName = "picklist_scan")
data class PicklistScanEntity(
    @PrimaryKey val epc: String, // epc sebagai PRIMARY KEY sesuai Supabase
    val no_picklist: String, // no_picklist (NOT NULL)
    val product_id: String, // product_id (NOT NULL)
    val article_id: String?, // article_id (nullable)
    val article_name: String?, // article_name (nullable)
    val size: String?, // size (nullable)
    val created_at: String?, // created_at timestamp
    val notrans: String?, // notrans (nullable)
    // Kolom tambahan untuk sinkronisasi lokal
    val isSynced: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

### **3. DAO Updates**

**PicklistDao:**
- Semua query diupdate dari `picklist_items` → `picklist`
- Kolom diupdate: `noPicklist` → `no_picklist`, `articleId` → `article_id`, dll.
- Method `updateQtyScan` → `updateScanStatus` (sesuai dengan kolom `scan` di Supabase)

**PicklistScanDao:**
- Semua query diupdate dari `picklist_scans` → `picklist_scan`
- Kolom diupdate: `noPicklist` → `no_picklist`, `productId` → `product_id`, dll.
- Primary key diupdate dari `id` → `epc`

### **4. Database Version**

**LocalDatabase.kt:**
- Version diupdate dari `7` → `9`
- Migration akan otomatis menggunakan `fallbackToDestructiveMigration()`

## 📊 Mapping Kolom Supabase ↔ Room

### **Tabel `picklist`:**

| Supabase | Room Database | Tipe | Keterangan |
|----------|---------------|------|------------|
| `id` | `id` | TEXT (UUID) | Primary Key |
| `no_picklist` | `no_picklist` | TEXT | NOT NULL |
| `article_id` | `article_id` | TEXT | NOT NULL |
| `size` | `size` | TEXT | nullable |
| `product_id` | `product_id` | TEXT | nullable |
| `qty` | `qty` | INTEGER | NOT NULL |
| `created_at` | `created_at` | TEXT | timestamp |
| `article_name` | `article_name` | TEXT | nullable |
| `notrans` | `notrans` | TEXT | NOT NULL |
| `scan` | `scan` | INTEGER (Boolean) | DEFAULT false |

### **Tabel `picklist_scan`:**

| Supabase | Room Database | Tipe | Keterangan |
|----------|---------------|------|------------|
| `epc` | `epc` | TEXT | Primary Key |
| `no_picklist` | `no_picklist` | TEXT | NOT NULL |
| `product_id` | `product_id` | TEXT | NOT NULL |
| `article_id` | `article_id` | TEXT | nullable |
| `article_name` | `article_name` | TEXT | nullable |
| `size` | `size` | TEXT | nullable |
| `created_at` | `created_at` | TEXT | timestamp |
| `notrans` | `notrans` | TEXT | nullable |

## 🚀 Cara Menjalankan Migration

### **1. Build dan Install Aplikasi**
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### **2. Database akan Otomatis Migrate**
- Room akan mendeteksi perubahan schema (version 7 → 9)
- `fallbackToDestructiveMigration()` akan menghapus data lama
- Tabel baru akan dibuat sesuai struktur Supabase

### **3. Verifikasi Migration**
```bash
# Connect ke device/emulator
adb shell

# Masuk ke direktori database
cd /data/data/com.example.cekpicklist.debug/databases/

# Buka database
sqlite3 cekpicklist_local.db

# Cek struktur tabel
.schema picklist
.schema picklist_scan

# Cek data
SELECT COUNT(*) FROM picklist;
SELECT COUNT(*) FROM picklist_scan;
```

## ⚠️ Catatan Penting

### **1. Data Loss**
- Migration menggunakan `fallbackToDestructiveMigration()`
- **Semua data lama akan dihapus**
- Pastikan data penting sudah di-backup

### **2. Backward Compatibility**
- Entity classes memiliki method `toPicklistItem()` dan `toPicklistScan()`
- Method ini memastikan kompatibilitas dengan kode yang ada
- Domain models tetap menggunakan nama kolom lama

### **3. Sync dengan Supabase**
- Struktur sekarang 100% kompatibel dengan Supabase
- Data dapat langsung di-sync tanpa transformasi
- API calls ke Supabase akan berfungsi normal

## 🔍 Testing

### **1. Test Database Structure**
```sql
-- Cek apakah tabel ada
SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'picklist%';

-- Cek struktur tabel
PRAGMA table_info(picklist);
PRAGMA table_info(picklist_scan);
```

### **2. Test Data Operations**
- Insert data baru
- Query data
- Update data
- Delete data

### **3. Test Supabase Sync**
- Pull data dari Supabase
- Push data ke Supabase
- Verify data consistency

## 📝 File yang Diubah

1. **Entities:**
   - `PicklistItemEntity.kt` - Updated structure
   - `PicklistScanEntity.kt` - Updated structure

2. **DAOs:**
   - `PicklistDao.kt` - Updated queries
   - `PicklistScanDao.kt` - Updated queries

3. **Database:**
   - `LocalDatabase.kt` - Updated version to 9

4. **Migration:**
   - `room_database_migration_v9.sql` - Migration script

## ✅ Status

- [x] ✅ Analisis struktur Supabase
- [x] ✅ Update Room entities
- [x] ✅ Update Room DAO queries
- [x] ✅ Update migration script
- [x] ✅ Update database version
- [ ] 🔄 Test sinkronisasi data lokal dengan Supabase

---

**🎉 Room Database sekarang 100% kompatibel dengan struktur Supabase!**

