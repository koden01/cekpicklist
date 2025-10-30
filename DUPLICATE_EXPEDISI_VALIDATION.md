# 🔍 Duplicate & Expedisi Validation Alignment

## 📋 Overview

Penanganan duplikat dan validasi expedisi di aplikasi Android sekarang **sudah sama** dengan aplikasi web `azure-chinchilla-swoop` yang sudah berjalan baik.

## 🔄 Validation Flow Comparison

### Web App (azure-chinchilla-swoop)
```typescript
// 1. Check if already processed (in allFlagYesExpedisiResiNumbers)
if (allFlagYesExpedisiResiNumbers?.has(normalizedCurrentResi)) {
    validationStatus = 'DUPLICATE_PROCESSED';
    // Get details from tbl_resi
    const { data: resiDetails } = await supabase
        .from("tbl_resi")
        .select("created, Keterangan, nokarung, schedule")
        .eq("Resi", currentResi)
        .maybeSingle();
    
    if (resiDetails.schedule === "batal") {
        validationMessage = `BATAL! Resi ini sudah dibatalkan pada ${processedDate}.`;
    } else {
        validationMessage = `DOUBLE! Resi ini ${keterangan} sudah diproses pada ${processedDate} di karung ${nokarung}.`;
    }
}

// 2. Check in tbl_expedisi (allFlagNoExpedisiData)
else if (allFlagNoExpedisiData?.has(normalizedCurrentResi)) {
    expedisiRecordFromCache = allFlagNoExpedisiData.get(normalizedCurrentResi);
    wasFlagNo = true;
    actualCourierNameForResiTable = normalizeExpeditionName(expedisiRecordFromCache.couriername);
    
    // Determine schedule based on created date
    if (isSameDay(expedisiCreatedDate, today)) {
        actualScheduleForResiTable = "ontime";
    } else {
        actualScheduleForResiTable = "late";
    }
}

// 3. Check expedition mismatch
const isDirectMatch = (normalizedSelectedExpedition === normalizedExpedisiCourierName);
if (!isDirectMatch) {
    validationStatus = 'MISMATCH_EXPEDISI';
    validationMessage = `Resi ini terdaftar untuk ekspedisi ${expedisiRecordFromCache.couriername}, bukan ${expedition}.`;
}
```

### Android App (cekpicklist)
```kotlin
// 1. Check if already processed (in tbl_resi)
val resiDetails = checkResiInDatabase(trimmedBarcode)
if (resiDetails != null) {
    val message = if (resiDetails.schedule == "batal") {
        "BATAL! Resi ini sudah dibatalkan pada $processedDate."
    } else {
        "DOUBLE! Resi ini $keterangan sudah diproses pada $processedDate di karung $nokarung."
    }
    
    return ValidationResult(
        status = ValidationStatus.DUPLICATE_PROCESSED,
        message = message,
        resiDetails = resiDetails
    )
}

// 2. Check in tbl_expedisi
val expedisiRecord = checkExpedisiInDatabase(trimmedBarcode)
if (expedisiRecord == null) {
    return ValidationResult(
        status = ValidationStatus.NOT_FOUND_IN_EXPEDISI,
        message = "Data resi $trimmedBarcode tidak ditemukan di database."
    )
}

// 3. Check expedition mismatch
val normalizedExpedisiCourierName = normalizeExpeditionName(expedisiRecord.couriername)
val normalizedSelectedExpedition = normalizeExpeditionName(selectedExpedisi)

if (normalizedExpedisiCourierName != normalizedSelectedExpedition) {
    return ValidationResult(
        status = ValidationStatus.MISMATCH_EXPEDISI,
        message = "Resi ini terdaftar untuk ekspedisi ${expedisiRecord.couriername}, bukan $selectedExpedisi.",
        expedisiRecord = expedisiRecord
    )
}

// 4. Determine schedule based on created date
val actualSchedule = determineSchedule(expedisiRecord.created)
```

## ✅ Validation Status Mapping

| Web App Status | Android App Status | Description |
|---|---|---|
| `'OK'` | `ValidationStatus.OK` | Validasi berhasil |
| `'DUPLICATE_PROCESSED'` | `ValidationStatus.DUPLICATE_PROCESSED` | Resi sudah diproses |
| `'MISMATCH_EXPEDISI'` | `ValidationStatus.MISMATCH_EXPEDISI` | Expedisi tidak sesuai |
| `'NOT_FOUND_IN_EXPEDISI'` | `ValidationStatus.NOT_FOUND_IN_EXPEDISI` | Resi tidak ditemukan |
| N/A | `ValidationStatus.INVALID_FORMAT` | Format barcode tidak valid |
| N/A | `ValidationStatus.EMPTY_INPUT` | Input kosong |

## 🔍 Key Validation Features

### ✅ **1. Duplicate Detection**
- **Web**: Check `allFlagYesExpedisiResiNumbers` Set
- **Android**: Check `tbl_resi` database
- **Benefit**: Mencegah scan ganda dengan detail informasi

### ✅ **2. Expedisi Validation**
- **Web**: Check `allFlagNoExpedisiData` Map
- **Android**: Check `tbl_expedisi` database
- **Benefit**: Memastikan resi sesuai dengan expedisi yang dipilih

### ✅ **3. Schedule Determination**
- **Web**: `isSameDay(expedisiCreatedDate, today) ? "ontime" : "late"`
- **Android**: `determineSchedule(expedisiRecord.created)`
- **Benefit**: Otomatis menentukan status ontime/late

### ✅ **4. Status Batal Handling**
- **Web**: Check `resiDetails.schedule === "batal"`
- **Android**: Check `resiDetails.schedule == "batal"`
- **Benefit**: Mencegah scan resi yang sudah dibatalkan

### ✅ **5. Expedition Name Normalization**
- **Web**: `normalizeExpeditionName(expedisiRecord.couriername)`
- **Android**: `normalizeExpeditionName(expedisiRecord.couriername)`
- **Benefit**: Konsistensi nama expedisi (uppercase, trim)

## 📊 Validation Messages

### **Duplicate Messages**
```kotlin
// Web App
"DOUBLE! Resi ini ${keterangan} sudah diproses pada ${processedDate} di karung ${nokarung}."

// Android App
"DOUBLE! Resi ini $keterangan sudah diproses pada $processedDate di karung $nokarung."
```

### **Batal Messages**
```kotlin
// Web App
"BATAL! Resi ini sudah dibatalkan pada ${processedDate}."

// Android App
"BATAL! Resi ini sudah dibatalkan pada $processedDate."
```

### **Mismatch Messages**
```kotlin
// Web App
"Resi ini terdaftar untuk ekspedisi ${expedisiRecordFromCache.couriername}, bukan ${expedition}."

// Android App
"Resi ini terdaftar untuk ekspedisi ${expedisiRecord.couriername}, bukan $selectedExpedisi."
```

### **Not Found Messages**
```kotlin
// Web App
"Data resi ${currentResi} tidak ditemukan di database."

// Android App
"Data resi $trimmedBarcode tidak ditemukan di database."
```

## 🔧 Implementation Details

### **ExpedisiValidator Class**
```kotlin
class ExpedisiValidator(private val supabaseService: SupabaseService) {
    
    suspend fun validateBarcode(
        barcode: String,
        selectedExpedisi: String? = null,
        selectedKarung: String? = null
    ): ValidationResult {
        // 1. Empty input check
        // 2. Expedition/karung selection check
        // 3. Format validation
        // 4. Duplicate check (tbl_resi)
        // 5. Expedisi check (tbl_expedisi)
        // 6. Expedition mismatch check
        // 7. Schedule determination
    }
}
```

### **BarcodeScannerViewModel Integration**
```kotlin
// Use ExpedisiValidator untuk validasi yang sama dengan web app
if (::expedisiValidator.isInitialized) {
    val validationResult = expedisiValidator.validateBarcode(
        barcode = trimmedBarcode,
        selectedExpedisi = selectedExpedisi,
        selectedKarung = selectedKarung
    )
    
    when (validationResult.status) {
        ValidationStatus.DUPLICATE_PROCESSED -> {
            // Handle duplicate
        }
        ValidationStatus.MISMATCH_EXPEDISI -> {
            // Handle mismatch
        }
        ValidationStatus.NOT_FOUND_IN_EXPEDISI -> {
            // Handle not found
        }
        ValidationStatus.OK -> {
            // Process successful scan
        }
    }
}
```

## 🎯 Benefits dari Alignment

### **1. Consistent Behavior**
- Validasi yang sama antara web dan mobile
- User experience yang konsisten
- Reduced confusion dan errors

### **2. Data Integrity**
- Mencegah scan ganda
- Memastikan expedisi yang benar
- Validasi format yang robust

### **3. Business Logic Compliance**
- Mengikuti aturan bisnis yang sama
- Schedule determination yang akurat
- Status handling yang konsisten

### **4. Error Handling**
- Pesan error yang informatif
- Status code yang jelas
- Debugging yang mudah

## 🚀 Performance Considerations

### **1. Database Queries**
- Optimized queries untuk duplicate check
- Caching untuk expedisi data
- Efficient validation flow

### **2. User Experience**
- Fast validation response
- Clear error messages
- Immediate feedback

### **3. Offline Support**
- Local validation fallback
- Pending operations untuk sync
- Graceful degradation

## 📈 Testing Scenarios

### **1. Duplicate Scenarios**
- ✅ Scan resi yang sudah diproses
- ✅ Scan resi yang sudah dibatalkan
- ✅ Scan resi dengan status berbeda

### **2. Expedisi Scenarios**
- ✅ Scan resi dengan expedisi yang benar
- ✅ Scan resi dengan expedisi yang salah
- ✅ Scan resi yang tidak ada di database

### **3. Schedule Scenarios**
- ✅ Scan resi hari ini (ontime)
- ✅ Scan resi kemarin (late)
- ✅ Scan resi dengan tanggal berbeda

---

**✅ Penanganan duplikat dan validasi expedisi sekarang sudah sama dengan aplikasi web yang sudah berjalan baik!**

