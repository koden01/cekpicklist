# Cek Picklist - RFID Scanning Application

Aplikasi Android untuk scanning RFID dalam proses picklist dengan integrasi Supabase dan Nirwana API.

## 📋 Overview

Aplikasi ini digunakan untuk:
- **Scanning RFID** barang dalam proses picklist
- **Validasi quantity** sesuai dengan rencana picklist
- **Auto-save** data EPC ke Supabase secara real-time
- **Deteksi over-scan** dan item NA (Not Available)
- **Manajemen cache** untuk performa optimal

## 🏗️ Arsitektur

### Komponen Utama

```
📱 UI Layer
├── MainActivity - Halaman utama scanning
├── PicklistInputActivity - Input nomor picklist
├── SettingsActivity - Konfigurasi aplikasi
└── Adapters - RecyclerView adapters

🧠 Business Logic
├── ScanViewModel - ViewModel untuk scanning
├── Repository - Data access layer
└── CacheManager - Manajemen cache lokal

🌐 Network Layer
├── SupabaseService - API Supabase
├── SupabaseRealtimeService - Realtime updates
└── NirwanaApiService - API Nirwana untuk data produk

📊 Data Models
├── PicklistItem - Model item picklist
├── ScanResult - Hasil scanning RFID
└── PicklistScan - Data scan untuk database
```

## 🔧 Teknologi yang Digunakan

- **Android SDK 30+** dengan Kotlin
- **Architecture Components** (ViewModel, LiveData)
- **Retrofit** untuk networking
- **Supabase** untuk database dan realtime
- **Coroutines** untuk async operations
- **RFID SDK** untuk scanning hardware

## 📦 Dependencies

```kotlin
// Core Android
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.7.1")
implementation("com.google.android.material:material:1.12.0")

// Architecture Components
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Supabase
implementation("io.github.jan-tennert.supabase:realtime-kt:2.3.0")
implementation("io.github.jan-tennert.supabase:postgrest-kt:2.3.0")

// RFID Hardware
implementation(files("libs/DeviceAPI_ver20250209_release.aar"))
```

## 🚀 Fitur Utama

### 1. **RFID Scanning**
- Auto-scan setiap detik
- Deteksi duplikat EPC
- Buffer EPC untuk validasi

### 2. **Validasi Quantity**
- ✅ **GREEN**: qtyScan = qtyPl (Sesuai)
- 🔴 **RED**: qtyScan < qtyPl (Kurang)
- 🟡 **YELLOW**: qtyScan > qtyPl (Lebih) atau tidak ada di picklist

### 3. **Auto-Save ke Supabase**
- EPC tersimpan otomatis setiap detik
- Hanya EPC valid yang disimpan (tidak over-scan)
- Realtime sync dengan database

### 4. **Cache Management**
- Cache lokal untuk performa optimal
- Smart stale time untuk data freshness
- Offline capability

### 5. **Error Handling**
- Deteksi item NA (Not Available)
- Penanganan over-scan
- Swipe-to-delete untuk koreksi

## 📊 Database Schema

### Tabel `picklist`
```sql
CREATE TABLE picklist (
    id UUID PRIMARY KEY,
    no_picklist VARCHAR,
    article_id VARCHAR,
    article_name VARCHAR,
    size VARCHAR,
    product_id VARCHAR,
    qty INTEGER,
    created_at TIMESTAMP
);
```

### Tabel `picklist_scan`
```sql
CREATE TABLE picklist_scan (
    id UUID PRIMARY KEY,
    no_picklist VARCHAR,
    product_id VARCHAR,
    article_id VARCHAR,
    article_name VARCHAR,
    size VARCHAR,
    epc VARCHAR,
    notrans VARCHAR,
    created_at TIMESTAMP
);
```

## 🔄 Alur Kerja

### 1. **Load Picklist**
```
User Input → PicklistInputActivity → Repository → Supabase API → Cache → UI
```

### 2. **RFID Scanning**
```
RFID Hardware → addEpc() → Buffer → Auto-timer → Nirwana API → Validation → Supabase
```

### 3. **Data Validation**
```
EPC Scan → Product Info → Quantity Check → Save/Reject → UI Update
```

## ⚙️ Konfigurasi

### Supabase Configuration
```kotlin
// Di SupabaseService.kt
private const val SUPABASE_URL = "your-supabase-url"
private const val SUPABASE_ANON_KEY = "your-anon-key"
```

### Nirwana API Configuration
```kotlin
// Di NirwanaApiService.kt
private const val NIRWANA_BASE_URL = "your-nirwana-api-url"
private const val NIRWANA_USERNAME = "your-username"
private const val NIRWANA_PASSWORD = "your-password"
```

## 🐛 Troubleshooting

### Masalah Umum

1. **RFID tidak terdeteksi**
   - Cek koneksi hardware
   - Restart aplikasi
   - Cek power level RFID

2. **Data tidak tersimpan**
   - Cek koneksi internet
   - Cek konfigurasi Supabase
   - Lihat log error

3. **Over-scan tidak terdeteksi**
   - Cek validasi quantity
   - Reset qty scan dan scan ulang

### Log Debugging
```kotlin
// Enable debug logging
Log.d("ScanViewModel", "🔥 Debug message")
```

## 📱 Screenshots

### Main Screen
- List picklist items
- RFID counter
- Summary cards (Total, Scanned, Remaining)

### Picklist Input
- Input nomor picklist
- List picklist tersedia
- Load data dari Supabase

### Settings
- Konfigurasi Supabase
- Konfigurasi Nirwana API
- Realtime connection status

## 🔒 Security

- API keys disimpan di local properties
- HTTPS untuk semua komunikasi
- Input validation untuk mencegah injection

## 📈 Performance

- **Cache Strategy**: Smart stale time
- **Background Processing**: Coroutines
- **Memory Management**: ViewModel lifecycle
- **Network Optimization**: Retrofit caching

## 🚀 Deployment

### Build Release
```bash
./gradlew assembleRelease
```

### Install APK
```bash
adb install app-release.apk
```

## 🤝 Contributing

1. Fork repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

## 📄 License

Proprietary - Internal Use Only

## 📞 Support

Untuk bantuan teknis, hubungi tim development.

---

**Version**: 1.0  
**Last Updated**: 2025  
**Platform**: Android 11+ (API 30+)