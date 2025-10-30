# 🔄 Barcode Scanner Logic Alignment

## 📋 Overview

Aplikasi barcode scanner Android sekarang menggunakan **logika yang sama** dengan aplikasi `azure-chinchilla-swoop` yang sudah berjalan baik. Implementasi mengikuti pola yang terbukti berhasil di web application.

## 🎯 Key Alignments

### 1. **Scanner Input Buffer Pattern**
```kotlin
// Mengikuti pola web app dengan input buffer
private var scannerInputBuffer = ""
private var lastKeyPressTime = 0L
private const val SCANNER_TIMEOUT_MS = 500L

fun handleScannerInput(input: String) {
    val currentTime = System.currentTimeMillis()
    
    // Reset buffer if too much time has passed
    if (currentTime - lastKeyPressTime > SCANNER_TIMEOUT_MS) {
        scannerInputBuffer = ""
    }
    lastKeyPressTime = currentTime
    
    // Add input to buffer
    scannerInputBuffer += input
    
    // Process if buffer is complete
    if (scannerInputBuffer.length >= 3) {
        processScannedBarcode(scannerInputBuffer.trim())
        scannerInputBuffer = ""
    }
}
```

### 2. **Processing State Management**
```kotlin
// Mengikuti pola web app dengan processing state
private var isProcessing = false

fun processScannedBarcode(barcode: String, scanDuration: Long = 0L, scannerType: String = "built-in") {
    if (isProcessing) {
        Log.w(TAG, "⏳ UI update pending. Ignoring rapid input.")
        return
    }
    
    isProcessing = true
    
    try {
        // Process barcode...
    } finally {
        isProcessing = false
    }
}
```

### 3. **Pending Operations System**
```kotlin
// Mengikuti pola web app dengan pending operations
data class PendingOperation(
    val id: String,
    val type: OperationType,
    val payload: OperationPayload,
    val timestamp: Long,
    val retries: Int = 0,
    val lastAttempt: Long = System.currentTimeMillis()
)

enum class OperationType {
    SCAN,           // Barcode scan operation
    CONFIRM,        // Confirm operation
    CANCEL,         // Cancel operation
    UPDATE          // Update operation
}
```

### 4. **Background Sync Manager**
```kotlin
// Mengikuti pola web app dengan background sync
class BackgroundSyncManager {
    private const val SYNC_INTERVAL_MS = 60 * 1000L // 1 minute
    private const val SYNC_DEBOUNCE_MS = 500L // 500ms debounce
    private const val MAX_RETRIES = 5
    
    fun startBackgroundSync() {
        // Initial sync
        syncScope.launch {
            performSync()
        }
        
        // Periodic sync
        syncJob = syncScope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                performSync()
            }
        }
    }
}
```

### 5. **Offline Support dengan Local Storage**
```kotlin
// Mengikuti pola web app dengan offline support
class PendingOperationsManager {
    private val pendingOperationsFile = File(context.filesDir, "pending_operations.dat")
    
    suspend fun addPendingOperation(operation: PendingOperation) {
        pendingOperations.add(operation)
        savePendingOperations() // Save to local file
    }
    
    private suspend fun savePendingOperations() {
        FileOutputStream(pendingOperationsFile).use { fos ->
            ObjectOutputStream(fos).use { oos ->
                oos.writeObject(pendingOperations.toList())
            }
        }
    }
}
```

## 🔄 Data Flow Comparison

### Web App (azure-chinchilla-swoop)
```
1. Scanner Input → useResiScanner hook
2. Validation → Check duplicates, format, expedition
3. Optimistic UI Update → React Query cache
4. Pending Operation → IndexedDB
5. Background Sync → Supabase API
6. Error Handling → Retry mechanism
```

### Android App (cekpicklist)
```
1. Scanner Input → BarcodeScannerViewModel
2. Validation → Check duplicates, format
3. Optimistic UI Update → LiveData
4. Pending Operation → Local File Storage
5. Background Sync → Supabase API
6. Error Handling → Retry mechanism
```

## 📊 Key Features Aligned

### ✅ **Scanner Input Buffer**
- **Web**: `scannerInputBuffer.current` dengan timeout 500ms
- **Android**: `scannerInputBuffer` dengan timeout 500ms
- **Benefit**: Menangani input scanner yang cepat dengan benar

### ✅ **Processing State**
- **Web**: `isProcessing` state dengan `useTransition`
- **Android**: `isProcessing` state dengan coroutines
- **Benefit**: Mencegah input ganda dan race conditions

### ✅ **Pending Operations**
- **Web**: IndexedDB dengan `addPendingOperation`
- **Android**: Local file storage dengan `PendingOperationsManager`
- **Benefit**: Offline support dan data persistence

### ✅ **Background Sync**
- **Web**: `useBackgroundSync` dengan interval 1 menit
- **Android**: `BackgroundSyncManager` dengan interval 1 menit
- **Benefit**: Sinkronisasi otomatis dengan retry mechanism

### ✅ **Error Handling**
- **Web**: Retry mechanism dengan max 5 attempts
- **Android**: Retry mechanism dengan max 5 attempts
- **Benefit**: Robust error handling dan recovery

### ✅ **Data Structure**
- **Web**: `tbl_resi` dan `tbl_expedisi` structure
- **Android**: `barcode_scan` dengan kolom yang sama
- **Benefit**: Konsistensi database dan integrasi

## 🚀 Performance Benefits

### **1. Offline First**
- Data tersimpan lokal saat offline
- Sinkronisasi otomatis saat online
- Tidak ada data loss

### **2. Optimistic UI**
- UI update langsung tanpa menunggu server
- User experience yang responsif
- Feedback visual yang cepat

### **3. Background Processing**
- Sync berjalan di background
- Tidak mengganggu user interaction
- Efficient resource usage

### **4. Retry Mechanism**
- Automatic retry untuk failed operations
- Exponential backoff
- Graceful error handling

## 🔧 Implementation Details

### **Scanner Input Handling**
```kotlin
// Mengikuti pola web app
fun handleScannerInput(input: String) {
    val currentTime = System.currentTimeMillis()
    
    // Reset buffer if too much time has passed
    if (currentTime - lastKeyPressTime > SCANNER_TIMEOUT_MS) {
        scannerInputBuffer = ""
    }
    lastKeyPressTime = currentTime
    
    // Add input to buffer
    scannerInputBuffer += input
    
    // Process if buffer is complete
    if (scannerInputBuffer.length >= 3) {
        processScannedBarcode(scannerInputBuffer.trim())
        scannerInputBuffer = ""
    }
}
```

### **Pending Operations**
```kotlin
// Mengikuti pola web app
viewModelScope.launch {
    try {
        val operation = PendingOperationFactory.createScanOperation(
            resiNumber = trimmedBarcode,
            selectedKarung = "0",
            courierNameFromExpedisi = scannerType,
            scannerType = scannerType,
            scanDuration = scanDuration,
            deviceInfo = "Android Device"
        )
        pendingOperationsManager.addPendingOperation(operation)
        
        // Trigger background sync
        if (::backgroundSyncManager.isInitialized) {
            backgroundSyncManager.triggerSync()
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error adding pending operation: ${e.message}", e)
    }
}
```

### **Background Sync**
```kotlin
// Mengikuti pola web app
private suspend fun performSync() {
    if (isSyncing) {
        Log.d(TAG, "⏳ Sync in progress. Skipping.")
        return
    }
    
    isSyncing = true
    
    try {
        val pendingOperations = pendingOperationsManager.getPendingOperations()
        
        for (operation in pendingOperations) {
            try {
                val success = when (operation.type) {
                    OperationType.SCAN -> processScanOperation(operation)
                    OperationType.CONFIRM -> processConfirmOperation(operation)
                    OperationType.CANCEL -> processCancelOperation(operation)
                    OperationType.UPDATE -> processUpdateOperation(operation)
                }
                
                if (success) {
                    pendingOperationsManager.deletePendingOperation(operation.id)
                } else {
                    // Retry logic
                }
            } catch (error: Exception) {
                // Error handling with retry
            }
        }
    } finally {
        isSyncing = false
    }
}
```

## 📈 Benefits dari Alignment

### **1. Proven Logic**
- Menggunakan logika yang sudah terbukti berhasil di web app
- Mengurangi risiko bugs dan issues
- Konsistensi behavior antara web dan mobile

### **2. Better Performance**
- Offline support untuk performa yang lebih baik
- Background sync yang tidak mengganggu UI
- Optimistic updates untuk responsivitas

### **3. Robust Error Handling**
- Retry mechanism yang sama dengan web app
- Graceful degradation saat offline
- Comprehensive error logging

### **4. Maintainability**
- Code structure yang familiar untuk tim development
- Consistent patterns antara web dan mobile
- Easier debugging dan troubleshooting

## 🎯 Next Steps

1. **Testing**: Test offline/online scenarios
2. **Performance**: Monitor sync performance
3. **Error Handling**: Test error scenarios
4. **Integration**: Test dengan existing RFID system

---

**✅ Barcode scanner Android sekarang menggunakan logika yang sama dengan aplikasi web yang sudah berjalan baik!**

