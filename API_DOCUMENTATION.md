# API Documentation

Dokumentasi lengkap untuk semua API yang digunakan dalam aplikasi Cek Picklist.

## 🌐 Supabase API

### Base Configuration
```kotlin
private const val SUPABASE_URL = "https://your-project.supabase.co"
private const val SUPABASE_ANON_KEY = "your-anon-key"
```

### Endpoints

#### 1. **Get Picklists**
```http
GET /rest/v1/picklist?select=no_picklist&order=created_at.desc
```

**Response:**
```json
[
  {
    "no_picklist": "PL001"
  },
  {
    "no_picklist": "PL002"
  }
]
```

#### 2. **Get Picklist Items**
```http
GET /rest/v1/picklist?no_picklist=eq.PL001&select=*
```

**Response:**
```json
[
  {
    "id": "uuid",
    "no_picklist": "PL001",
    "article_id": "ART001",
    "article_name": "T-Shirt",
    "size": "M",
    "product_id": "PROD001",
    "qty": 5,
    "created_at": "2025-01-01T00:00:00Z"
  }
]
```

#### 3. **Save Picklist Scan**
```http
POST /rest/v1/picklist_scan
Content-Type: application/json

{
  "no_picklist": "PL001",
  "product_id": "PROD001",
  "article_id": "ART001",
  "article_name": "T-Shirt",
  "size": "M",
  "epc": "EPC123456789",
  "notrans": null
}
```

**Response:**
```json
{
  "id": "uuid",
  "no_picklist": "PL001",
  "product_id": "PROD001",
  "article_id": "ART001",
  "article_name": "T-Shirt",
  "size": "M",
  "epc": "EPC123456789",
  "notrans": null,
  "created_at": "2025-01-01T00:00:00Z"
}
```

#### 4. **Get Picklist Scans**
```http
GET /rest/v1/picklist_scan?no_picklist=eq.PL001&select=*
```

**Response:**
```json
[
  {
    "id": "uuid",
    "no_picklist": "PL001",
    "product_id": "PROD001",
    "article_id": "ART001",
    "article_name": "T-Shirt",
    "size": "M",
    "epc": "EPC123456789",
    "notrans": null,
    "created_at": "2025-01-01T00:00:00Z"
  }
]
```

### Realtime Subscriptions

#### 1. **Picklist Changes**
```kotlin
realtimeService.subscribeToPicklists { change ->
    when (change.action) {
        "INSERT" -> handleNewPicklist(change)
        "UPDATE" -> handlePicklistUpdate(change)
        "DELETE" -> handlePicklistDelete(change)
    }
}
```

#### 2. **Picklist Scan Changes**
```kotlin
realtimeService.subscribeToPicklistScans { change ->
    when (change.action) {
        "INSERT" -> handleNewScan(change)
        "UPDATE" -> handleScanUpdate(change)
        "DELETE" -> handleScanDelete(change)
    }
}
```

## 🔍 Nirwana API

### Base Configuration
```kotlin
private const val NIRWANA_BASE_URL = "https://api.nirwana.com"
private const val NIRWANA_USERNAME = "your-username"
private const val NIRWANA_PASSWORD = "your-password"
```

### Authentication

#### 1. **Login**
```http
POST /auth/login
Content-Type: application/json

{
  "username": "your-username",
  "password": "your-password"
}
```

**Response:**
```json
{
  "access_token": "jwt-token-here"
}
```

### Product Information

#### 1. **Get Product Info by RFID**
```http
POST /products/rfid
Content-Type: application/json
Authorization: Bearer jwt-token-here

{
  "rfid_list": "EPC001,EPC002,EPC003",
  "limit": "100"
}
```

**Response:**
```json
{
  "length": 3,
  "data": [
    {
      "index": 0,
      "product_id": "PROD001",
      "product_name": "T-Shirt",
      "article_id": "ART001",
      "article_name": "T-Shirt Cotton",
      "brand": "Nike",
      "category": "Clothing",
      "sub_category": "T-Shirt",
      "color": "Black",
      "gender": "Unisex",
      "size": "M",
      "warehouse": "WH001",
      "tag_status": "ACTIVE",
      "qty": 10,
      "rfid_list": ["EPC001"]
    }
  ]
}
```

## 📱 RFID Hardware API

### DeviceAPI Integration

#### 1. **Initialize RFID Reader**
```kotlin
// Di MainActivity.kt
private fun initializeRFID() {
    try {
        // Initialize RFID hardware
        DeviceAPI.init()
        Log.d("MainActivity", "RFID initialized successfully")
    } catch (e: Exception) {
        Log.e("MainActivity", "RFID initialization failed: ${e.message}")
    }
}
```

#### 2. **Start Scanning**
```kotlin
private fun startRFIDScanning() {
    try {
        DeviceAPI.startScan { epc, rssi ->
            // Callback untuk setiap EPC yang terdeteksi
            viewModel.addEpc(epc, rssi)
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "RFID scanning failed: ${e.message}")
    }
}
```

#### 3. **Stop Scanning**
```kotlin
private fun stopRFIDScanning() {
    try {
        DeviceAPI.stopScan()
        Log.d("MainActivity", "RFID scanning stopped")
    } catch (e: Exception) {
        Log.e("MainActivity", "Error stopping RFID: ${e.message}")
    }
}
```

## 🔄 Data Flow

### 1. **RFID Scanning Flow**
```
RFID Hardware → DeviceAPI → MainActivity → ScanViewModel → Repository → Supabase
```

### 2. **Product Validation Flow**
```
EPC → Nirwana API → Product Info → Quantity Validation → Save/Reject
```

### 3. **Realtime Update Flow**
```
Supabase → Realtime Service → ScanViewModel → UI Update
```

## 🛠️ Error Handling

### Common Error Codes

#### Supabase Errors
- **401**: Unauthorized - Cek API key
- **404**: Not found - Resource tidak ada
- **500**: Server error - Cek Supabase status

#### Nirwana API Errors
- **401**: Invalid credentials - Cek username/password
- **429**: Rate limit exceeded - Tunggu beberapa saat
- **500**: Server error - Cek Nirwana API status

#### RFID Hardware Errors
- **Connection Error**: Cek koneksi hardware
- **Power Error**: Cek power level
- **Timeout Error**: Restart scanning

## 📊 Response Models

### PicklistItem
```kotlin
data class PicklistItem(
    val id: String,
    val noPicklist: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val productId: String?,
    val qtyPl: Int,
    val qtyScan: Int = 0,
    val createdAt: String? = null,
    val warehouse: String? = null,
    val tagStatus: String? = null
)
```

### ScanResult
```kotlin
data class ScanResult(
    val rfid: String,
    val productId: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val rssi: Int = 0,
    val warehouse: String? = null,
    val tagStatus: String? = null
)
```

### PicklistScan
```kotlin
data class PicklistScan(
    val noPicklist: String,
    val productId: String,
    val articleId: String,
    val articleName: String,
    val size: String,
    val epc: String,
    val notrans: String? = null
)
```

## 🔧 Configuration

### Environment Variables
```properties
# local.properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
NIRWANA_BASE_URL=https://api.nirwana.com
NIRWANA_USERNAME=your-username
NIRWANA_PASSWORD=your-password
```

### Network Configuration
```kotlin
// Di Repository.kt
private val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
```

## 📈 Performance Tips

1. **Batch Processing**: Kumpulkan EPC dalam batch sebelum API call
2. **Caching**: Gunakan cache untuk mengurangi API calls
3. **Background Processing**: Gunakan coroutines untuk async operations
4. **Connection Pooling**: Reuse HTTP connections
5. **Error Retry**: Implement retry mechanism untuk failed requests

---

**Last Updated**: 2025  
**Version**: 1.0
