# 📱 Cek Picklist - RFID Scanning Application

Aplikasi Android untuk scanning RFID dalam proses picklist dengan integrasi Supabase dan Nirwana API.

> 📚 **Dokumentasi Lengkap**: Lihat [DOCUMENTATION.md](DOCUMENTATION.md) untuk informasi detail dan komprehensif.

## 📋 Overview

Aplikasi ini digunakan untuk:
- **RFID Scanning** barang dalam proses picklist dengan auto-scan setiap detik
- **Validasi quantity** sesuai dengan rencana picklist dengan visual status
- **Auto-save** data EPC ke Supabase secara real-time
- **Deteksi over-scan** dan item NA (Not Available)
- **Manajemen cache** untuk performa optimal dengan smart stale time
- **Sound feedback** untuk setiap scanning dengan audio confirmation
- **Auto versioning** dengan GitHub Actions untuk update otomatis

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

- **Android SDK 30+** dengan Kotlin dan Java 17
- **Architecture Components** (ViewModel, LiveData, ViewBinding)
- **Retrofit** untuk networking dengan OkHttp
- **Supabase** untuk database dan realtime dengan WebSocket
- **Coroutines** untuk async operations
- **RFID SDK** untuk scanning hardware
- **GitHub Actions** untuk otomatisasi versi
- **Material Design** untuk UI modern

## 📦 Dependencies

```kotlin
// Core Android
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.7.1")
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.activity:activity-ktx:1.8.2")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")

// Architecture Components
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

// UI Components
implementation("androidx.recyclerview:recyclerview:1.3.2")
implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

// Supabase
implementation("io.github.jan-tennert.supabase:realtime-kt:2.3.0")
implementation("io.github.jan-tennert.supabase:postgrest-kt:2.3.0")
implementation("io.ktor:ktor-client-websockets:2.3.7")
implementation("io.ktor:ktor-client-cio:2.3.7")

// RFID Hardware
implementation(files("libs/DeviceAPI_ver20250209_release.aar"))
```

## 🚀 Fitur Utama

### 1. **RFID Scanning System**
- Auto-scan setiap detik dengan timer
- Deteksi duplikat EPC dengan buffer management
- Sound feedback untuk setiap scanning
- Hardware integration dengan proper error handling

### 2. **Quantity Validation System**
- ✅ **GREEN**: qtyScan = qtyPl (Sesuai dengan rencana)
- 🔴 **RED**: qtyScan < qtyPl (Kurang dari rencana)
- 🟡 **YELLOW**: qtyScan > qtyPl (Lebih dari rencana) atau tidak ada di picklist
- **NA Detection**: Deteksi item Not Available dengan informasi khusus

### 3. **Real-time Data Synchronization**
- EPC tersimpan otomatis ke Supabase setiap detik
- Realtime sync dengan database menggunakan WebSocket
- Connection status indicator di toolbar
- Offline capability dengan cache lokal

### 4. **Advanced Cache Management**
- Memory cache untuk performa optimal
- Smart stale time (5 menit) untuk data freshness
- Cache invalidation otomatis
- Prefetch strategy untuk performa

### 5. **User Experience Features**
- **Swipe Actions**: Swipe-to-delete untuk koreksi data
- **Completion Animation**: Confetti animation saat picklist selesai
- **Search & Filter**: Real-time search dengan filtering
- **Version Display**: Auto-updating version display di input screen

### 6. **Auto Versioning System**
- **GitHub Actions**: Otomatisasi update versi saat push
- **Manual Updates**: Update manual via GitHub UI
- **Version Display**: Otomatis terupdate di aplikasi

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

### Environment Variables
```properties
# local.properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
NIRWANA_BASE_URL=https://api.nirwana.com
NIRWANA_USERNAME=your-username
NIRWANA_PASSWORD=your-password
```

### Build Configuration
```kotlin
android {
    compileSdk = 35
    minSdk = 30
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
    
    buildFeatures {
        viewBinding = true
    }
}
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

## 📱 UI Features

### Main Screen
- List picklist items dengan swipe-to-delete
- RFID counter dengan sound feedback
- Summary cards (Total, Scanned, Remaining)
- Real-time status indicator (WiFi connection)
- Completion animation dengan confetti

### Picklist Input
- Input nomor picklist dengan search functionality
- List picklist tersedia dengan status icons
- Version display yang auto-updating
- Status indicators untuk data loading

### Settings
- Konfigurasi Supabase dan Nirwana API
- Realtime connection status
- App configuration management

## 🔒 Security

- API keys disimpan di local properties
- HTTPS untuk semua komunikasi
- Input validation untuk mencegah injection

## 📈 Performance & Optimization

- **Cache Strategy**: Smart stale time (5 menit) dengan memory cache
- **Background Processing**: Coroutines untuk async operations
- **Memory Management**: ViewModel lifecycle dengan proper cleanup
- **Network Optimization**: Retrofit dengan connection pooling
- **UI Optimization**: ViewBinding, RecyclerView dengan ViewHolder pattern
- **Sound Management**: SoundPool untuk efficient audio resources

## 🚀 Deployment & Automation

### Build Release
```bash
./gradlew assembleRelease
```

### Install APK
```bash
adb install app-release.apk
```

### Auto Versioning
- **Automatic**: Push ke GitHub → Auto update versi
- **Manual**: GitHub Actions → Manual version update
- **Scripts**: `scripts/update_version.py` untuk local update

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

## 📚 Dokumentasi Lengkap

Untuk informasi detail dan komprehensif tentang:
- Arsitektur aplikasi lengkap
- Database schema detail
- Design system & UI guidelines
- Performance optimization
- Debugging & logging system
- Troubleshooting guide

Lihat **[DOCUMENTATION.md](DOCUMENTATION.md)** 📖

---

**Version**: 1.0 (Auto-updating)  
**Last Updated**: 2025  
**Platform**: Android 11+ (API 30+)  
**Auto Versioning**: ✅ Enabled dengan GitHub Actions