# 📱 **SIMULASI ALUR SCAN BARCODE - BARIS PER BARIS**

## 🎯 **OVERVIEW**
Dokumen ini menelusuri alur lengkap dari saat tombol scan barcode ditekan hingga data siap dan validasi selesai, dengan estimasi waktu untuk setiap langkah.

---

## 📍 **TIMELINE TOTAL**

**Best Case (Cache Hit, Network Fast):** ~50-150ms  
**Normal Case (Cache Hit, Network Normal):** ~100-300ms  
**Worst Case (Cache Miss, Network Slow):** ~500-2000ms  

---

## 🔄 **ALUR LENGKAP: BARIS PER BARIS**

### **PHASE 1: USER ACTION (0ms)**

#### 1.1 BarcodeScannerTabsActivity.onKeyDown() / Button Click
**File:** `BarcodeScannerTabsActivity.kt:309-325`
```
⏱️ Waktu: ~1-5ms (UI event handling)

- KeyDown event diterima (keyCode 293)
- Cek useBroadcastInput flag
- Jika broadcast mode: allowNextBroadcast = true
- Jika direct mode: startBarcodeScan()
```

#### 1.2 BuiltInBarcodeManager Callback
**File:** `BarcodeScannerTabsActivity.kt:291-296`
```
⏱️ Waktu: ~5-10ms (barcode scanner hardware response)

- Barcode terdeteksi oleh hardware scanner
- Callback dipanggil dengan barcode string
- viewModel.processScannedBarcode(barcode) dipanggil
```

---

### **PHASE 2: DEBOUNCE & PRE-VALIDATION (10-20ms)**

#### 2.1 BarcodeScannerViewModel.processScannedBarcode()
**File:** `BarcodeScannerViewModel.kt:343-388`
```
⏱️ Waktu: ~5-15ms (sync operations)

Line 344: val trimmedBarcode = barcode.trim()          // ~0.1ms
Line 345: val currentTime = System.currentTimeMillis() // ~0.1ms
Line 347-351: Log operations                          // ~1-2ms

Line 354-366: DEBOUNCE CHECK #1
├─ Cek isProcessing flag
├─ Jika true: return early dengan error message
└─ Waktu: ~1-2ms

Line 369-383: DEBOUNCE CHECK #2 (MIN_SCAN_INTERVAL_MS = 100ms)
├─ Cek timeSinceLastScan
├─ Jika < 100ms: return early dengan error message
└─ Waktu: ~1-2ms

Line 387: processBarcodeInternal() dipanggil
└─ Waktu: ~1ms (function call)
```

**Total Phase 2: ~10-25ms**

---

### **PHASE 3: INTERNAL PROCESSING START (25-30ms)**

#### 3.1 BarcodeScannerViewModel.processBarcodeInternal()
**File:** `BarcodeScannerViewModel.kt:393-509`
```
⏱️ Waktu: ~5-10ms (initialization)

Line 394: isProcessing = true                         // ~0.1ms
Line 397: viewModelScope.launch { ... }               // ~1ms (coroutine launch)

Line 399: val trimmedBarcode = barcode.trim()         // ~0.1ms

Line 402-430: PRE-VALIDATION (Expedisi & Karung Check)
├─ Cek selectedExpedisi null/empty
├─ Cek selectedKarung null/empty
├─ Jika salah satu kosong: return early dengan error
└─ Waktu: ~2-5ms
```

**Total Phase 3: ~5-15ms**

---

### **PHASE 4: EXPEDISI VALIDATOR (30-300ms)**

#### 4.1 ExpedisiValidator.validateBarcode()
**File:** `ExpedisiValidator.kt:93-231`

```
⏱️ Waktu: VARIABEL - tergantung cache status
- Cache Hit (all data in memory): ~50-150ms
- Cache Miss (need fetch): ~200-1000ms
- Network Slow: ~500-2000ms

Line 96: Log operation                             // ~1ms

Line 101-130: VALIDATION STEP 1 - Expedisi & Karung Check
├─ Cek selectedExpedisi null/empty
├─ Cek selectedKarung null/empty
├─ Jika error: return ValidationStatus.INVALID_FORMAT
└─ Waktu: ~2-5ms

Line 135: checkResiInDatabase(trimmedBarcode)       // ⚠️ CRITICAL PATH
├─ EnhancedRepository.getBarcodeResiRecords()
│  ├─ Cache Hit: ~10-50ms
│  │  ├─ BarcodeCacheManager.getAllResiRecords()
│  │  ├─ Filter by Resi == barcode
│  │  └─ Return result
│  │
│  └─ Cache Miss: ~200-800ms
│     ├─ BarcodeSupabaseService.getAllBarcodeResi()
│     ├─ Network call ke Supabase
│     ├─ Parse JSON response
│     ├─ Update cache
│     └─ Return result
│
├─ Jika ditemukan: return DUPLICATE_PROCESSED
└─ Waktu: ~10ms - 800ms (tergantung cache)

Line 175: checkExpedisiInDatabase(trimmedBarcode)   // ⚠️ CRITICAL PATH
├─ EnhancedRepository.getBarcodeExpedisiRecords()
│  ├─ Cache Hit: ~10-50ms
│  │  ├─ BarcodeCacheManager.getAllExpedisiRecords()
│  │  ├─ Filter by resino == barcode
│  │  └─ Return result
│  │
│  └─ Cache Miss: ~200-800ms
│     ├─ BarcodeSupabaseService.getAllBarcodeExpedisi()
│     ├─ Network call ke Supabase
│     ├─ Parse JSON response
│     ├─ Update cache
│     └─ Return result
│
├─ Jika null: return NOT_FOUND_IN_EXPEDISI
└─ Waktu: ~10ms - 800ms (tergantung cache)

Line 188-201: VALIDATION STEP 4 - Expedition Mismatch Check
├─ normalizeExpeditionName() untuk expedisi dari DB
├─ normalizeExpeditionName() untuk expedisi yang dipilih
├─ Bandingkan kedua normalized name
├─ Jika tidak match: return MISMATCH_EXPEDISI
└─ Waktu: ~2-5ms

Line 204-219: VALIDATION STEP 5 - Schedule Determination
├─ determineSchedule(expedisiRecord.created)
├─ normalizeExpeditionName()
├─ Return ValidationStatus.OK dengan schedule
└─ Waktu: ~2-5ms
```

**Total Phase 4:**
- **Best Case (Cache Hit):** ~50-150ms
- **Normal Case (Cache Hit):** ~100-300ms
- **Worst Case (Cache Miss):** ~500-2000ms

---

### **PHASE 5: SAVE DATA (150-350ms)**

#### 5.1 BarcodeScannerViewModel.processSuccessfulScan()
**File:** `BarcodeScannerViewModel.kt:515-587`

```
⏱️ Waktu: ~10-50ms (synchronous operations)

Line 523-532: GUARD Check
├─ Cek validationResult.status == OK
├─ Jika tidak: return early dengan error
└─ Waktu: ~1-2ms

Line 537: val createdTime = Instant.now().toString()  // ~0.5ms
Line 538-545: Create BarcodeScanRecord
├─ Create data class instance
├─ Set semua field
└─ Waktu: ~1-2ms

Line 550: enhancedRepository.saveBarcodeResiRecord() // ⚠️ CRITICAL PATH
```

#### 5.2 EnhancedRepository.saveBarcodeResiRecord()
**File:** `EnhancedRepository.kt:513-553`

```
⏱️ Waktu: ~5-20ms (LOCAL-FIRST - INSTANT CACHE UPDATE)

Line 516-521: Validate created timestamp
├─ Cek record.created null/empty
├─ Jika kosong: tambahkan timestamp sekarang
└─ Waktu: ~1ms

Line 524: BarcodeCacheManager.addResiRecord()        // ⚡ INSTANT
├─ Add ke ConcurrentHashMap in-memory cache
├─ Update processed set
├─ Non-blocking, thread-safe
└─ Waktu: ~1-5ms (sangat cepat, in-memory)

Line 529: CoroutineScope.launch { ... }               // ⚡ BACKGROUND
├─ Launch coroutine terpisah
├─ Network call dilakukan di background
└─ Waktu: ~1ms (non-blocking launch)

Line 531: barcodeSupabaseService.saveBarcodeScan()   // ⚡ BACKGROUND (Non-blocking)
└─ POST ke Supabase terjadi di background
└─ Tidak blocking return value

Line 525: Log operation                              // ~1ms
Line 548: return true                                // ⚡ OPTIMISTIC RETURN
└─ Return true INSTAN (tidak menunggu network)
```

**Total Phase 5:**
- **Cache Update:** ~5-20ms (INSTANT)
- **Network Sync:** Background (tidak blocking)

---

### **PHASE 6: UI UPDATE (170-370ms)**

#### 6.1 UI State Update
**File:** `BarcodeScannerViewModel.kt:563-581`

```
⏱️ Waktu: ~5-15ms (LiveData update)

Line 564: BarcodeCacheManager.getProcessedResiSet().size
├─ Get size dari cache
└─ Waktu: ~1ms

Line 565: _scanCount.value = processedCount
├─ LiveData update
├─ Trigger observer di UI
└─ Waktu: ~2-5ms (observer execution)

Line 566: _processedBarcodesFromDB.value = ...
├─ LiveData update
├─ Trigger observer di UI
└─ Waktu: ~2-5ms (observer execution)

Line 569-570: Format lastScanTime
├─ SimpleDateFormat.format()
└─ Waktu: ~1-2ms

Line 570: _lastScanTime.value = ...
├─ LiveData update
└─ Waktu: ~2-5ms

Line 573-581: _scanResult.value = ScanResult(...)
├─ Create ScanResult object
├─ LiveData update
├─ Trigger observer di UI (Fragment/Activity)
│  ├─ Update UI elements
│  ├─ Play success beep sound
│  ├─ Show toast notification
│  └─ Update scan count display
└─ Waktu: ~10-50ms (dengan UI rendering)
```

**Total Phase 6: ~15-70ms**

---

### **PHASE 7: BACKGROUND SYNC (Non-blocking)**

#### 7.1 Network Sync to Supabase
**File:** `EnhancedRepository.kt:529-543` (Background coroutine)

```
⏱️ Waktu: Background (tidak blocking UI)
- Network call: ~100-500ms (tergantung network)
- POST request ke Supabase
- Response handling
- Error handling (jika gagal, akan retry di background sync)
```

#### 7.2 Flag Update
**File:** `BarcodeScannerViewModel.kt:588-591` (Background coroutine)

```
⏱️ Waktu: Background (tidak blocking UI)
- PATCH request ke Supabase untuk update flag
- Network call: ~100-500ms
```

---

### **PHASE 8: CLEANUP (185-395ms)**

#### 8.1 Finalization
**File:** `BarcodeScannerViewModel.kt:502-507`

```
⏱️ Waktu: ~5-10ms

Line 503: isProcessing = false
└─ Waktu: ~0.1ms

Line 505: lastScanCompleteTime = System.currentTimeMillis()
└─ Waktu: ~0.1ms

Line 506: Log operation
└─ Waktu: ~1ms
```

**Total Phase 8: ~2-5ms**

---

## 📊 **ESTIMASI WAKTU TOTAL**

### **Best Case Scenario (Cache Hit, Network Fast)**
```
Phase 1: User Action              ~10ms
Phase 2: Debounce                ~15ms
Phase 3: Internal Processing     ~10ms
Phase 4: Validation (Cache Hit)  ~100ms
Phase 5: Save Data                ~15ms
Phase 6: UI Update                ~30ms
Phase 8: Cleanup                   ~5ms
────────────────────────────────────────
TOTAL:                            ~185ms ✅
```

### **Normal Case Scenario (Cache Hit, Network Normal)**
```
Phase 1: User Action              ~10ms
Phase 2: Debounce                ~20ms
Phase 3: Internal Processing     ~15ms
Phase 4: Validation (Cache Hit)  ~200ms
Phase 5: Save Data                ~20ms
Phase 6: UI Update                ~50ms
Phase 8: Cleanup                   ~5ms
────────────────────────────────────────
TOTAL:                            ~320ms ✅
```

### **Worst Case Scenario (Cache Miss, Network Slow)**
```
Phase 1: User Action              ~10ms
Phase 2: Debounce                ~25ms
Phase 3: Internal Processing     ~15ms
Phase 4: Validation (Cache Miss) ~1500ms ⚠️
Phase 5: Save Data                ~20ms
Phase 6: UI Update                ~70ms
Phase 8: Cleanup                   ~5ms
────────────────────────────────────────
TOTAL:                            ~1645ms ⚠️
```

---

## 🔑 **POINTS PENTING**

### **1. Local-First Strategy**
- ✅ Cache update **INSTANT** (~1-5ms)
- ✅ Network sync di **background** (non-blocking)
- ✅ UI update **tidak menunggu network**

### **2. Critical Path (Bottleneck)**
- ⚠️ **ExpedisiValidator.validateBarcode()** adalah bottleneck utama
- ⚠️ Terutama `checkResiInDatabase()` dan `checkExpedisiInDatabase()`
- ⚠️ Jika cache miss, perlu network call (~200-800ms per call)

### **3. Optimizations**
- ✅ Cache hit: Sangat cepat (~50-150ms total)
- ✅ Debounce: Mencegah duplicate processing
- ✅ Background sync: Tidak blocking UI

### **4. Data Ready State**
- ✅ **Data siap di cache:** ~20ms setelah button click
- ✅ **UI update selesai:** ~50-70ms setelah button click
- ✅ **Network sync:** Background (tidak blocking)

---

## 🎯 **KESIMPULAN**

**Untuk user experience:**
- ✅ **Data tersimpan di cache:** ~20ms (INSTANT)
- ✅ **UI feedback:** ~50-150ms (sangat cepat)
- ✅ **Bisa scan berikutnya:** ~100ms setelah scan sebelumnya

**Network sync terjadi di background:**
- ⚡ Tidak blocking UI
- ⚡ Jika gagal, akan retry otomatis
- ⚡ User tidak merasakan delay network

**Bottleneck utama:**
- ⚠️ **Validation dengan cache miss:** Bisa sampai 1500ms
- 💡 **Solusi:** Ensure cache selalu warmed up

