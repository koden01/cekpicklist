# 📱 **SIMULASI: SCAN BARCODE SAAT ACTIVITY DIBUKA DENGAN CACHE KOSONG**

## 🎯 **SKENARIO**
User membuka `BarcodeScannerTabsActivity` untuk pertama kali atau setelah cache di-clear, kemudian melakukan scan barcode.

---

## ⏱️ **TIMELINE SIMULASI**

### **PHASE 1: Activity Dibuka (Cache Kosong)**

**Waktu:** `T+0ms` - `T+50ms`

#### **1.1 Activity.onCreate()**
**File:** `BarcodeScannerTabsActivity.kt:62-73`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_barcode_scanner_tabs)
    
    setupToolbar()              // ~5ms
    setupViewModel()            // ⚠️ See 1.2
    setupAudioManager()         // ~10ms
    setupScannerBroadcast()     // ~5ms
    setupNavbar()               // ~10ms
    
    Log.d(TAG, "📱 BarcodeScannerTabsActivity created")
}
```

**Alur:**
1. ✅ Inflate layout
2. ✅ Setup toolbar
3. ✅ **Setup ViewModel** (akan trigger cache initialization)
4. ✅ Setup audio manager (ready untuk scan)
5. ✅ Setup broadcast receiver untuk scanner
6. ✅ Setup navbar

**Log Output:**
```
📱 BarcodeScannerTabsActivity created
✅ ViewModel initialized
🔊 Audio manager initialized at Activity onCreate (ready before first scan)
✅ Scanner broadcast receiver registered
🧭 Navbar setup completed
```

---

#### **1.2 ViewModel Initialization**
**File:** `BarcodeScannerViewModel.kt:118-130`

```kotlin
init {
    _scanCount.value = 0
    _lastScanTime.value = "Belum ada scan"
    _scannerType.value = "built-in"
    
    notificationManager = BarcodeNotificationManager(application)
    
    // ⚠️ Initialize cache dengan background loading
    initializeCacheAsync()  // ⚠️ See 1.3
    
    Log.d(TAG, "📱 BarcodeScannerViewModel initialized with Light Cache strategy")
}
```

**Alur:**
1. ✅ Initialize LiveData values
2. ✅ Initialize notification manager
3. ✅ **Initialize cache async** (background, non-blocking)

**Log Output:**
```
📱 BarcodeScannerViewModel initialized with Light Cache strategy
```

---

#### **1.3 Cache Initialization (Cache Kosong)**
**File:** `BarcodeScannerViewModel.kt:136-201`

```kotlin
private fun initializeCacheAsync() {
    viewModelScope.launch {
        try {
            // **OPTIMASI**: Load UI data dulu dari cache yang sudah ada (INSTANT)
            loadScanHistoryFromDatabase()        // ⚠️ See 1.3.1
            loadProcessedBarcodesFromDatabase()  // ⚠️ See 1.3.2
            
            // Check cache terlebih dahulu
            val cachedResi = BarcodeCacheManager.getAllResiRecords()
            val cachedExpedisi = BarcodeCacheManager.getAllExpedisiRecords()
            val resiCacheValid = BarcodeCacheManager.isResiCacheValid()
            val expedisiCacheValid = BarcodeCacheManager.isExpedisiCacheValid()
            
            // **CACHE KOSONG**: cachedResi.isEmpty() && cachedExpedisi.isEmpty()
            if (cachedResi.isEmpty() || cachedExpedisi.isEmpty() || !resiCacheValid || !expedisiCacheValid) {
                Log.d(TAG, "📦 Cache empty or expired, refreshing from Supabase (background)...")
                
                // Load dari repository (akan mengisi cache) - background, non-blocking
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val resiRecords = enhancedRepository.getBarcodeResiRecords()      // ⚠️ See 1.3.3
                        val expedisiRecords = enhancedRepository.getBarcodeExpedisiRecords()  // ⚠️ See 1.3.4
                        
                        // Pre-warm courier names cache
                        if (expedisiRecords.isNotEmpty()) {
                            val courierNames = BarcodeCacheManager.getUniqueCourierNamesFromCache()
                            if (courierNames.isNotEmpty()) {
                                BarcodeCacheManager.setCourierNamesCache(courierNames)
                            }
                        }
                        
                        // Reload UI data setelah cache di-update
                        loadScanHistoryFromDatabase()
                        loadProcessedBarcodesFromDatabase()
                        
                        Log.d(TAG, "✅ Cache refreshed: ${resiRecords.size} resi, ${expedisiRecords.size} expedisi")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error refreshing cache: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing cache: ${e.message}", e)
        }
    }
}
```

**Alur:**
1. ✅ Load UI data dari cache (kosong, tapi tidak error)
2. ✅ Check cache → **KOSONG**
3. ✅ **Background refresh** dari Supabase (non-blocking)
4. ✅ Update cache setelah data diterima

**Log Output:**
```
📱 Loaded 0 scan history items from cache
📱 Loaded 0 processed barcodes from cache (HashSet)
📦 Cache empty or expired, refreshing from Supabase (background)...
```

---

#### **1.3.1 Load Scan History (Cache Kosong)**
**File:** `BarcodeScannerViewModel.kt:206-300`

```kotlin
private fun loadScanHistoryFromDatabase() {
    viewModelScope.launch {
        try {
            val barcodeResiRecords = BarcodeCacheManager.getAllResiRecords()
            
            // **CACHE KOSONG**: barcodeResiRecords.isEmpty()
            if (barcodeResiRecords.isEmpty()) {
                Log.d(TAG, "📦 Cache empty, loading from repository...")
                enhancedRepository.getBarcodeResiRecords()      // Network call
                enhancedRepository.getBarcodeExpedisiRecords() // Network call
            }
            
            val cachedRecords = BarcodeCacheManager.getAllResiRecords()
            Log.d(TAG, "📦 Total records in cache: ${cachedRecords.size}")
            
            // **CACHE KOSONG**: cachedRecords.isEmpty()
            val scanHistoryItems = cachedRecords.map { ... } // Empty list
            
            _scanHistoryFromDB.value = emptyList()
            _scanHistory.value = emptyList()
            
            Log.d(TAG, "📱 Loaded 0 scan history items from cache")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading scan history: ${e.message}")
            _scanHistoryFromDB.value = emptyList()
            _scanHistory.value = emptyList()
        }
    }
}
```

**Alur:**
1. ✅ Check cache → **KOSONG**
2. ✅ Trigger repository load (akan mengisi cache di background)
3. ✅ Return empty list (UI ready dengan data kosong)

**Log Output:**
```
📦 Cache empty, loading from repository...
📦 Total records in cache: 0
📱 Loaded 0 scan history items from cache (0 with valid created, 0 without created)
```

---

#### **1.3.2 Load Processed Barcodes (Cache Kosong)**
**File:** `BarcodeScannerViewModel.kt:305-331`

```kotlin
private fun loadProcessedBarcodesFromDatabase() {
    viewModelScope.launch {
        try {
            val processedResiSet = BarcodeCacheManager.getProcessedResiSet()
            
            // **CACHE KOSONG**: processedResiSet.isEmpty()
            _processedBarcodesFromDB.value = emptySet()
            Log.d(TAG, "📱 Loaded 0 processed barcodes from cache (HashSet)")
            
            if (processedResiSet.isEmpty()) {
                Log.d(TAG, "📦 Cache empty, loading from repository...")
                val barcodeResiRecords = enhancedRepository.getBarcodeResiRecords()
                val expedisiRecords = enhancedRepository.getBarcodeExpedisiRecords()
                
                // Cache akan diupdate oleh repository
                val updatedSet = BarcodeCacheManager.getProcessedResiSet()
                _processedBarcodesFromDB.value = updatedSet
                Log.d(TAG, "📱 Updated processed barcodes from repository: ${updatedSet.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading processed barcodes: ${e.message}")
            _processedBarcodesFromDB.value = emptySet()
        }
    }
}
```

**Alur:**
1. ✅ Check cache → **KOSONG**
2. ✅ Return empty set (UI ready)
3. ✅ Trigger repository load (background)

**Log Output:**
```
📱 Loaded 0 processed barcodes from cache (HashSet)
📦 Cache empty, loading from repository...
```

---

#### **1.3.3 Repository Load Resi Records (Network Call)**
**File:** `EnhancedRepository.kt` (simulated)

**Waktu:** `T+50ms` - `T+500ms` (background, non-blocking)

```kotlin
suspend fun getBarcodeResiRecords(): List<BarcodeScanRecord> {
    // Network call ke Supabase
    val records = supabaseService.getAllBarcodeResiRecords()
    
    // Update cache
    BarcodeCacheManager.initializeCache(records, expedisiRecords)
    
    return records
}
```

**Alur:**
1. ✅ Network call ke Supabase (200-800ms)
2. ✅ Parse response
3. ✅ Update cache
4. ✅ Return records

**Log Output:**
```
🌐 Fetching barcode resi records from Supabase...
📥 Retrieved 150 barcode resi records
✅ Cache initialized: 150 resi records
```

---

#### **1.3.4 Repository Load Expedisi Records (Network Call)**
**File:** `EnhancedRepository.kt` (simulated)

**Waktu:** `T+50ms` - `T+600ms` (background, non-blocking)

```kotlin
suspend fun getBarcodeExpedisiRecords(): List<BarcodeSessionRecord> {
    // Network call ke Supabase (hanya flag=NO)
    val records = supabaseService.getAllBarcodeExpedisi(flag = "NO")
    
    // Update cache
    BarcodeCacheManager.initializeCache(resiRecords, records)
    
    return records
}
```

**Alur:**
1. ✅ Network call ke Supabase (filter flag=NO)
2. ✅ Parse response
3. ✅ Update cache
4. ✅ Return records

**Log Output:**
```
🌐 Fetching barcode expedisi records from Supabase (flag=NO)...
📥 Retrieved 500 expedisi records (flag=NO only)
✅ Cache initialized: 500 expedisi records (flag=NO only)
```

---

### **PHASE 2: Fragment Dibuka (UI Ready)**

**Waktu:** `T+50ms` - `T+100ms`

#### **2.1 Fragment.onViewCreated()**
**File:** `BarcodeInputFragment.kt:68-95`

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    viewModel = ViewModelProvider(requireActivity())[BarcodeScannerViewModel::class.java]
    
    // Get audio manager dari Activity (sudah ready)
    audioManager = (requireActivity() as BarcodeScannerTabsActivity).getAudioManager()
    
    expedisiValidator = ExpedisiValidator(requireContext(), audioManager = audioManager)
    
    initViews(view)
    setupObservers()
    setupClickListeners()
    setupSpinners()  // ⚠️ See 2.2
    updateTodayUnique()
}
```

**Alur:**
1. ✅ Get ViewModel dari Activity
2. ✅ Get audio manager (sudah ready)
3. ✅ Initialize expedisi validator
4. ✅ Setup UI components
5. ✅ **Setup spinners** (akan load expedisi data)

**Log Output:**
```
🔊 Using audio manager from Activity (already initialized)
```

---

#### **2.2 Setup Spinners (Load Expedisi Data)**
**File:** `BarcodeInputFragment.kt:113-160`

```kotlin
private fun setupSpinners() {
    viewLifecycleOwner.lifecycleScope.launch {
        try {
            // **CACHE KOSONG**: Load dari repository (akan trigger network call)
            val expedisiList = viewModel.loadExpedisiData()  // ⚠️ See 2.2.1
            
            if (expedisiList.isNotEmpty()) {
                val withHint = expedisiList + listOf("Pilih Expedisi")
                val expedisiAdapter = ArrayAdapter(...)
                spinnerExpedisi.adapter = expedisiAdapter
                spinnerExpedisi.setSelection(expedisiAdapter.count) // Show hint
                
                Log.d(TAG, "📋 Loaded ${expedisiList.size} expedisi from database")
            } else {
                Toast.makeText(ctx, "⚠️ No expedisi data found in database", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "❌ Error loading expedisi data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
```

**Alur:**
1. ✅ Load expedisi data dari ViewModel
2. ✅ Setup spinner adapter
3. ✅ Show hint "Pilih Expedisi"

**Log Output:**
```
📋 Loaded 0 expedisi from database
⚠️ No expedisi data found in database
```

**Note:** Jika cache masih kosong, expedisi list akan kosong. Setelah cache terisi (dari background refresh), spinner akan ter-update otomatis.

---

#### **2.2.1 Load Expedisi Data (Cache Kosong)**
**File:** `BarcodeScannerViewModel.kt:1276-1325`

```kotlin
suspend fun loadExpedisiData(): List<String> {
    return try {
        // **CACHE KOSONG**: cachedExpedisi.isEmpty()
        val cachedExpedisi = BarcodeCacheManager.getCourierNamesCache()
        val cachedExpedisiFromRecords = BarcodeCacheManager.getUniqueCourierNamesFromCache()
        
        val fastResult = when {
            cachedExpedisi.isNotEmpty() && BarcodeCacheManager.isCouriersCacheValid() -> {
                cachedExpedisi
            }
            cachedExpedisiFromRecords.isNotEmpty() -> {
                cachedExpedisiFromRecords
            }
            else -> {
                // **CACHE KOSONG**: Load dari repository (SLOW PATH)
                Log.d(TAG, "📋 No cache available, loading from repository...")
                enhancedRepository.getUniqueCourierNames()  // Network call
            }
        }
        
        // Background refresh jika cache expired
        if (fastResult.isNotEmpty() && (!BarcodeCacheManager.isCouriersCacheValid() || cachedExpedisi.isEmpty())) {
            viewModelScope.launch(Dispatchers.IO) {
                val refreshed = enhancedRepository.getUniqueCourierNames()
                Log.d(TAG, "✅ Background refresh completed: ${refreshed.size} expedisi")
            }
        }
        
        fastResult
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error loading expedisi data: ${e.message}")
        emptyList()
    }
}
```

**Alur:**
1. ✅ Check cache → **KOSONG**
2. ✅ Load dari repository (network call, blocking)
3. ✅ Return expedisi list (atau empty jika network gagal)

**Log Output:**
```
📋 No cache available, loading from repository...
🌐 Fetching unique courier names from Supabase...
📥 Retrieved 10 unique courier names
📋 Loaded 10 expedisi (Local-First)
```

---

### **PHASE 3: User Memilih Expedisi & Karung**

**Waktu:** `T+200ms` - `T+500ms`

#### **3.1 User Memilih Expedisi**
**File:** `BarcodeInputFragment.kt:190-205`

```kotlin
spinnerExpedisi.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val selectedExpedisi = parent?.getItemAtPosition(position) as? String
        selectedExpedisi?.let {
            viewModel.setSelectedExpedisi(it)
            tvSelectedExpedisi.text = it
            updateKarungOptionsForExpedition(it)  // ⚠️ See 3.2
        }
    }
}
```

**Alur:**
1. ✅ User pilih expedisi dari spinner
2. ✅ Update ViewModel
3. ✅ Update UI
4. ✅ **Update karung options**

**Log Output:**
```
📋 Selected expedisi: JNE
```

---

#### **3.2 Update Karung Options**
**File:** `BarcodeInputFragment.kt:424-488`

```kotlin
private fun updateKarungOptionsForExpedition(expedition: String) {
    lifecycleScope.launch {
        try {
            // Get karung options berdasarkan highest karung untuk expedisi ini
            var karungOptions = viewModel.getKarungOptionsForExpedition(expedition)
            
            if (karungOptions.isEmpty()) {
                karungOptions = listOf("1")
            }
            
            val withHint = karungOptions + listOf("Pilih Karung")
            val karungAdapter = ArrayAdapter(...)
            spinnerKarung.adapter = karungAdapter
            
            // Set default karung
            val defaultKarung = viewModel.getDefaultKarungForExpedition(expedition)
            val defaultIndex = karungOptions.indexOf(defaultKarung)
            if (defaultIndex >= 0) {
                spinnerKarung.setSelection(defaultIndex, false)
                viewModel.setSelectedKarung(defaultKarung)
                updateExpeditionInfo()  // ⚠️ See 3.3
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "❌ Error updating karung options: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

**Alur:**
1. ✅ Get karung options untuk expedisi
2. ✅ Setup spinner adapter
3. ✅ Set default karung
4. ✅ **Update expedition info**

**Log Output:**
```
📦 Generated 100 karung options for expedition 'JNE' (highest: 50)
📦 Default karung for expedition 'JNE' (today): 51 (highest: 50, karungs: [1, 2, ..., 50])
```

---

#### **3.3 Update Expedition Info**
**File:** `BarcodeInputFragment.kt:490-539`

```kotlin
private fun updateExpeditionInfo() {
    val expedition = viewModel.selectedExpedisiLive.value ?: ""
    val karung = viewModel.selectedKarungLive.value ?: ""
    
    lifecycleScope.launch {
        try {
            // Get summary dari ViewModel
            val summary = withContext(Dispatchers.IO) {
                viewModel.getExpeditionSummaryToday(expedition)  // ⚠️ See 3.3.1
            }
            
            val total = summary["total"] ?: 0
            val scanned = summary["scanned"] ?: 0
            val remaining = summary["remaining"] ?: 0
            
            // Update UI
            withContext(Dispatchers.Main) {
                tvSummaryInfo.text = "Total: $total - Scan: $scanned - Sisa: $remaining"
                
                val conCount = withContext(Dispatchers.IO) {
                    viewModel.getCountByExpeditionAndKarung(expedition, karung)
                }
                tvScanCount.text = conCount.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting expedition summary: ${e.message}", e)
        }
    }
}
```

**Alur:**
1. ✅ Get summary dari ViewModel (dari cache)
2. ✅ Update UI dengan total, scanned, remaining
3. ✅ Update CON count

**Log Output:**
```
📊 Getting summary for expedition: 'JNE'
📊   - TOTAL from Supabase (ALL flags, today): 200
📊   - SCANNED from cache (schedule=ontime, today): 50
📊   - SISA from cache (flag=NO, today): 150
📊 ✅ UI Updated - Summary for JNE: Total=200, Scan=50, Sisa=150
```

---

### **PHASE 4: User Scan Barcode**

**Waktu:** `T+1000ms` (setelah cache terisi)

#### **4.1 Trigger Scan (KeyDown/KeyUp)**
**File:** `BarcodeScannerTabsActivity.kt:337-358`

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    val isTrigger = keyCode == 293
    if (!isTrigger) return super.onKeyDown(keyCode, event)

    if (event?.repeatCount == 0) {
        Log.d(TAG, "🔑 KeyDown trigger: $keyCode (repeat=0) → authorize broadcast")
        allowNextBroadcast = true  // ⚠️ Authorize next broadcast
    }
    return false
}

override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    val isTrigger = keyCode == 293
    if (!isTrigger) return super.onKeyUp(keyCode, event)

    Log.d(TAG, "🔑 KeyUp trigger: $keyCode → authorize next broadcast (one-shot)")
    allowNextBroadcast = true
    return false
}
```

**Alur:**
1. ✅ User tekan trigger button (keycode 293)
2. ✅ Set `allowNextBroadcast = true`
3. ✅ Scanner mengirim broadcast dengan barcode

**Log Output:**
```
🔑 KeyDown trigger: 293 (repeat=0) → authorize broadcast
🔑 KeyUp trigger: 293 → authorize next broadcast (one-shot)
```

---

#### **4.2 Broadcast Receiver Menerima Barcode**
**File:** `BarcodeScannerTabsActivity.kt:80-99`

```kotlin
private fun setupScannerBroadcast() {
    scannerBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.scanner.broadcast") {
                val barcode = intent.getStringExtra("data")?.trim().orEmpty()
                if (barcode.isNotEmpty()) {
                    if (allowNextBroadcast) {
                        Log.d(TAG, "📡 Broadcast scan received (one-shot latch): $barcode")
                        allowNextBroadcast = false  // Reset latch
                        viewModel.processScannedBarcode(barcode)  // ⚠️ See 4.3
                    } else {
                        Log.d(TAG, "⚠️ Broadcast ignored (latch not set)")
                    }
                }
            }
        }
    }
}
```

**Alur:**
1. ✅ Broadcast receiver menerima barcode
2. ✅ Check `allowNextBroadcast` → **TRUE**
3. ✅ Reset latch
4. ✅ **Process scanned barcode**

**Log Output:**
```
📡 Broadcast scan received (one-shot latch): SPXID12345678901A
```

---

#### **4.3 Process Scanned Barcode**
**File:** `BarcodeScannerViewModel.kt:372-417`

```kotlin
fun processScannedBarcode(barcode: String, scanDuration: Long = 0L, scannerType: String = "built-in") {
    val trimmedBarcode = barcode.trim()
    val currentTime = System.currentTimeMillis()
    
    Log.d(TAG, "📱 Processing barcode: $trimmedBarcode")
    
    // **DEBOUNCE**: Cek apakah masih memproses
    if (isProcessing) {
        Log.w(TAG, "⏳ Masih memproses scan sebelumnya. Barcode $trimmedBarcode ditolak")
        return
    }
    
    // **DEBOUNCE**: Cek minimum interval
    val timeSinceLastScan = currentTime - lastScanCompleteTime
    if (timeSinceLastScan < MIN_SCAN_INTERVAL_MS && lastScanCompleteTime > 0) {
        Log.w(TAG, "⏳ Terlalu cepat! Tunggu ${remainingTime}ms sebelum scan berikutnya")
        return
    }
    
    // **PROSES LANGSUNG**
    processBarcodeInternal(trimmedBarcode, scanDuration, scannerType)  // ⚠️ See 4.4
}
```

**Alur:**
1. ✅ Trim barcode
2. ✅ Check debounce (tidak sedang memproses, interval cukup)
3. ✅ **Process barcode internal**

**Log Output:**
```
📱 Processing barcode: SPXID12345678901A
📱 Barcode length: 18
📱 Scanner type: built-in
📱 Is processing: false
📱 Time since last scan: 0ms
```

---

#### **4.4 Process Barcode Internal**
**File:** `BarcodeScannerViewModel.kt:422-546`

```kotlin
private fun processBarcodeInternal(barcode: String, scanDuration: Long, scannerType: String) {
    isProcessing = true
    
    viewModelScope.launch {
        try {
            val trimmedBarcode = barcode.trim()
            
            // **VALIDASI AWAL**: Cek expedisi dan karung sudah dipilih
            val expedisiError = when {
                selectedExpedisi.isNullOrEmpty() -> "Mohon pilih Expedisi terlebih dahulu."
                selectedExpedisi == "Pilih Expedisi" -> "Mohon pilih Expedisi terlebih dahulu."
                else -> null
            }
            
            val karungError = when {
                selectedKarung.isNullOrEmpty() -> "Mohon pilih No Karung terlebih dahulu."
                selectedKarung == "Pilih Karung" -> "Mohon pilih No Karung terlebih dahulu."
                else -> null
            }
            
            if (expedisiError != null || karungError != null) {
                Log.w(TAG, "🛑 [BLOCKED] $errorMessage - scan tidak dapat diproses")
                _scanResult.value = ScanResult(
                    status = "error",
                    message = errorMessage,
                    Resi = trimmedBarcode,
                    timestamp = System.currentTimeMillis()
                )
                isProcessing = false
                return@launch
            }
            
            // **SATU JALUR VALIDASI**: ExpedisiValidator
            val validationResult = expedisiValidator.validateBarcode(
                barcode = trimmedBarcode,
                selectedExpedisi = selectedExpedisi,
                selectedKarung = selectedKarung
            )  // ⚠️ See 4.5
            
            when (validationResult.status) {
                ExpedisiValidator.ValidationStatus.OK -> {
                    // **VALIDASI OK**: Data disimpan
                    processSuccessfulScan(
                        barcode = trimmedBarcode,
                        scanDuration = scanDuration,
                        scannerType = scannerType,
                        validationResult = validationResult
                    )  // ⚠️ See 4.6
                    isProcessing = false
                    lastScanCompleteTime = System.currentTimeMillis()
                }
                else -> {
                    // Validasi gagal → TIDAK menyimpan
                    _scanResult.value = ScanResult(
                        status = "error",
                        message = validationResult.message,
                        Resi = trimmedBarcode,
                        timestamp = System.currentTimeMillis()
                    )
                    isProcessing = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing barcode: ${e.message}", e)
            isProcessing = false
        }
    }
}
```

**Alur:**
1. ✅ Set `isProcessing = true`
2. ✅ Validasi expedisi & karung sudah dipilih
3. ✅ **Validasi barcode** via ExpedisiValidator
4. ✅ Jika valid → **Process successful scan**
5. ✅ Jika tidak valid → Return error

**Log Output:**
```
📱 Setting isProcessing to true for: SPXID12345678901A
✅ Validasi OK, data akan disimpan: SPXID12345678901A
```

---

#### **4.5 ExpedisiValidator.validateBarcode()**
**File:** `ExpedisiValidator.kt` (simulated)

```kotlin
fun validateBarcode(barcode: String, selectedExpedisi: String?, selectedKarung: String?): ValidationResult {
    // 1. Cek format barcode
    if (!isValidFormat(barcode)) {
        return ValidationResult(INVALID_FORMAT, "Format barcode tidak valid")
    }
    
    // 2. Cek duplicate (dari cache tbl_resi, 7 hari terakhir)
    if (BarcodeCacheManager.isResiProcessed(barcode)) {
        val existingRecord = BarcodeCacheManager.getResiRecord(barcode)
        return ValidationResult(DUPLICATE_PROCESSED, "Resi sudah diproses", existingRecord)
    }
    
    // 3. Cek NOT_FOUND (dari cache tbl_expedisi, flag=NO)
    val expedisiRecord = BarcodeCacheManager.getExpedisiRecordByResino(barcode)
    if (expedisiRecord == null) {
        return ValidationResult(NOT_FOUND_IN_EXPEDISI, "Resi tidak ditemukan di expedisi")
    }
    
    // 4. Cek mismatch expedisi
    if (expedisiRecord.couriername != selectedExpedisi) {
        return ValidationResult(MISMATCH_EXPEDISI, "Resi tidak sesuai expedisi yang dipilih")
    }
    
    // 5. VALIDASI OK
    return ValidationResult(
        status = OK,
        message = "Validasi berhasil",
        resiDetails = expedisiRecord,
        actualCourierName = expedisiRecord.couriername,
        actualSchedule = expedisiRecord.schedule ?: "ontime"
    )
}
```

**Alur:**
1. ✅ Validasi format
2. ✅ Cek duplicate (dari cache)
3. ✅ Cek NOT_FOUND (dari cache)
4. ✅ Cek mismatch expedisi
5. ✅ Return validation result

**Log Output:**
```
🔍 Validating barcode: SPXID12345678901A
✅ Format valid
✅ Not duplicate
✅ Found in expedisi cache (flag=NO)
✅ Expedisi match: JNE
✅ Validation OK
```

---

#### **4.6 Process Successful Scan**
**File:** `BarcodeScannerViewModel.kt:553-651`

```kotlin
private suspend fun processSuccessfulScan(
    barcode: String,
    scanDuration: Long,
    scannerType: String,
    validationResult: ExpedisiValidator.ValidationResult
) {
    try {
        // **GUARD**: Pastikan hanya menyimpan jika VALIDASI OK
        if (validationResult.status != ExpedisiValidator.ValidationStatus.OK) {
            return
        }
        
        Log.d(TAG, "✅ [VALIDASI OK] Saving data for resi: $barcode")
        
        // **LOCAL-FIRST STRATEGY**: Biarkan Repository handle (cache first, lalu sync)
        val createdTime = java.time.Instant.now().toString()
        val barcodeRecord = BarcodeSupabaseService.BarcodeScanRecord(
            id = "${barcode}_${System.currentTimeMillis()}",
            Resi = barcode,
            created = createdTime,
            Keterangan = validationResult.actualCourierName ?: scannerType,
            nokarung = selectedKarung,
            schedule = validationResult.actualSchedule ?: "ontime"
        )
        
        // **LOCAL-FIRST**: Save via Repository (cache first, network background)
        val cacheUpdated = enhancedRepository.saveBarcodeResiRecord(barcodeRecord)  // ⚠️ See 4.6.1
        
        if (!cacheUpdated) {
            Log.e(TAG, "❌ Cache update failed for: $barcode")
            _scanResult.value = ScanResult(
                status = "error",
                message = "Gagal menyimpan ke cache lokal",
                Resi = barcode,
                timestamp = System.currentTimeMillis()
            )
            return
        }
        
        // **OPTIMISTIC UI UPDATE**: Update UI INSTAN (cache sudah di-update)
        val processedCount = BarcodeCacheManager.getProcessedResiSet().size
        _scanCount.value = processedCount
        _processedBarcodesFromDB.value = BarcodeCacheManager.getProcessedResiSet()
        
        // Update last scan time
        val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        _lastScanTime.value = timeFormat.format(Date())
        
        // **IMMEDIATE UI FEEDBACK**: Update scanResult
        _scanResult.value = ScanResult(
            status = "success",
            message = "Barcode $barcode berhasil di-scan dan disimpan",
            Resi = barcode,
            created = createdTime,
            Keterangan = validationResult.actualCourierName ?: scannerType,
            nokarung = selectedKarung,
            schedule = validationResult.actualSchedule ?: "ontime",
            timestamp = System.currentTimeMillis()
        )
        
        Log.d(TAG, "⚡ [LOCAL-FIRST] Cache updated instantly, Supabase sync in background...")
        
        // **NON-BLOCKING**: Update flag di background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateExpedisiFlag(barcode)  // ⚠️ See 4.6.2
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating flag in background: ${e.message}", e)
            }
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error processing barcode: ${e.message}")
        _scanResult.value = ScanResult(
            status = "error",
            message = "Gagal menyimpan barcode: ${e.message}",
            Resi = barcode,
            timestamp = System.currentTimeMillis()
        )
    }
}
```

**Alur:**
1. ✅ Create barcode record
2. ✅ **Save via Repository** (cache first, network background)
3. ✅ Update UI instantly (optimistic)
4. ✅ Update scan result LiveData
5. ✅ **Update expedisi flag** di background

**Log Output:**
```
✅ [VALIDASI OK] Saving data for resi: SPXID12345678901A
⚡ [LOCAL-FIRST] Cache updated instantly, Supabase sync in background...
✅ ScanResult updated with status=success, timestamp=..., Resi=SPXID12345678901A
```

---

#### **4.6.1 Repository Save (Cache First)**
**File:** `EnhancedRepository.kt` (simulated)

```kotlin
suspend fun saveBarcodeResiRecord(record: BarcodeScanRecord): Boolean {
    // **CACHE FIRST**: Update cache instantly
    BarcodeCacheManager.addResiRecord(record)
    
    // **BACKGROUND SYNC**: Post ke Supabase di background (non-blocking)
    viewModelScope.launch(Dispatchers.IO) {
        try {
            supabaseService.postBarcodeResiRecord(record)
            Log.d(TAG, "✅ Synced to Supabase: ${record.Resi}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync failed: ${e.message}")
            // Background sync akan retry otomatis
        }
    }
    
    return true  // Optimistic: selalu return true karena cache sudah di-update
}
```

**Alur:**
1. ✅ **Update cache instantly** (~1ms)
2. ✅ Post ke Supabase di background (non-blocking)
3. ✅ Return true (optimistic)

**Log Output:**
```
💾 Adding resi record to cache: SPXID12345678901A
✅ Cache updated: 151 resi records
🌐 Syncing to Supabase in background...
```

---

#### **4.6.2 Update Expedisi Flag**
**File:** `BarcodeScannerViewModel.kt:972-991`

```kotlin
private suspend fun updateExpedisiFlag(barcode: String) {
    try {
        Log.d(TAG, "🔄 Updating flag in tbl_expedisi for barcode: $barcode")
        
        // Update flag di local database
        val success = enhancedRepository.updateExpedisiFlag(barcode, "YES")
        
        if (success) {
            Log.d(TAG, "✅ Flag updated to YES for barcode: $barcode")
            
            // **PENTING**: Remove dari expedisi cache (karena flag berubah ke YES)
            BarcodeCacheManager.removeExpedisiRecord(barcode)
            
            Log.d(TAG, "🗑️ Removed expedisi record from cache (flag changed to YES): $barcode")
        } else {
            Log.w(TAG, "⚠️ Failed to update flag for barcode: $barcode")
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error updating expedisi flag: ${e.message}", e)
    }
}
```

**Alur:**
1. ✅ Update flag di repository (cache + Supabase)
2. ✅ **Remove dari expedisi cache** (karena flag berubah ke YES)
3. ✅ Log success

**Log Output:**
```
🔄 Updating flag in tbl_expedisi for barcode: SPXID12345678901A
✅ Flag updated to YES for barcode: SPXID12345678901A
🗑️ Removed expedisi record from cache (flag changed to YES): SPXID12345678901A
```

---

### **PHASE 5: UI Update (Observer)**

**Waktu:** `T+1050ms` - `T+1100ms`

#### **5.1 ScanResult Observer**
**File:** `BarcodeInputFragment.kt:235-317`

```kotlin
viewModel.scanResult.observe(viewLifecycleOwner) { result ->
    result?.let {
        Log.d(TAG, "🔍 ScanResult observer triggered: status=${it.status}, Resi=${it.Resi}")
        
        // Skip jika value lama
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - result.timestamp
        if (timeDiff > 30_000 || result.timestamp <= lastProcessedTimestamp) {
            return@let
        }
        
        lastProcessedTimestamp = result.timestamp
        
        // Play appropriate audio and show toast
        when (it.status) {
            "success" -> {
                Log.d(TAG, "🔊 Playing success beep and showing toast for: ${it.Resi}")
                audioManager.playSuccessBeep()
                BarcodeToastManager.showSuccess(requireContext(), "Scan berhasil: ${it.Resi}")
                etBarcodeInput.setText("")
                etBarcodeInput.requestFocus()
                updateTodayUnique()
            }
            "duplicate" -> {
                audioManager.playDoubleBeep()
                BarcodeToastManager.showError(requireContext(), it.message ?: "Resi sudah diproses")
                etBarcodeInput.setText("")
                etBarcodeInput.requestFocus()
            }
            "error" -> {
                audioManager.playFailureBeep()
                BarcodeToastManager.showError(requireContext(), it.message ?: "Terjadi kesalahan")
                etBarcodeInput.setText("")
                etBarcodeInput.requestFocus()
            }
            "mismatch" -> {
                audioManager.playMismatchBeep()
                BarcodeToastManager.showError(requireContext(), it.message ?: "Resi tidak sesuai ekspedisi")
                etBarcodeInput.setText("")
                etBarcodeInput.requestFocus()
            }
        }
        
        // Refresh metrics after scan
        lifecycleScope.launch {
            kotlinx.coroutines.delay(100)
            updateExpeditionInfo()
        }
    }
}
```

**Alur:**
1. ✅ Observer triggered oleh LiveData update
2. ✅ Check timestamp (skip jika lama)
3. ✅ **Play audio** sesuai status
4. ✅ **Show toast** sesuai status
5. ✅ Clear input field
6. ✅ **Refresh expedition info**

**Log Output:**
```
🔍 ScanResult observer triggered: status=success, Resi=SPXID12345678901A
🔊 Playing success beep and showing toast for: SPXID12345678901A
🔄 Expedition info refreshed after scan
```

---

## 📊 **SUMMARY TIMELINE**

| Waktu | Phase | Aktivitas | Durasi |
|-------|-------|-----------|--------|
| T+0ms | 1.1 | Activity.onCreate() | ~50ms |
| T+0ms | 1.2 | ViewModel.init() | ~10ms |
| T+0ms | 1.3 | Cache initialization (kosong) | ~5ms (instant) |
| T+50ms | 1.3.3 | Background: Load resi records | ~500ms (non-blocking) |
| T+50ms | 1.3.4 | Background: Load expedisi records | ~600ms (non-blocking) |
| T+50ms | 2.1 | Fragment.onViewCreated() | ~50ms |
| T+100ms | 2.2 | Setup spinners (load expedisi) | ~400ms (blocking jika cache kosong) |
| T+500ms | 3.1 | User pilih expedisi | ~10ms |
| T+510ms | 3.2 | Update karung options | ~100ms |
| T+610ms | 3.3 | Update expedition info | ~200ms |
| T+1000ms | 4.1 | User tekan trigger | ~10ms |
| T+1010ms | 4.2 | Broadcast receiver | ~5ms |
| T+1015ms | 4.3 | Process scanned barcode | ~5ms |
| T+1020ms | 4.4 | Process barcode internal | ~10ms |
| T+1030ms | 4.5 | ExpedisiValidator.validateBarcode() | ~50ms (cache lookup) |
| T+1080ms | 4.6 | Process successful scan | ~20ms |
| T+1080ms | 4.6.1 | Repository save (cache first) | ~1ms (instant) |
| T+1080ms | 4.6.2 | Update expedisi flag (background) | ~200ms (non-blocking) |
| T+1100ms | 5.1 | UI update (observer) | ~50ms |

---

## 🔑 **POIN PENTING**

### **1. Cache Kosong → Background Refresh**
- ✅ UI ready **INSTANT** (dengan data kosong)
- ✅ Background refresh dari Supabase (non-blocking)
- ✅ UI auto-update setelah cache terisi

### **2. Local-First Strategy**
- ✅ Cache update **INSTANT** (~1ms)
- ✅ Network sync di background (non-blocking)
- ✅ UI optimistic update

### **3. Validasi dari Cache**
- ✅ Duplicate check: dari cache tbl_resi
- ✅ NOT_FOUND check: dari cache tbl_expedisi (flag=NO)
- ✅ Mismatch check: dari cache tbl_expedisi

### **4. Expedisi Flag Update**
- ✅ Flag di-update ke "YES" setelah scan berhasil
- ✅ Record dihapus dari expedisi cache (karena flag=YES)
- ✅ Update di background (non-blocking)

---

## ⚠️ **CATATAN**

1. **Cache Kosong**: Jika cache kosong saat activity dibuka, UI akan ready dengan data kosong, lalu auto-update setelah background refresh selesai.

2. **Network Latency**: Background refresh membutuhkan waktu 200-800ms tergantung koneksi. UI tetap responsive karena non-blocking.

3. **Validasi dari Cache**: Semua validasi dilakukan dari cache (fast lookup), bukan dari network. Jika cache kosong, validasi akan gagal dengan NOT_FOUND.

4. **Optimistic UI Update**: UI di-update instantly setelah cache di-update, tidak menunggu network sync selesai.

---

## 📝 **KESIMPULAN**

**Flow saat cache kosong:**
1. ✅ Activity dibuka → ViewModel init → Cache check (kosong)
2. ✅ Background refresh dari Supabase (non-blocking)
3. ✅ UI ready dengan data kosong (instant)
4. ✅ User pilih expedisi & karung
5. ✅ User scan barcode
6. ✅ Validasi dari cache (jika cache sudah terisi)
7. ✅ Save ke cache (instant)
8. ✅ UI update (instant)
9. ✅ Network sync di background

**Total waktu untuk scan pertama (jika cache sudah terisi):** ~100-150ms
**Total waktu untuk scan pertama (jika cache masih kosong):** Validasi akan gagal dengan NOT_FOUND (karena cache belum terisi)

