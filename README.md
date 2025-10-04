# 📱 CEK PICKLIST - RFID Scanning Application

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
├── Toolbar (static)
├── Status Card (static)
└── NestedScrollView (scrollable content)
    └── LinearLayout
        ├── Current Location Card
        ├── Target Location Card
        ├── Buttons
        └── RecyclerView (height=560dp, nested scroll)
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

## 📋 **OVERVIEW APLIKASI**

**Cek Picklist** adalah aplikasi Android untuk scanning RFID dalam proses picklist dengan integrasi Supabase dan Nirwana API. Aplikasi ini dirancang untuk memudahkan proses inventory management dengan teknologi RFID scanning yang real-time.

### 🎯 **Tujuan Utama**
- **RFID Scanning**: Scanning barang menggunakan RFID hardware dengan auto-scan setiap detik
- **Validasi Quantity**: Memastikan jumlah scan sesuai dengan rencana picklist dengan sistem warna
- **Auto-Save**: Menyimpan data EPC ke Supabase secara real-time
- **Cache Overscan Prevention**: Mencegah overscan di level cache dengan validasi real-time
- **Hide Completed Items**: Items yang sudah complete disembunyikan dari UI
- **Sound Feedback**: Audio confirmation untuk setiap scanning

### 🏆 **Fitur Unggulan**
- **Real-time Sync**: Sinkronisasi data dengan Supabase secara real-time
- **Visual Status System**: Indikator warna untuk status quantity (Hijau/Merah/Kuning)
- **Hide Completed Items**: Items yang sudah complete disembunyikan dari UI
- **Swipe Actions**: Swipe-to-delete untuk koreksi data
- **Auto Versioning**: Sistem otomatisasi versi dengan GitHub Actions
- **Auto-Update System**: Cek update otomatis dari GitHub releases dengan dialog notifikasi
- **Settings Integration**: Settings yang konsisten antara MainActivity dan PicklistInputActivity

---

## 🏗️ **ARSITEKTUR APLIKASI**

### **MVVM Pattern Implementation**
```
📱 View Layer (Activities/Fragments)
    ↕️ Data Binding
🧠 ViewModel Layer (Business Logic)
    ↕️ LiveData/Observable
📊 Repository Layer (Data Access)
    ↕️ API Calls
🌐 Network Layer (API Services)
    ↕️ HTTP/WebSocket
🗄️ Data Sources (Supabase/Nirwana)
```

### **Package Structure**
```
com.example.cekpicklist/
├── 📱 Activities
│   ├── MainActivity.kt                 # Halaman utama scanning RFID
│   ├── PicklistInputActivity.kt        # Input/selection nomor picklist
│   └── SettingsActivity.kt             # Konfigurasi aplikasi
├── 📋 Adapters
│   ├── PicklistAdapter.kt              # Adapter untuk list picklist items
│   └── PicklistSelectionAdapter.kt    # Adapter untuk dialog selection
├── 🌐 API Services
│   ├── SupabaseService.kt              # API Supabase untuk database
│   ├── SupabaseRealtimeService.kt      # Realtime updates dari Supabase
│   └── NirwanaApiService.kt            # API Nirwana untuk data produk
├── 💾 Cache Management
│   ├── CacheManager.kt                 # Manajemen cache lokal
│   └── PicklistScanCache.kt            # Cache untuk scan data
├── 📊 Data Models
│   ├── PicklistItem.kt                 # Model item picklist
│   ├── PicklistStatus.kt               # Status picklist
│   └── ScanResult.kt                   # Hasil scanning RFID
├── 🏪 Repository
│   └── Repository.kt                   # Data access layer
├── 🔧 Utils
│   ├── Logger.kt                       # Logging system
│   └── ToastUtils.kt                   # Toast notifications
├── 🧠 ViewModel
│   ├── ScanViewModel.kt                # ViewModel untuk scanning
│   └── ScanViewModelFactory.kt         # Factory untuk ViewModel
└── 🚀 Application
    └── CekPicklistApplication.kt       # Application class
```

---

## ⚙️ **TEKNOLOGI & DEPENDENCIES**

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

## 📊 **DATABASE SCHEMA**

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

## 🚀 **FITUR UTAMA APLIKASI**

### **1. RFID Scanning System**
- **Auto-scan**: Scanning otomatis setiap detik dengan timer
- **EPC Buffer**: Menyimpan EPC dalam buffer untuk validasi
- **Duplicate Detection**: Mencegah scanning duplikat dengan EPC tracking
- **Sound Feedback**: Audio confirmation untuk setiap scanning

### **2. Quantity Validation System dengan Sistem Warna**
- **🔴 RED**: `qtyScan < qtyPl` (Kurang dari rencana picklist)
- **🟡 YELLOW**: `qtyScan > qtyPl` (Lebih dari rencana) atau item tidak ada di picklist
- **🟢 GREEN**: `qtyScan == qtyPl` (Sesuai dengan rencana - disembunyikan dari UI)

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
- **Status Icons**: Arrow (⬇️) untuk belum scan, Check (✅) untuk sudah scan
- **Detail Status**: 
  - "📋 Belum scan sama sekali" untuk picklist yang belum pernah di-scan
  - "✅ Selesai" untuk picklist yang sudah selesai
  - "⚠️ Sisa X qty" untuk picklist yang masih ada sisa
- **Search & Filter**: Pencarian picklist dengan filter real-time

### **7. Settings Integration**
- **Consistent Settings**: Settings yang sama antara MainActivity dan PicklistInputActivity
- **RFID Configuration**: Power Level dan RSSI Threshold settings
- **Navigation**: Settings icon mengarah ke SettingsActivity yang sama

### **8. Back Button Behavior System**
- **Data Filtering**: Saat tombol back ditekan, sistem akan memfilter data sebelum menyimpan ke Supabase
- **Overscan Handling**: Data dengan qty scan > qty PL akan DIBUANG, tidak disimpan
- **Non-picklist Handling**: Data yang tidak ada di picklist akan DIBUANG, tidak disimpan
- **Valid Data Only**: Hanya data yang valid (qty scan ≤ qty PL) yang akan disimpan ke Supabase
- **User Confirmation**: Dialog konfirmasi ditampilkan sebelum keluar dari scanning

#### **Contoh Skenario Back Button:**
- **Article A**: qty PL = 5, qty scan = 7 → **7 RFID DIBUANG** (overscan)
- **Article B**: qty PL = 5, qty scan = 4 → **4 RFID DISIMPAN** (valid)
- **Article C**: qty PL = 0, qty scan = 3 → **3 RFID DIBUANG** (non-picklist)

### **9. Auto-Update System**
- **GitHub Integration**: Cek versi terbaru dari GitHub releases API
- **Smart Interval**: Update check minimal 1 hari sekali untuk menghindari spam
- **Version Comparison**: Bandingkan versi current vs latest dengan format major.minor.patch
- **Update Dialog**: Dialog informatif dengan opsi download, nanti, atau disable
- **User Control**: User bisa disable update check atau force check manual
- **Non-blocking**: Update check berjalan di background tanpa mengganggu UI
- **Direct Download**: Buka GitHub releases page untuk download APK terbaru

#### **Cara Kerja Auto-Update:**
- **Automatic Check**: Saat aplikasi dibuka, sistem cek update otomatis
- **Version API**: Menggunakan GitHub API untuk mendapatkan versi terbaru
- **Dialog Options**: 
  - 📥 **Download**: Buka GitHub releases page
  - ⏰ **Nanti**: Tutup dialog, cek lagi nanti
  - ❌ **Jangan Tampilkan Lagi**: Disable update check
- **Manual Check**: Opsi manual check di Settings (jika tersedia)

---

## 📋 **ALUR KERJA APLIKASI**

### **1. Application Startup Flow**
```
Launch App → PicklistInputActivity → Load Picklists → 
Select Picklist → Navigate to MainActivity → Initialize RFID → Start Scanning
```

### **2. RFID Scanning Flow**
```
RFID Hardware → EPC Detection → Add to Buffer → 
Validation Timer → Nirwana API Lookup → Product Info → 
Quantity Check → Status Determination → Cache Validation (Overscan Prevention) → 
Save to Supabase → UI Update → Sound Feedback → Visual Feedback
```

### **3. Hide Completed Items Flow**
```
Items Load → Filter Incomplete Items → Hide Completed Items → 
Display Only Incomplete → Update Summary Cards → 
Show Completion Message (if all complete)
```

---

## 🎨 **DESIGN SYSTEM**

### **Status Color System**
- **🔴 RED**: `Color.RED` - Untuk qtyScan < qtyPl (kurang dari rencana)
- **🟡 YELLOW**: `#FFCC00` - Untuk qtyScan > qtyPl atau qtyPl = 0 && qtyScan > 0
- **🟢 GREEN**: `#4CAF50` - Untuk qtyScan == qtyPl (disembunyikan dari UI)

### **Picklist Status System**
- **📋 Belum scan sama sekali**: Arrow icon, hitam, "📋 Belum scan sama sekali"
- **✅ Selesai**: Check icon, hijau, "✅ Selesai"
- **⚠️ Sisa X qty**: Check icon, hijau, "⚠️ Sisa X qty"

---

## ⚙️ **KONFIGURASI**

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

## 📈 **PERFORMA & OPTIMASI**

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

## 🎯 **STATUS SISTEM SAAT INI**

### **✅ Fitur yang Sudah Diimplementasikan**

1. **RFID Scanning System** ✅
2. **Quantity Validation System** ✅
3. **Hide Completed Items** ✅
4. **Cache Overscan Prevention** ✅
5. **Real-time Data Sync** ✅
6. **Advanced Cache Management** ✅
7. **User Interface** ✅
8. **Picklist Selection System** ✅
9. **Settings Integration** ✅
10. **Auto Versioning** ✅
11. **Auto-Update System** ✅

### **🚀 Future Enhancements**
- **Unit Testing**: Implementasi testing framework
- **Performance Monitoring**: Analytics dan crash reporting
- **Offline Mode**: Enhanced offline capabilities
- **Multi-language**: Internationalization support

---

## 📱 **PRODUCTION READINESS**

Aplikasi **Cek Picklist** siap untuk production dengan:

- ✅ **Stable Architecture**: MVVM pattern yang proven
- ✅ **Complete Features**: Semua fitur utama sudah implemented
- ✅ **Performance Optimized**: Cache dan async operations
- ✅ **Error Handling**: Comprehensive error management
- ✅ **Overscan Prevention**: Cache-level validation
- ✅ **Hide Completed Items**: Clean UI dengan completion detection
- ✅ **Settings Integration**: Consistent configuration
- ✅ **Automation**: Version management yang robust
- ✅ **Auto-Update System**: Update notification dan download otomatis

**🎉 Aplikasi siap untuk deployment dengan sistem yang robust!**

---

**Version**: ∩╗┐4.1.0 (Auto-updating)  
**Last Updated**: 2025-10-04  
**Platform**: Android 11+ (API 30+)  
**Auto Versioning**: ✅ Enabled dengan PowerShell Scripts  
**Status**: ✅ Production Ready

