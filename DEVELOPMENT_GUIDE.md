# Development Guide

Panduan lengkap untuk development, maintenance, dan troubleshooting aplikasi Cek Picklist.

## 🚀 Setup Development Environment

### Prerequisites
- **Android Studio** Arctic Fox atau lebih baru
- **JDK 17** atau lebih baru
- **Android SDK 30+**
- **Git** untuk version control

### Project Setup
```bash
# Clone repository
git clone <repository-url>
cd cekpicklist

# Open di Android Studio
# File → Open → Pilih folder cekpicklist
```

### Configuration
1. **Setup Supabase**
   ```kotlin
   // Di local.properties
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   ```

2. **Setup Nirwana API**
   ```kotlin
   // Di local.properties
   NIRWANA_BASE_URL=https://api.nirwana.com
   NIRWANA_USERNAME=your-username
   NIRWANA_PASSWORD=your-password
   ```

3. **Build Project**
   ```bash
   ./gradlew build
   ```

## 🏗️ Architecture Overview

### MVVM Pattern
```
View (Activity/Fragment) ↔ ViewModel ↔ Repository ↔ Data Source
```

### Package Structure
```
com.example.cekpicklist/
├── MainActivity.kt                 # Main scanning activity
├── PicklistInputActivity.kt        # Picklist selection
├── SettingsActivity.kt             # App configuration
├── adapter/                        # RecyclerView adapters
│   ├── PicklistAdapter.kt
│   └── PicklistSelectionAdapter.kt
├── api/                           # Network services
│   ├── SupabaseService.kt
│   ├── SupabaseRealtimeService.kt
│   └── NirwanaApiService.kt
├── cache/                         # Local caching
│   └── CacheManager.kt
├── data/                          # Data models
│   ├── PicklistItem.kt
│   ├── ScanResult.kt
│   └── PicklistScan.kt
├── repository/                    # Data access layer
│   └── Repository.kt
├── utils/                         # Utility classes
│   └── Logger.kt
└── viewmodel/                     # Business logic
    ├── ScanViewModel.kt
    └── ScanViewModelFactory.kt
```

## 🔧 Development Workflow

### 1. **Feature Development**
```bash
# Create feature branch
git checkout -b feature/new-feature

# Make changes
# Test changes
# Commit changes
git add .
git commit -m "feat: add new feature"

# Push to remote
git push origin feature/new-feature
```

### 2. **Code Standards**
- **Kotlin**: Gunakan Kotlin coding conventions
- **Naming**: camelCase untuk variables, PascalCase untuk classes
- **Comments**: Gunakan KDoc untuk public functions
- **Logging**: Gunakan tag "ScanViewModel", "MainActivity", dll

### 3. **Testing**
```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Build APK
./gradlew assembleDebug
```

## 🐛 Debugging

### Logging Strategy
```kotlin
// Debug logging dengan tag yang konsisten
Log.d("ScanViewModel", "🔥 Processing ${epcBuffer.size} EPCs")
Log.i("MainActivity", "🔥 RFID initialized successfully")
Log.e("Repository", "🔥 API call failed: ${e.message}")
```

### Common Debug Scenarios

#### 1. **RFID Not Detected**
```kotlin
// Cek di MainActivity.kt
private fun debugRFIDStatus() {
    Log.d("MainActivity", "🔥 RFID Status:")
    Log.d("MainActivity", "   Hardware connected: ${DeviceAPI.isConnected()}")
    Log.d("MainActivity", "   Power level: ${DeviceAPI.getPowerLevel()}")
    Log.d("MainActivity", "   Scanning active: ${DeviceAPI.isScanning()}")
}
```

#### 2. **API Connection Issues**
```kotlin
// Cek di Repository.kt
private fun debugApiStatus() {
    Log.d("Repository", "🔥 API Status:")
    Log.d("Repository", "   Supabase connected: ${supabaseService.isConnected()}")
    Log.d("Repository", "   Nirwana authenticated: ${nirwanaService.isAuthenticated()}")
    Log.d("Repository", "   Realtime connected: ${realtimeService.isConnected()}")
}
```

#### 3. **Data Sync Issues**
```kotlin
// Cek di ScanViewModel.kt
private fun debugDataSync() {
    Log.d("ScanViewModel", "🔥 Data Sync Status:")
    Log.d("ScanViewModel", "   Cache size: ${cacheManager.getCacheSize()}")
    Log.d("ScanViewModel", "   EPC buffer: ${epcBuffer.size}")
    Log.d("ScanViewModel", "   Processed buffer: ${processedEpcBuffer.size}")
}
```

## 🔄 Data Flow Debugging

### 1. **RFID Scanning Flow**
```
RFID Hardware → DeviceAPI → MainActivity → ScanViewModel → Repository → Supabase
     ↓              ↓           ↓            ↓            ↓          ↓
   Log.d()       Log.d()     Log.d()      Log.d()      Log.d()   Log.d()
```

### 2. **Product Validation Flow**
```
EPC → Nirwana API → Product Info → Quantity Check → Save/Reject
 ↓        ↓            ↓              ↓              ↓
Log.d() Log.d()     Log.d()        Log.d()       Log.d()
```

### 3. **Realtime Update Flow**
```
Supabase → Realtime Service → ScanViewModel → UI Update
    ↓            ↓                ↓              ↓
 Log.d()      Log.d()          Log.d()        Log.d()
```

## 🛠️ Common Issues & Solutions

### 1. **Build Issues**

#### Gradle Sync Failed
```bash
# Clean project
./gradlew clean

# Invalidate caches
# File → Invalidate Caches and Restart

# Re-sync project
# File → Sync Project with Gradle Files
```

#### Dependency Conflicts
```bash
# Check dependency tree
./gradlew app:dependencies

# Resolve conflicts
# Update versions di build.gradle.kts
```

### 2. **Runtime Issues**

#### App Crashes on Startup
```kotlin
// Cek di MainActivity.onCreate()
try {
    initializeRFID()
    setupViewModel()
    setupUI()
} catch (e: Exception) {
    Log.e("MainActivity", "🔥 Startup error: ${e.message}", e)
    // Show error dialog
}
```

#### RFID Not Working
```kotlin
// Cek hardware connection
if (!DeviceAPI.isConnected()) {
    Log.e("MainActivity", "🔥 RFID hardware not connected")
    // Show error message
    return
}

// Cek power level
val powerLevel = DeviceAPI.getPowerLevel()
if (powerLevel < 20) {
    Log.w("MainActivity", "🔥 Low power level: $powerLevel")
    // Show warning
}
```

#### API Connection Failed
```kotlin
// Cek network connectivity
if (!isNetworkAvailable()) {
    Log.e("Repository", "🔥 No network connection")
    // Show offline message
    return
}

// Cek API credentials
if (supabaseKey.isEmpty()) {
    Log.e("Repository", "🔥 Supabase key not configured")
    // Show configuration error
    return
}
```

### 3. **Data Issues**

#### Cache Corruption
```kotlin
// Clear cache
cacheManager.clearAllCache()

// Restart app
// Or implement cache validation
```

#### Sync Issues
```kotlin
// Force refresh
viewModel.refreshPicklistData(picklistNumber)

// Check realtime connection
if (!realtimeService.isConnected()) {
    realtimeService.reconnect()
}
```

## 📊 Performance Optimization

### 1. **Memory Management**
```kotlin
// Di ViewModel
override fun onCleared() {
    super.onCleared()
    // Cancel coroutines
    autoPostTimer?.cancel()
    // Clear buffers
    epcBuffer.clear()
    processedEpcBuffer.clear()
}
```

### 2. **Network Optimization**
```kotlin
// Batch API calls
private suspend fun batchProcessEPCs(epcs: List<String>) {
    val batches = epcs.chunked(10) // Process 10 EPCs at a time
    batches.forEach { batch ->
        val results = repository.getProductInfoFromRFID(batch)
        processResults(results)
    }
}
```

### 3. **UI Optimization**
```kotlin
// Use DiffUtil untuk RecyclerView
class PicklistAdapter : RecyclerView.Adapter<PicklistAdapter.ViewHolder>() {
    fun updateItems(newItems: List<PicklistItem>) {
        val diffCallback = PicklistDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }
}
```

## 🧪 Testing

### 1. **Unit Tests**
```kotlin
// Test ViewModel
@Test
fun `test addEpc adds new EPC to buffer`() {
    val viewModel = ScanViewModel(Application())
    val epc = "EPC123456789"
    
    val result = viewModel.addEpc(epc)
    
    assertTrue(result)
    assertEquals(1, viewModel.scanCounter.value)
}
```

### 2. **Integration Tests**
```kotlin
// Test API integration
@Test
fun `test savePicklistScan saves to Supabase`() = runTest {
    val repository = Repository()
    val picklistScan = PicklistScan(
        noPicklist = "PL001",
        productId = "PROD001",
        articleId = "ART001",
        articleName = "Test Item",
        size = "M",
        epc = "EPC123456789"
    )
    
    repository.savePicklistScan(picklistScan)
    
    // Verify data saved
    val savedScans = repository.getPicklistScans("PL001")
    assertTrue(savedScans.any { it.epc == "EPC123456789" })
}
```

### 3. **UI Tests**
```kotlin
// Test UI interactions
@Test
fun testPicklistSelection() {
    onView(withId(R.id.btnLoadPicklist))
        .perform(click())
    
    onView(withText("PL001"))
        .perform(click())
    
    onView(withId(R.id.rvPicklistItems))
        .check(matches(isDisplayed()))
}
```

## 🚀 Deployment

### 1. **Build Release**
```bash
# Generate signed APK
./gradlew assembleRelease

# APK akan tersimpan di
# app/build/outputs/apk/release/app-release.apk
```

### 2. **Install on Device**
```bash
# Install via ADB
adb install app-release.apk

# Or copy APK to device and install manually
```

### 3. **Version Management**
```kotlin
// Di build.gradle.kts
defaultConfig {
    versionCode = 2
    versionName = "1.1"
}
```

## 📝 Code Review Checklist

### Before Submitting PR
- [ ] Code follows Kotlin conventions
- [ ] All functions have proper documentation
- [ ] Error handling implemented
- [ ] Logging added for debugging
- [ ] Unit tests written
- [ ] No hardcoded values
- [ ] Performance considerations addressed
- [ ] Memory leaks prevented

### Review Focus Areas
- **Architecture**: Follows MVVM pattern
- **Error Handling**: Proper exception handling
- **Performance**: No blocking operations on main thread
- **Security**: No sensitive data exposed
- **Maintainability**: Clean, readable code

## 🔧 Maintenance

### Regular Tasks
1. **Update Dependencies**: Monthly
2. **Security Patches**: As needed
3. **Performance Monitoring**: Weekly
4. **Log Analysis**: Daily
5. **Backup Configuration**: Weekly

### Monitoring
- **Crash Reports**: Firebase Crashlytics
- **Performance**: Android Vitals
- **API Usage**: Supabase Dashboard
- **User Feedback**: Play Console

---

**Last Updated**: 2025  
**Version**: 1.0
