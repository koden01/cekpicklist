# ðŸ“± CEK PICKLIST - RFID Scanning Application

## Dokumentasi Terpadu (Aplikasi + Signing)

### Keystore (Release)
- Key alias: `cekpicklist`
- Keystore file: `cekpicklist-release-key.keystore` (root repo)
- Keystore password: `CekPicklist#2025`
- Key password: `CekPicklist#2025`

### Build Release (Signed)
1) Build unsigned:
```
./gradlew assembleRelease -x test --no-daemon
```
2) Zipalign dan Sign:
```
"%ANDROID_HOME%/build-tools/35.0.1/zipalign" -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/release/app-release-aligned.apk
"%ANDROID_HOME%/build-tools/35.0.1/apksigner" sign --ks cekpicklist-release-key.keystore --ks-key-alias cekpicklist --ks-pass pass:CekPicklist#2025 --key-pass pass:CekPicklist#2025 --out app/build/outputs/apk/release/CekPicklist-v1.0-signed.apk app/build/outputs/apk/release/app-release-aligned.apk
```
3) Verifikasi:
```
"%ANDROID_HOME%/build-tools/35.0.1/apksigner" verify --print-certs app/build/outputs/apk/release/CekPicklist-v1.0-signed.apk
```

### Signing Otomatis di Gradle
Konfigurasi sudah diaktifkan di `app/build.gradle.kts`:
```
android {
    signingConfigs {
        create("release") {
            storeFile = file("../cekpicklist-release-key.keystore")
            storePassword = "CekPicklist#2025"
            keyAlias = "cekpicklist"
            keyPassword = "CekPicklist#2025"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

## Relocation Layout & Flow (Terbaru)

Struktur layar Relocation:

```
LinearLayout (vertical)
â”œâ”€â”€ Toolbar (static)
â”œâ”€â”€ Status Card (static)
â””â”€â”€ NestedScrollView (scrollable content)
    â””â”€â”€ LinearLayout
        â”œâ”€â”€ Current Location Card
        â”œâ”€â”€ Target Location Card
        â”œâ”€â”€ Buttons
        â””â”€â”€ RecyclerView (height=560dp, nested scroll)
```

Komponen kunci:
- `activity_relocation.xml` menempatkan Status Card di luar area scroll agar selalu terlihat.
- `RecyclerView`: `layout_height=560dp`, `nestedScrollingEnabled=true`.
- `RelocationActivity.kt` menginisialisasi `LinearLayoutManager` dan `RelocationAdapter`, serta mengobservasi `relocationItems` dari `RelocationViewModel`.

## Signing APK (Release)

APK release disiapkan dengan keystore lokal berikut untuk mempermudah build berikutnya.

- Key alias: `cekpicklist`
- Keystore file: `cekpicklist-release-key.keystore` (root repository)
- Keystore password: `CekPicklist#2025`
- Key password: `CekPicklist#2025`

### Build Cepat (Signed)

1) Build unsigned release:
```
./gradlew assembleRelease -x test --no-daemon
```

2) Zipalign lalu sign:
```
"%ANDROID_HOME%/build-tools/35.0.1/zipalign" -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/release/app-release-aligned.apk
"%ANDROID_HOME%/build-tools/35.0.1/apksigner" sign --ks cekpicklist-release-key.keystore --ks-key-alias cekpicklist --ks-pass pass:CekPicklist#2025 --key-pass pass:CekPicklist#2025 --out app/build/outputs/apk/release/CekPicklist-v1.0-signed.apk app/build/outputs/apk/release/app-release-aligned.apk
```

3) Verifikasi signature:
```
"%ANDROID_HOME%/build-tools/35.0.1/apksigner" verify --print-certs app/build/outputs/apk/release/CekPicklist-v1.0-signed.apk
```

Output signed APK: `app/build/outputs/apk/release/CekPicklist-v1.0-signed.apk`

## Enable Signing Otomatis (Opsional)

Aktifkan konfigurasi berikut di `app/build.gradle.kts` untuk sign otomatis saat `assembleRelease`:

```
android {
    signingConfigs {
        create("release") {
            storeFile = file("../cekpicklist-release-key.keystore")
            storePassword = "CekPicklist#2025"
            keyAlias = "cekpicklist"
            keyPassword = "CekPicklist#2025"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

## ðŸ“‹ **OVERVIEW APLIKASI**

**Cek Picklist** adalah aplikasi Android untuk scanning RFID dalam proses picklist dengan integrasi Supabase dan Nirwana API. Aplikasi ini dirancang untuk memudahkan proses inventory management dengan teknologi RFID scanning yang real-time.

### ðŸŽ¯ **Tujuan Utama**
- **RFID Scanning**: Scanning barang menggunakan RFID hardware dengan auto-scan setiap detik
- **Validasi Quantity**: Memastikan jumlah scan sesuai dengan rencana picklist dengan sistem warna
- **Auto-Save**: Menyimpan data EPC ke Supabase secara real-time
- **Cache Overscan Prevention**: Mencegah overscan di level cache dengan validasi real-time
- **Hide Completed Items**: Items yang sudah complete disembunyikan dari UI
- **Sound Feedback**: Audio confirmation untuk setiap scanning

### ðŸ† **Fitur Unggulan**
- **Real-time Sync**: Sinkronisasi data dengan Supabase secara real-time
- **Visual Status System**: Indikator warna untuk status quantity (Hijau/Merah/Kuning)
- **Hide Completed Items**: Items yang sudah complete disembunyikan dari UI
- **Swipe Actions**: Swipe-to-delete untuk koreksi data
- **Auto Versioning**: Sistem otomatisasi versi dengan GitHub Actions
- **Settings Integration**: Settings yang konsisten antara MainActivity dan PicklistInputActivity

---

## ðŸ—ï¸ **ARSITEKTUR APLIKASI**

### **MVVM Pattern Implementation**
```
ðŸ“± View Layer (Activities/Fragments)
    â†•ï¸ Data Binding
ðŸ§  ViewModel Layer (Business Logic)
    â†•ï¸ LiveData/Observable
ðŸ“Š Repository Layer (Data Access)
    â†•ï¸ API Calls
ðŸŒ Network Layer (API Services)
    â†•ï¸ HTTP/WebSocket
ðŸ—„ï¸ Data Sources (Supabase/Nirwana)
```

### **Package Structure**
```
com.example.cekpicklist/
â”œâ”€â”€ ðŸ“± Activities
â”‚   â”œâ”€â”€ MainActivity.kt                 # Halaman utama scanning RFID
â”‚   â”œâ”€â”€ PicklistInputActivity.kt        # Input/selection nomor picklist
â”‚   â””â”€â”€ SettingsActivity.kt             # Konfigurasi aplikasi
â”œâ”€â”€ ðŸ”„ Adapters
â”‚   â”œâ”€â”€ PicklistAdapter.kt              # Adapter untuk list picklist items
â”‚   â””â”€â”€ PicklistSelectionAdapter.kt    # Adapter untuk dialog selection
â”œâ”€â”€ ðŸŒ API Services
â”‚   â”œâ”€â”€ SupabaseService.kt              # API Supabase untuk database
â”‚   â”œâ”€â”€ SupabaseRealtimeService.kt      # Realtime updates dari Supabase
â”‚   â””â”€â”€ NirwanaApiService.kt            # API Nirwana untuk data produk
â”œâ”€â”€ ðŸ’¾ Cache Management
â”‚   â”œâ”€â”€ CacheManager.kt                 # Manajemen cache lokal
â”‚   â””â”€â”€ PicklistScanCache.kt            # Cache untuk scan data
â”œâ”€â”€ ðŸ“Š Data Models
â”‚   â”œâ”€â”€ PicklistItem.kt                 # Model item picklist
â”‚   â”œâ”€â”€ PicklistStatus.kt               # Status picklist
â”‚   â””â”€â”€ ScanResult.kt                   # Hasil scanning RFID
â”œâ”€â”€ ðŸª Repository
â”‚   â””â”€â”€ Repository.kt                   # Data access layer
â”œâ”€â”€ ðŸ› ï¸ Utils
â”‚   â”œâ”€â”€ Logger.kt                       # Logging system
â”‚   â””â”€â”€ ToastUtils.kt                   # Toast notifications
â”œâ”€â”€ ðŸ§  ViewModel
â”‚   â”œâ”€â”€ ScanViewModel.kt                # ViewModel untuk scanning
â”‚   â””â”€â”€ ScanViewModelFactory.kt         # Factory untuk ViewModel
â””â”€â”€ ðŸš€ Application
    â””â”€â”€ CekPicklistApplication.kt       # Application class
```

---

## ðŸ”§ **TEKNOLOGI & DEPENDENCIES**

### **Core Technologies**
- **Android SDK**: 30+ (Android 11+)
- **Kotlin**: Modern Android development dengan coroutines
- **Java Version**: 17
- **Target SDK**: 35 (Android terbaru)

### **Architecture Components**
```kotlin
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.7.1")
implementation("com.google.android.material:material:1.12.0")
```

### **Networking & API Integration**
```kotlin
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("io.github.jan-tennert.supabase:realtime-kt:2.3.0")
implementation("io.github.jan-tennert.supabase:postgrest-kt:2.3.0")
```

### **Hardware Integration**
```kotlin
implementation(files("libs/DeviceAPI_ver20250209_release.aar"))
```

---

## ðŸ“Š **DATABASE SCHEMA**

### **Tabel `picklist` (Master Data)**
```sql
CREATE TABLE picklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no_picklist VARCHAR NOT NULL,
    article_id VARCHAR NOT NULL,
    article_name VARCHAR NOT NULL,
    size VARCHAR,
    product_id VARCHAR NOT NULL,
    qty INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### **Tabel `picklist_scan` (Transaction Data)**
```sql
CREATE TABLE picklist_scan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    no_picklist VARCHAR NOT NULL,
    product_id VARCHAR NOT NULL,
    article_id VARCHAR NOT NULL,
    article_name VARCHAR NOT NULL,
    size VARCHAR,
    epc VARCHAR NOT NULL,
    notrans VARCHAR,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

---

## ðŸš€ **FITUR UTAMA APLIKASI**

### **1. RFID Scanning System**
- **Auto-scan**: Scanning otomatis setiap detik dengan timer
- **EPC Buffer**: Menyimpan EPC dalam buffer untuk validasi
- **Duplicate Detection**: Mencegah scanning duplikat dengan EPC tracking
- **Sound Feedback**: Audio confirmation untuk setiap scanning

### **2. Quantity Validation System dengan Sistem Warna**
- **ðŸ”´ RED**: `qtyScan < qtyPl` (Kurang dari rencana picklist)
- **ðŸŸ¡ YELLOW**: `qtyScan > qtyPl` (Lebih dari rencana) atau item tidak ada di picklist
- **ðŸŸ¢ GREEN**: `qtyScan == qtyPl` (Sesuai dengan rencana - disembunyikan dari UI)

### **3. Hide Completed Items System**
- **Filter Items**: Items yang sudah complete (`qtyScan == qtyPl`) disembunyikan dari UI
- **Clean UI**: Hanya menampilkan items yang perlu di-scan
- **Completion Message**: Pesan khusus saat semua items sudah complete

### **4. Cache Overscan Prevention System**
- **Real-time Validation**: Cache memvalidasi overscan sebelum data disimpan
- **Prevention First**: Mencegah overscan di level cache, bukan hanya menghitungnya
- **Data Integrity**: Cache hanya menyimpan data yang valid
- **Back Button Behavior**: Saat tombol back ditekan, data overscan dan non-picklist akan DIBUANG, tidak disimpan ke Supabase

### **5. Real-time Data Synchronization**
- **Auto-save**: EPC tersimpan otomatis ke Supabase setiap detik
- **Realtime Updates**: Sinkronisasi real-time dengan database menggunakan WebSocket
- **Connection Status**: Indikator koneksi WiFi di toolbar

### **6. Picklist Selection System**
- **Status Icons**: Arrow (â¬‡ï¸) untuk belum scan, Check (âœ…) untuk sudah scan
- **Detail Status**: 
  - "ðŸ“‹ Belum scan sama sekali" untuk picklist yang belum pernah di-scan
  - "âœ… Selesai" untuk picklist yang sudah selesai
  - "âš ï¸ Sisa X qty" untuk picklist yang masih ada sisa
- **Search & Filter**: Pencarian picklist dengan filter real-time

### **7. Settings Integration**
- **Consistent Settings**: Settings yang sama antara MainActivity dan PicklistInputActivity
- **RFID Configuration**: Power Level dan RSSI Threshold settings
- **Navigation**: Settings icon mengarah ke SettingsActivity yang sama

### **8. Back Button Behavior System**
- **Data Filtering**: Saat tombol back ditekan, sistem akan memfilter data sebelum menyimpan ke Supabase
- **Overscan Handling**: Data dengan qty scan > qty PL akan DIBUANG, tidak disimpan
- **Non-picklist Handling**: Data yang tidak ada di picklist akan DIBUANG, tidak disimpan
- **Valid Data Only**: Hanya data yang valid (qty scan â‰¤ qty PL) yang akan disimpan ke Supabase
- **User Confirmation**: Dialog konfirmasi ditampilkan sebelum keluar dari scanning

#### **Contoh Skenario Back Button:**
- **Article A**: qty PL = 5, qty scan = 7 â†’ **7 RFID DIBUANG** (overscan)
- **Article B**: qty PL = 5, qty scan = 4 â†’ **4 RFID DISIMPAN** (valid)
- **Article C**: qty PL = 0, qty scan = 3 â†’ **3 RFID DIBUANG** (non-picklist)

---

## ðŸ”„ **ALUR KERJA APLIKASI**

### **1. Application Startup Flow**
```
Launch App â†’ PicklistInputActivity â†’ Load Picklists â†’ 
Select Picklist â†’ Navigate to MainActivity â†’ Initialize RFID â†’ Start Scanning
```

### **2. RFID Scanning Flow**
```
RFID Hardware â†’ EPC Detection â†’ Add to Buffer â†’ 
Validation Timer â†’ Nirwana API Lookup â†’ Product Info â†’ 
Quantity Check â†’ Status Determination â†’ Cache Validation (Overscan Prevention) â†’ 
Save to Supabase â†’ UI Update â†’ Sound Feedback â†’ Visual Feedback
```

### **3. Hide Completed Items Flow**
```
Items Load â†’ Filter Incomplete Items â†’ Hide Completed Items â†’ 
Display Only Incomplete â†’ Update Summary Cards â†’ 
Show Completion Message (if all complete)
```

---

## ðŸŽ¨ **DESIGN SYSTEM**

### **Status Color System**
- **ðŸ”´ RED**: `Color.RED` - Untuk qtyScan < qtyPl (kurang dari rencana)
- **ðŸŸ¡ YELLOW**: `#FFCC00` - Untuk qtyScan > qtyPl atau qtyPl = 0 && qtyScan > 0
- **ðŸŸ¢ GREEN**: `#4CAF50` - Untuk qtyScan == qtyPl (disembunyikan dari UI)

### **Picklist Status System**
- **ðŸ“‹ Belum scan sama sekali**: Arrow icon, hitam, "ðŸ“‹ Belum scan sama sekali"
- **âœ… Selesai**: Check icon, hijau, "âœ… Selesai"
- **âš ï¸ Sisa X qty**: Check icon, hijau, "âš ï¸ Sisa X qty"

---

## ðŸ”§ **KONFIGURASI**

### **Environment Variables**
```properties
# local.properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
NIRWANA_BASE_URL=https://api.nirwana.com
NIRWANA_USERNAME=your-username
NIRWANA_PASSWORD=your-password
```

### **Build Configuration**
```kotlin
android {
    namespace = "com.example.cekpicklist"
    compileSdk = 35
    
    defaultConfig {
        applicationId = "com.example.cekpicklist"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    
    buildFeatures {
        viewBinding = true
    }
}
```

---

## ðŸ“ˆ **PERFORMA & OPTIMASI**

### **Cache Strategy**
- **Memory Cache**: Data picklist dalam memory untuk akses cepat
- **Stale Time**: 5 menit untuk data freshness
- **Cache Invalidation**: Otomatis invalidate saat data berubah
- **Overscan Prevention**: Validasi real-time di level cache

### **UI Optimization**
- **ViewBinding**: Type-safe view access
- **RecyclerView**: Optimized dengan ViewHolder pattern
- **Hide Completed Items**: UI lebih clean dan performa lebih baik

---

## ðŸŽ¯ **STATUS SISTEM SAAT INI**

### **âœ… Fitur yang Sudah Diimplementasikan**

1. **RFID Scanning System** âœ…
2. **Quantity Validation System** âœ…
3. **Hide Completed Items** âœ…
4. **Cache Overscan Prevention** âœ…
5. **Real-time Data Sync** âœ…
6. **Advanced Cache Management** âœ…
7. **User Interface** âœ…
8. **Picklist Selection System** âœ…
9. **Settings Integration** âœ…
10. **Auto Versioning** âœ…

### **ðŸš€ Future Enhancements**
- **Unit Testing**: Implementasi testing framework
- **Performance Monitoring**: Analytics dan crash reporting
- **Offline Mode**: Enhanced offline capabilities
- **Multi-language**: Internationalization support

---

## ðŸ“± **PRODUCTION READINESS**

Aplikasi **Cek Picklist** siap untuk production dengan:

- âœ… **Stable Architecture**: MVVM pattern yang proven
- âœ… **Complete Features**: Semua fitur utama sudah implemented
- âœ… **Performance Optimized**: Cache dan async operations
- âœ… **Error Handling**: Comprehensive error management
- âœ… **Overscan Prevention**: Cache-level validation
- âœ… **Hide Completed Items**: Clean UI dengan completion detection
- âœ… **Settings Integration**: Consistent configuration
- âœ… **Automation**: Version management yang robust

**ðŸŽ‰ Aplikasi siap untuk deployment dengan sistem yang robust!**

---

**Version**: 2.0.1 (Auto-updating)  
**Last Updated**: 2025-01-10  
**Platform**: Android 11+ (API 30+)  
**Auto Versioning**: ✅ Enabled dengan PowerShell Scripts  
**Status**: âœ… Production Ready

