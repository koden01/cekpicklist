# 📱 **ALUR LENGKAP: Barcode Scanner dari Pertama Dibuka**

## 🎯 **OVERVIEW**

Dokumen ini menelusuri alur lengkap Barcode Scanner mulai dari Activity dibuka hingga scan selesai, dengan detail setiap langkah yang dilakukan.

---

## 📍 **TIMELINE TOTAL**

**Best Case:** ~300-500ms (fast device, cache ready)  
**Normal Case:** ~500-1000ms (normal device, cache ready)  
**Worst Case:** ~1500-3000ms (slow device, cache miss, network call)

---

## 🔄 **PHASE 1: ACTIVITY CREATION (onCreate)**

### **1.1 BarcodeScannerTabsActivity.onCreate()**

**File:** `BarcodeScannerTabsActivity.kt:65-79`

**Waktu:** ~50-200ms

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)                    // ~5-10ms
    setContentView(R.layout.activity_barcode_scanner_tabs) // ~20-50ms (layout inflation)
    
    setupToolbar()           // ⚠️ See 1.2
    setupViewModel()         // ⚠️ See 1.3
    setupAudioManager()      // ⚠️ See 1.4
    setupBuiltInBarcodeManager() // ⚠️ See 1.5
    setupNavbar()           // ⚠️ See 1.6
    
    Log.d(TAG, "📱 BarcodeScannerTabsActivity created")
}
```

**Alur:**
1. ✅ Load layout XML → inflate view hierarchy
2. ✅ Setup toolbar
3. ✅ Setup ViewModel (critical)
4. ✅ Setup Audio Manager (critical)
5. ✅ Setup Barcode Manager (critical)
6. ✅ Setup Navbar

---

### **1.2 setupToolbar()**

**File:** `BarcodeScannerTabsActivity.kt:156-160`

**Waktu:** ~5-10ms

```kotlin
private fun setupToolbar() {
    setSupportActionBar(findViewById(R.id.toolbar))         // ~2-5ms
    supportActionBar?.setDisplayHomeAsUpEnabled(true)        // ~1ms
    supportActionBar?.title = "Barcode Scanner"             // ~1ms
}
```

**Alur:**
1. ✅ Set toolbar sebagai action bar
2. ✅ Enable back button
3. ✅ Set title

---

### **1.3 setupViewModel()**

**File:** `BarcodeScannerTabsActivity.kt:162-172`

**Waktu:** ~50-200ms (tergantung ViewModel init)

```kotlin
private fun setupViewModel() {
    viewModel = ViewModelProvider(this)[BarcodeScannerViewModel::class.java]
    // ⚠️ ViewModel instantiation (lihat 1.3.1)
    
    viewModel.initializeBackgroundSync()                    // Background
    viewModel.initializeExpedisiValidator(this)             // ~10-50ms
    
    Log.d(TAG, "✅ ViewModel initialized")
}
```

#### **1.3.1 BarcodeScannerViewModel.init{}**

**File:** `BarcodeScannerViewModel.kt:120-134`

**Waktu:** ~30-150ms

```kotlin
init {
    _scanCount.value = 0                                     // ~1ms
    _lastScanTime.value = "Belum ada scan"                  // ~1ms
    _scannerType.value = "built-in"                          // ~1ms
    
    // Initialize audio and notification managers
    audioManager = BarcodeAudioManager(application)         // ~5-10ms
    notificationManager = BarcodeNotificationManager(application) // ~1-5ms
    audioManager.initAudio()                                 // ~10-50ms (SoundPool)
    
    // Initialize cache dengan background loading
    initializeCacheAsync()                                   // Background
    
    Log.d(TAG, "📱 BarcodeScannerViewModel initialized")
}
```

**Alur:**
1. ✅ Initialize LiveData (scanCount, lastScanTime, scannerType)
2. ✅ Initialize Audio Manager (app-wide)
3. ✅ Initialize Notification Manager
4. ✅ Initialize Audio System (SoundPool + load sounds)
5. ✅ Initialize Cache Async (background)

#### **1.3.2 initializeCacheAsync()**

**File:** `BarcodeScannerViewModel.kt:140-190`

**Waktu:** Background (non-blocking)

```kotlin
private fun initializeCacheAsync() {
    viewModelScope.launch {
        // Load UI data dulu dari cache yang sudah ada (INSTANT)
        loadScanHistoryFromDatabase()                        // ~5-20ms
        loadProcessedBarcodesFromDatabase()                  // ~5-20ms
        
        // Check cache validity
        val cachedResi = BarcodeCacheManager.getAllResiRecords()
        val cachedExpedisi = BarcodeCacheManager.getAllExpedisiRecords()
        val resiCacheValid = BarcodeCacheManager.isResiCacheValid()
        val expedisiCacheValid = BarcodeCacheManager.isExpedisiCacheValid()
        
        // Jika cache valid, langsung gunakan (INSTANT)
        if (cachedResi.isNotEmpty() && cachedExpedisi.isNotEmpty() && 
            resiCacheValid && expedisiCacheValid) {
            Log.d(TAG, "⚡ Using existing valid cache")
            return@launch
        }
        
        // Cache kosong/expired - refresh di background
        forceRefreshData()                                   // Background
    }
}
```

**Alur:**
1. ✅ Load scan history dari database (Room)
2. ✅ Load processed barcodes dari database
3. ✅ Check cache validity (SharedPreferences)
4. ✅ Jika cache valid → gunakan cache (INSTANT)
5. ✅ Jika cache invalid → refresh di background (non-blocking)

---

### **1.4 setupAudioManager()**

**File:** `BarcodeScannerTabsActivity.kt:174-180`

**Waktu:** ~10-50ms

```kotlin
private fun setupAudioManager() {
    audioManager = BarcodeAudioManager(this)                // ~5-10ms
    audioManager.initAudio()                                 // ~10-50ms
    // ⚠️ See BarcodeAudioManager.initAudio()
    Log.d(TAG, "🔊 Audio manager initialized at Activity onCreate")
}
```

#### **1.4.1 BarcodeAudioManager.initAudio()**

**File:** `BarcodeAudioManager.kt:38-94`

**Waktu:** ~10-50ms

```kotlin
fun initAudio() {
    // Initialize sound settings manager
    soundSettingsManager = SoundSettingsManager(context)     // ~1-5ms
    
    if (soundPool != null && isSoundPoolReady) {
        return  // Already initialized
    }
    
    // Create SoundPool
    soundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(...)
        .build()                                            // ~5-10ms
    
    // Set load complete listener
    soundPool?.setOnLoadCompleteListener { ... }            // ~1ms
    
    // Load sound files (5 sounds)
    soundMap[SOUND_SUCCESS] = soundPool?.load(context, R.raw.beep_success, 1)  // ~2-10ms
    soundMap[SOUND_FAILURE] = soundPool?.load(context, R.raw.beep_failure, 1)  // ~2-10ms
    soundMap[SOUND_DOUBLE] = soundPool?.load(context, R.raw.beep_double, 1)    // ~2-10ms
    soundMap[SOUND_START] = soundPool?.load(context, R.raw.beep_start, 1)      // ~2-10ms
    soundMap[SOUND_SABAR] = soundPool?.load(context, R.raw.failure, 1)         // ~2-10ms
    
    // Get AudioManager
    audioManager = context.getSystemService(Context.AUDIO_SERVICE) // ~1ms
}
```

**Alur:**
1. ✅ Initialize Sound Settings Manager
2. ✅ Create SoundPool (5 streams max)
3. ✅ Set load complete listener
4. ✅ Load 5 sound files (success, failure, double, start, sabar)
5. ✅ Get AudioManager system service
6. ✅ Wait for all sounds loaded → `isSoundPoolReady = true`

**Catatan:** Sound loading adalah async, jadi `isSoundPoolReady` akan `true` setelah semua sound loaded.

---

### **1.5 setupBuiltInBarcodeManager()**

**File:** `BarcodeScannerTabsActivity.kt:332-357`

**Waktu:** ~10-30ms

```kotlin
private fun setupBuiltInBarcodeManager() {
    builtInBarcodeManager = BuiltInBarcodeManager(this, this) // ~5-15ms
    
    // Set barcode callback
    builtInBarcodeManager.setBarcodeCallback { barcode ->
        if (allowNextBroadcast) {
            allowNextBroadcast = false
            viewModel.processScannedBarcode(barcode)          // ⚠️ See Phase 3
        }
    }
    
    // Set scan state callback
    builtInBarcodeManager.setScanStateCallback { isActive ->
        isScanning = isActive
        viewModel.setScanningState(isActive)
    }
    
    Log.d(TAG, "✅ BuiltInBarcodeManager initialized")
}
```

**Alur:**
1. ✅ Create BuiltInBarcodeManager instance
2. ✅ Set barcode callback (untuk menerima hasil scan)
3. ✅ Set scan state callback (untuk tracking scanning state)
4. ✅ Setup broadcast receiver (internal)

---

### **1.6 setupNavbar()**

**File:** `BarcodeScannerTabsActivity.kt:182-201`

**Waktu:** ~10-20ms

```kotlin
private fun setupNavbar() {
    navInput = findViewById(R.id.navInput)                  // ~1-2ms
    navDashboard = findViewById(R.id.navDashboard)          // ~1-2ms
    navHistory = findViewById(R.id.navHistory)               // ~1-2ms
    contentFrame = findViewById(R.id.contentFrame)          // ~1-2ms
    swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout) // ~1-2ms
    
    setupSwipeRefresh()                                      // ~2-5ms
    
    // Set click listeners
    navInput.setOnClickListener { switchToTab(0) }          // ~1ms
    navHistory.setOnClickListener { switchToTab(1) }        // ~1ms
    
    // Set initial selection
    switchToTab(0)                                          // ⚠️ See 1.6.1
    
    Log.d(TAG, "🧭 Navbar setup completed")
}
```

#### **1.6.1 switchToTab(0)**

**File:** `BarcodeScannerTabsActivity.kt:245-301`

**Waktu:** ~20-50ms

```kotlin
private fun switchToTab(position: Int) {
    // Update navbar selection
    navInput.isSelected = (position == 0)                   // ~1ms
    navHistory.isSelected = (position == 1)                 // ~1ms
    
    // Update text dan icon color
    // ... (UI updates)                                      // ~5-10ms
    
    // Switch fragment
    when (position) {
        0 -> showFragment(BarcodeInputFragment())           // ⚠️ See Phase 2
        1 -> showFragment(BarcodeHistoryFragment())
    }
    
    Log.d(TAG, "🔄 Switched to tab: $position")
}
```

**Alur:**
1. ✅ Update navbar selection state
2. ✅ Update text dan icon colors
3. ✅ Show fragment (BarcodeInputFragment)

---

## 🔄 **PHASE 2: FRAGMENT CREATION**

### **2.1 BarcodeInputFragment.onCreateView()**

**File:** `BarcodeInputFragment.kt:46-52`

**Waktu:** ~20-50ms

```kotlin
override fun onCreateView(...): View? {
    return inflater.inflate(R.layout.fragment_barcode_input, container, false)
    // ⚠️ Layout inflation
}
```

**Alur:**
1. ✅ Inflate fragment layout XML
2. ✅ Create view hierarchy

---

### **2.2 BarcodeInputFragment.onViewCreated()**

**File:** `BarcodeInputFragment.kt:66-93`

**Waktu:** ~100-300ms

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    viewModel = ViewModelProvider(requireActivity())[BarcodeScannerViewModel::class.java] // ~1-5ms
    
    // Gunakan audio manager dari Activity
    val activity = requireActivity() as? BarcodeScannerTabsActivity
    if (activity != null) {
        audioManager = activity.getAudioManager()           // ✅ Reuse dari Activity
    }
    
    // Initialize expedisi validator
    expedisiValidator = ExpedisiValidator(requireContext(), null, null, audioManager) // ~5-10ms
    
    initViews(view)                                         // ⚠️ See 2.2.1
    setupObservers()                                        // ⚠️ See 2.2.2
    setupClickListeners()                                   // ~1-5ms
    setupSpinners()                                         // ⚠️ See 2.2.3
    updateTodayUnique()                                     // Background
}
```

#### **2.2.1 initViews()**

**File:** `BarcodeInputFragment.kt:95-109`

**Waktu:** ~10-20ms

```kotlin
private fun initViews(view: View) {
    tvScanCount = view.findViewById(R.id.tvScanCount)       // ~1ms
    tvSelectedExpedisi = view.findViewById(R.id.tvSelectedExpedisi) // ~1ms
    tvSummaryInfo = view.findViewById(R.id.tvSummaryInfo)   // ~1ms
    tvTodayUnique = view.findViewById(R.id.tvTodayUnique)   // ~1ms
    spinnerExpedisi = view.findViewById(R.id.spinnerExpedisi) // ~1ms
    spinnerKarung = view.findViewById(R.id.spinnerKarung)  // ~1ms
    etBarcodeInput = view.findViewById(R.id.etBarcodeInput) // ~1ms
    progressBar = view.findViewById(R.id.progressBar)      // ~1ms
    
    // Disable soft keyboard
    etBarcodeInput.showSoftInputOnFocus = false            // ~1ms
    requireActivity().window.setSoftInputMode(...)          // ~1ms
}
```

**Alur:**
1. ✅ Find all views by ID
2. ✅ Disable soft keyboard untuk hardware scanner

#### **2.2.2 setupObservers()**

**File:** `BarcodeInputFragment.kt:217-302`

**Waktu:** ~5-10ms

```kotlin
private fun setupObservers() {
    // Observer untuk scan count
    viewModel.scanCount.observe(viewLifecycleOwner) { count ->
        updateExpeditionInfo()                              // Update UI
    }
    
    // Observer untuk selected expedisi
    viewModel.selectedExpedisiLive.observe(viewLifecycleOwner) { expedition ->
        if (expedition.isNotEmpty()) {
            updateExpeditionInfo()
        }
    }
    
    // Observer untuk scan result (CRITICAL)
    viewModel.scanResult.observe(viewLifecycleOwner) { result ->
        result?.let {
            // Skip old results (timestamp filter)
            val timeDiff = System.currentTimeMillis() - result.timestamp
            if (timeDiff > 30_000 || result.timestamp <= lastProcessedTimestamp) {
                return@let  // Skip
            }
            
            // Process result based on status
            when (it.status) {
                "success" -> {
                    audioManager.playSuccessBeep()          // ✅ Play sound
                    BarcodeToastManager.showSuccess(...)     // ✅ Show toast
                    etBarcodeInput.setText("")              // Clear input
                    updateTodayUnique()                     // Update metrics
                }
                "duplicate" -> {
                    audioManager.playDoubleBeep()
                    BarcodeToastManager.showError(...)
                    // ...
                }
                "error" -> {
                    audioManager.playFailureBeep()
                    BarcodeToastManager.showError(...)
                    // ...
                }
            }
        }
    }
    
    // Observer untuk scanning state
    viewModel.isScanning.observe(viewLifecycleOwner) { isProcessing ->
        progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
    }
}
```

**Alur:**
1. ✅ Setup observer untuk scanCount
2. ✅ Setup observer untuk selectedExpedisi
3. ✅ Setup observer untuk scanResult (CRITICAL - untuk audio & toast)
4. ✅ Setup observer untuk isScanning (loading indicator)

#### **2.2.3 setupSpinners()**

**File:** `BarcodeInputFragment.kt:111-215`

**Waktu:** ~50-200ms (background)

```kotlin
private fun setupSpinners() {
    // Load expedisi data (async)
    viewLifecycleOwner.lifecycleScope.launch {
        val expedisiList = viewModel.loadExpedisiData()     // ~20-100ms (database query)
        
        // Create adapter dengan hint
        val withHint = expedisiList + listOf("Pilih Expedisi")
        val expedisiAdapter = ArrayAdapter(...)
        
        spinnerExpedisi.adapter = expedisiAdapter           // ~1-5ms
        spinnerExpedisi.setSelection(expedisiAdapter.count)  // Set to hint
    }
    
    // Setup karung spinner (1-100)
    val karungList = (1..100).map { it.toString() }
    val karungAdapter = ArrayAdapter(...)
    spinnerKarung.adapter = karungAdapter                   // ~1-5ms
    
    // Setup listeners
    spinnerExpedisi.onItemSelectedListener = object : ... {
        override fun onItemSelected(...) {
            val selectedExpedisi = parent?.getItemAtPosition(position) as? String
            viewModel.setSelectedExpedisi(selectedExpedisi)  // Update ViewModel
            updateKarungOptionsForExpedition(selectedExpedisi)
        }
    }
    
    spinnerKarung.onItemSelectedListener = object : ... {
        override fun onItemSelected(...) {
            val selectedKarung = parent?.getItemAtPosition(position) as? String
            viewModel.setSelectedKarung(selectedKarung)
            updateExpeditionInfo()
        }
    }
}
```

**Alur:**
1. ✅ Load expedisi data dari database (async)
2. ✅ Create expedisi adapter dengan hint
3. ✅ Setup karung spinner (1-100)
4. ✅ Setup item selected listeners
5. ✅ Restore previous selections (jika ada)

---

## 🔄 **PHASE 3: ACTIVITY RESUMED (onResume)**

### **3.1 BarcodeScannerTabsActivity.onResume()**

**File:** `BarcodeScannerTabsActivity.kt:107-140`

**Waktu:** ~10-50ms (jika cache valid) atau ~200-1000ms (jika cache expired)

```kotlin
override fun onResume() {
    super.onResume()
    
    // Auto-refresh jika cache expired (debounce 30 detik)
    val now = System.currentTimeMillis()
    val cacheStillValid = BarcodeCacheManager.isResiCacheValid() && 
                         BarcodeCacheManager.isExpedisiCacheValid() &&
                         BarcodeCacheManager.getAllResiRecords().isNotEmpty() &&
                         BarcodeCacheManager.getAllExpedisiRecords().isNotEmpty()
    
    if (now - lastAutoRefreshMs > AUTO_REFRESH_INTERVAL_MS && !cacheStillValid) {
        lastAutoRefreshMs = now
        lifecycleScope.launch {
            viewModel.forceRefreshData()                    // Background refresh
        }
    } else if (cacheStillValid) {
        Log.d(TAG, "⚡ Cache still valid, skipping auto-refresh")
    }
}
```

**Alur:**
1. ✅ Check cache validity
2. ✅ Jika cache valid → skip refresh (INSTANT)
3. ✅ Jika cache expired → refresh di background (non-blocking)

---

## 🔄 **PHASE 4: SCAN FLOW (Trigger Pressed)**

### **4.1 onKeyDown() - Trigger Key Pressed**

**File:** `BarcodeScannerTabsActivity.kt:327-343`

**Waktu:** ~1-5ms

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    val isTrigger = keyCode == 293  // Trigger keycode
    
    if (!isTrigger) return super.onKeyDown(keyCode, event)
    
    if (event?.repeatCount == 0) {
        Log.d(TAG, "🔑 KeyDown trigger: $keyCode → startBarcodeScan / authorize broadcast")
        
        if (!useBroadcastInput) {
            if (!isScanning) startBarcodeScan()             // ⚠️ See 4.1.1
        } else {
            allowNextBroadcast = true                        // ✅ Authorize broadcast
        }
    }
    
    return true
}
```

**Alur:**
1. ✅ Detect trigger key (keycode 293)
2. ✅ Jika bukan broadcast mode → start scan
3. ✅ Jika broadcast mode → set `allowNextBroadcast = true`

#### **4.1.1 startBarcodeScan()**

**File:** `BarcodeScannerTabsActivity.kt:360-384`

**Waktu:** ~10-50ms

```kotlin
fun startBarcodeScan() {
    val success = builtInBarcodeManager.startScan()         // ~5-20ms
    if (success) {
        isScanning = true
        viewModel.setScanningState(true)
        audioManager.playStartBeep()                       // ✅ Play start sound
        Toast.makeText(this, "✅ Scanner aktif", Toast.LENGTH_LONG).show()
    }
}
```

**Alur:**
1. ✅ Start scanner via BuiltInBarcodeManager
2. ✅ Update scanning state
3. ✅ Play start beep sound
4. ✅ Show toast notification

---

### **4.2 Barcode Detected (Broadcast/Callback)**

**File:** `BarcodeScannerTabsActivity.kt:301-314`

**Waktu:** ~1-5ms (callback)

```kotlin
builtInBarcodeManager.setBarcodeCallback { barcode ->
    if (allowNextBroadcast) {
        allowNextBroadcast = false                          // Reset latch
        viewModel.processScannedBarcode(barcode)            // ⚠️ See Phase 5
    } else {
        Log.w(TAG, "⚠️ Broadcast ignored (latch not set)")
    }
}
```

**Alur:**
1. ✅ Barcode received from scanner
2. ✅ Check `allowNextBroadcast` flag
3. ✅ Jika authorized → process barcode
4. ✅ Reset latch (one-shot)

---

### **4.3 onKeyUp() - Trigger Key Released**

**File:** `BarcodeScannerTabsActivity.kt:345-358`

**Waktu:** ~1-5ms

```kotlin
override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    val isTrigger = keyCode == 293
    
    if (!isTrigger) return super.onKeyUp(keyCode, event)
    
    Log.d(TAG, "🔑 KeyUp trigger: $keyCode → authorize next broadcast")
    
    if (useBroadcastInput) {
        allowNextBroadcast = true                           // ✅ Authorize next scan
    } else {
        if (isScanning) stopBarcodeScan()                  // Stop scan
    }
    
    return true
}
```

**Alur:**
1. ✅ Detect trigger release
2. ✅ Jika broadcast mode → authorize next broadcast
3. ✅ Jika non-broadcast → stop scan

---

## 🔄 **PHASE 5: PROCESS SCANNED BARCODE**

### **5.1 ViewModel.processScannedBarcode()**

**File:** `BarcodeScannerViewModel.kt:377-542`

**Waktu:** ~50-500ms (tergantung validation)

```kotlin
fun processScannedBarcode(barcode: String, ...) {
    val trimmedBarcode = barcode.trim()
    val currentTime = System.currentTimeMillis()
    
    // **DEBOUNCE**: Tolak jika masih memproses
    if (isProcessing) {
        _scanResult.value = ScanResult(
            status = "error",
            message = "Tunggu scan sebelumnya selesai",
            Resi = trimmedBarcode,
            timestamp = currentTime
        )
        return
    }
    
    // **DEBOUNCE**: Cek minimum interval antar scan
    val timeSinceLastScan = currentTime - lastScanCompleteTime
    if (timeSinceLastScan < MIN_SCAN_INTERVAL_MS && lastScanCompleteTime > 0) {
        _scanResult.value = ScanResult(
            status = "error",
            message = "Scan terlalu cepat, tunggu sebentar",
            Resi = trimmedBarcode,
            timestamp = currentTime
        )
        return
    }
    
    isProcessing = true
    
    viewModelScope.launch {
        try {
            // **VALIDASI**: Cek expedisi & karung
            val expedisiError = if (selectedExpedisi.isNullOrEmpty()) "Mohon pilih Expedisi" else null
            val karungError = if (selectedKarung.isNullOrEmpty()) "Mohon pilih No Karung" else null
            
            if (expedisiError != null || karungError != null) {
                _scanResult.value = ScanResult(
                    status = "error",
                    message = expedisiError ?: karungError,
                    Resi = trimmedBarcode,
                    timestamp = currentTime
                )
                isProcessing = false
                return@launch
            }
            
            // **VALIDASI**: ExpedisiValidator
            val validationResult = expedisiValidator.validateBarcode(
                barcode = trimmedBarcode,
                selectedExpedisi = selectedExpedisi,
                selectedKarung = selectedKarung
            )
            
            // Process berdasarkan hasil validasi
            when (validationResult.status) {
                ExpedisiValidator.ValidationStatus.OK -> {
                    processSuccessfulScan(...)              // ⚠️ See 5.2
                }
                ExpedisiValidator.ValidationStatus.DUPLICATE_PROCESSED -> {
                    _scanResult.value = ScanResult(
                        status = "duplicate",
                        message = validationResult.message,
                        Resi = trimmedBarcode,
                        timestamp = currentTime
                    )
                }
                else -> {
                    _scanResult.value = ScanResult(
                        status = "error",
                        message = validationResult.message,
                        Resi = trimmedBarcode,
                        timestamp = currentTime
                    )
                }
            }
        } catch (e: Exception) {
            _scanResult.value = ScanResult(
                status = "error",
                message = "Terjadi kesalahan: ${e.message}",
                Resi = barcode,
                timestamp = System.currentTimeMillis()
            )
        } finally {
            isProcessing = false
            lastScanCompleteTime = System.currentTimeMillis()
        }
    }
}
```

**Alur:**
1. ✅ Trim barcode
2. ✅ **DEBOUNCE**: Check if still processing
3. ✅ **DEBOUNCE**: Check minimum interval
4. ✅ Set `isProcessing = true`
5. ✅ **VALIDASI**: Check expedisi & karung selected
6. ✅ **VALIDASI**: ExpedisiValidator.validateBarcode()
7. ✅ Process berdasarkan hasil validasi
8. ✅ Set `isProcessing = false`

---

### **5.2 processSuccessfulScan()**

**File:** `BarcodeScannerViewModel.kt:549-653`

**Waktu:** ~50-200ms (local-first, non-blocking)

```kotlin
private fun processSuccessfulScan(...) {
    viewModelScope.launch {
        // **LOCAL-FIRST STRATEGY**: Create record
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
        val cacheUpdated = enhancedRepository.saveBarcodeResiRecord(barcodeRecord)
        // ✅ Cache updated INSTANTLY
        
        if (!cacheUpdated) {
            _scanResult.value = ScanResult(
                status = "error",
                message = "Gagal menyimpan ke cache lokal",
                Resi = barcode,
                timestamp = System.currentTimeMillis()
            )
            return@launch
        }
        
        // **OPTIMISTIC UI UPDATE**: Update UI INSTANTLY
        val processedCount = BarcodeCacheManager.getProcessedResiSet().size
        _scanCount.value = processedCount                   // ✅ Update count
        _processedBarcodesFromDB.value = BarcodeCacheManager.getProcessedResiSet()
        
        // Update last scan time
        val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        _lastScanTime.value = timeFormat.format(Date())
        
        // **IMMEDIATE UI FEEDBACK**: Update scanResult
        val resultTimestamp = System.currentTimeMillis()
        _scanResult.value = ScanResult(
            status = "success",
            message = "Barcode $barcode berhasil di-scan dan disimpan",
            Resi = barcode,
            created = createdTime,
            Keterangan = validationResult.actualCourierName ?: scannerType,
            nokarung = selectedKarung,
            schedule = validationResult.actualSchedule ?: "ontime",
            timestamp = resultTimestamp                     // ✅ Explicit timestamp
        )
        
        Log.d(TAG, "⚡ [LOCAL-FIRST] Cache updated instantly, Supabase sync in background...")
        // Note: Network sync terjadi di background (non-blocking)
        
        // **NON-BLOCKING**: Update flag di background
        viewModelScope.launch(Dispatchers.IO) {
            updateExpedisiFlag(barcode)                     // Background
        }
    }
}
```

**Alur:**
1. ✅ Create barcode record
2. ✅ **LOCAL-FIRST**: Save to cache (INSTANT)
3. ✅ Update scan count (optimistic UI)
4. ✅ Update last scan time
5. ✅ Update scanResult LiveData (triggers observer)
6. ✅ Background sync to Supabase (non-blocking)
7. ✅ Background update expedisi flag

---

### **5.3 Observer Triggered - Audio & Toast**

**File:** `BarcodeInputFragment.kt:233-296`

**Waktu:** ~10-50ms

```kotlin
viewModel.scanResult.observe(viewLifecycleOwner) { result ->
    result?.let {
        // Skip old results
        val timeDiff = System.currentTimeMillis() - result.timestamp
        if (timeDiff > 30_000 || result.timestamp <= lastProcessedTimestamp) {
            return@let  // Skip
        }
        
        lastProcessedTimestamp = result.timestamp
        
        // Process berdasarkan status
        when (it.status) {
            "success" -> {
                audioManager.playSuccessBeep()              // ✅ Play sound
                BarcodeToastManager.showSuccess(requireContext(), "Scan berhasil: ${it.Resi}") // ✅ Show toast
                etBarcodeInput.setText("")                  // Clear input
                etBarcodeInput.requestFocus()               // Focus for next scan
                updateTodayUnique()                         // Update metrics
            }
            "duplicate" -> {
                audioManager.playDoubleBeep()
                BarcodeToastManager.showError(...)
                // ...
            }
            "error" -> {
                audioManager.playFailureBeep()
                BarcodeToastManager.showError(...)
                // ...
            }
        }
        
        updateExpeditionInfo()                              // Update UI metrics
    }
}
```

**Alur:**
1. ✅ Observer triggered by LiveData change
2. ✅ Filter old results (timestamp check)
3. ✅ Play appropriate sound (success/duplicate/error)
4. ✅ Show toast notification
5. ✅ Clear input field
6. ✅ Request focus for next scan
7. ✅ Update metrics (today unique, expedition info)

---

## 📊 **SUMMARY: ALUR LENGKAP**

### **1. Activity Dibuka**
```
onCreate()
├─ setupToolbar()
├─ setupViewModel()
│  └─ ViewModel.init{}
│     ├─ Initialize Audio Manager (app-wide)
│     ├─ Initialize Notification Manager
│     └─ Initialize Cache Async (background)
├─ setupAudioManager()
│  └─ BarcodeAudioManager.initAudio()
│     ├─ Create SoundPool
│     └─ Load 5 sound files (async)
├─ setupBuiltInBarcodeManager()
│  ├─ Create BuiltInBarcodeManager
│  ├─ Set barcode callback
│  └─ Set scan state callback
└─ setupNavbar()
   └─ switchToTab(0)
      └─ showFragment(BarcodeInputFragment())
```

### **2. Fragment Dibuat**
```
BarcodeInputFragment.onViewCreated()
├─ Get ViewModel from Activity
├─ Get Audio Manager from Activity (reuse)
├─ Initialize ExpedisiValidator
├─ initViews()
├─ setupObservers()
│  ├─ scanCount observer
│  ├─ selectedExpedisi observer
│  ├─ scanResult observer (CRITICAL)
│  └─ isScanning observer
├─ setupClickListeners()
└─ setupSpinners()
   ├─ Load expedisi data (async)
   └─ Setup karung spinner (1-100)
```

### **3. Activity Resumed**
```
onResume()
└─ Check cache validity
   ├─ If valid → skip refresh (INSTANT)
   └─ If expired → refresh in background
```

### **4. Scan Flow**
```
onKeyDown(293) → Trigger Pressed
├─ If broadcast mode → allowNextBroadcast = true
└─ If non-broadcast → startBarcodeScan()
   └─ Play start beep

Barcode Detected → Callback
└─ If allowNextBroadcast → processScannedBarcode()

onKeyUp(293) → Trigger Released
└─ If broadcast mode → allowNextBroadcast = true (next scan)
```

### **5. Process Barcode**
```
processScannedBarcode()
├─ DEBOUNCE checks
├─ VALIDASI: Expedisi & Karung selected
├─ VALIDASI: ExpedisiValidator.validateBarcode()
│  ├─ Check format
│  ├─ Check duplicate
│  ├─ Check NOT_FOUND (tbl_expedisi)
│  └─ Check mismatch expedisi
└─ If OK → processSuccessfulScan()
   ├─ LOCAL-FIRST: Save to cache (INSTANT)
   ├─ Update scan count (optimistic UI)
   ├─ Update scanResult LiveData
   └─ Background sync to Supabase
```

### **6. UI Feedback**
```
scanResult Observer Triggered
├─ Filter old results (timestamp)
├─ Play sound (success/duplicate/error)
├─ Show toast notification
├─ Clear input field
├─ Request focus
└─ Update metrics
```

---

## ✅ **CHECKLIST: READY STATE**

Activity siap untuk scan jika:
- ✅ ViewModel initialized
- ✅ Audio Manager initialized (SoundPool ready)
- ✅ BuiltInBarcodeManager initialized
- ✅ Fragment created & observers setup
- ✅ Cache loaded (background)
- ✅ Expedisi data loaded (background)

**Total waktu hingga ready:** ~300-1000ms (tergantung device)

---

## 🎯 **KEY POINTS**

1. **Audio Manager** diinisialisasi di Activity (bukan Fragment)
2. **Cache** di-load di background (non-blocking)
3. **Local-First Strategy** - cache updated instantly, Supabase sync di background
4. **Observer Pattern** - UI update via LiveData observers
5. **Debounce** - mencegah scan terlalu cepat
6. **One-shot Latch** - broadcast mode hanya terima satu scan per trigger

---

## 📝 **CATATAN**

- Semua operasi network dilakukan di background (non-blocking)
- UI update menggunakan optimistic updates (cache first)
- Audio ready sebelum scan pertama (init di Activity)
- Fragment reuse audio manager dari Activity (single instance)

