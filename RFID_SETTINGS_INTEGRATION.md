# 🔧 **INTEGRASI RFID SETTINGS DENGAN RFIDSCANMANAGER**

## 🎯 **OVERVIEW**

SettingsActivity sekarang terintegrasi penuh dengan `RfidScanManager` untuk mengatur power level dan RSSI threshold RFID scanning.

## 🔄 **ALUR INTEGRASI:**

### **1. SettingsActivity → SharedPreferences**
```kotlin
// SettingsActivity menyimpan settings ke SharedPreferences
private fun savePowerLevel(powerLevel: Int) {
    sharedPreferences.edit()
        .putInt(KEY_POWER_LEVEL, powerLevel)
        .apply()
    notifySettingsChanged()  // Notify RfidScanManager
}

private fun saveRssiThreshold(rssiThreshold: Int) {
    sharedPreferences.edit()
        .putInt(KEY_RSSI_THRESHOLD, rssiThreshold)
        .apply()
    notifySettingsChanged()  // Notify RfidScanManager
}
```

### **2. RfidScanManager ← SharedPreferences**
```kotlin
// RfidScanManager membaca settings dari SharedPreferences
fun refreshSettingsFromSharedPreferences() {
    val sharedPreferences = activity.getSharedPreferences("RFIDSettings", MODE_PRIVATE)
    val powerLevel = sharedPreferences.getInt("power_level", 25)
    val rssiThreshold = sharedPreferences.getInt("rssi_threshold", -55)
    
    val newSettings = RfidScanSettings(
        powerLevel = powerLevel,
        rssiThreshold = rssiThreshold
    )
    updateSettings(newSettings)
}
```

## 📊 **SHAREDPREFERENCES CONFIGURATION:**

### **File Name:**
```kotlin
private const val PREFS_NAME = "RFIDSettings"
```

### **Keys:**
```kotlin
private const val KEY_POWER_LEVEL = "power_level"
private const val KEY_RSSI_THRESHOLD = "rssi_threshold"
```

### **Default Values:**
```kotlin
// Power Level: 1-30 (default: 25)
val powerLevel = sharedPreferences.getInt("power_level", 25)

// RSSI Threshold: -120 to 0 dBm (default: -55)
val rssiThreshold = sharedPreferences.getInt("rssi_threshold", -55)
```

## 🎛️ **SETTINGS UI:**

### **RFID Power Level:**
- **Range:** 1-30
- **Default:** 25
- **Description:** "Adjust RFID transmission power (1-30)"
- **Display:** "Current: 25"

### **RSSI Threshold:**
- **Range:** -120 to 0 dBm
- **Default:** -55 dBm
- **Description:** "Filter weak RFID signals (-120 to 0 dBm)"
- **Display:** "Current: -55 dBm"

## 🔄 **REAL-TIME UPDATES:**

### **1. SettingsActivity Changes:**
```kotlin
// User menggeser seekbar
seekBarPowerLevel.setOnSeekBarChangeListener { seekBar, progress, fromUser ->
    if (fromUser) {
        updatePowerLevelDisplay(progress)
        savePowerLevel(progress)  // Save to SharedPreferences
    }
}
```

### **2. RfidScanManager Updates:**
```kotlin
// RfidScanManager membaca perubahan settings
fun refreshSettingsFromSharedPreferences() {
    // Read from SharedPreferences
    // Update internal settings
    // Apply to RFID reader
}
```

## 🎯 **PENGGUNAAN SETTINGS:**

### **Power Level (1-30):**
- **1:** Power minimum (jarak dekat)
- **25:** Power default (jarak sedang)
- **30:** Power maksimum (jarak jauh)

### **RSSI Threshold (-120 to 0 dBm):**
- **-120:** Terima semua sinyal (termasuk yang lemah)
- **-55:** Default (filter sinyal lemah)
- **0:** Hanya terima sinyal kuat

## 🔧 **IMPLEMENTASI:**

### **1. SettingsActivity.kt:**
```kotlin
class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "RFIDSettings"  // Sama dengan RfidScanManager
        private const val KEY_POWER_LEVEL = "power_level"  // Sama dengan RfidScanManager
        private const val KEY_RSSI_THRESHOLD = "rssi_threshold"  // Sama dengan RfidScanManager
    }
    
    private fun savePowerLevel(powerLevel: Int) {
        sharedPreferences.edit().putInt(KEY_POWER_LEVEL, powerLevel).apply()
        notifySettingsChanged()  // Notify RfidScanManager
    }
}
```

### **2. RfidScanManager.kt:**
```kotlin
class RfidScanManager {
    fun refreshSettingsFromSharedPreferences() {
        val sharedPreferences = activity.getSharedPreferences("RFIDSettings", MODE_PRIVATE)
        val powerLevel = sharedPreferences.getInt("power_level", 25)
        val rssiThreshold = sharedPreferences.getInt("rssi_threshold", -55)
        
        val newSettings = RfidScanSettings(
            powerLevel = powerLevel,
            rssiThreshold = rssiThreshold
        )
        updateSettings(newSettings)
    }
}
```

## 🎉 **HASIL INTEGRASI:**

### **✅ FITUR YANG BERFUNGSI:**
- ✅ **Power Level Control** → Mempengaruhi kekuatan transmisi RFID
- ✅ **RSSI Threshold Control** → Mempengaruhi filtering sinyal lemah
- ✅ **Real-time Updates** → Perubahan langsung diterapkan
- ✅ **Persistent Storage** → Settings tersimpan di SharedPreferences
- ✅ **Default Values** → Fallback ke nilai default jika belum diset

### **📱 CARA PENGGUNAAN:**
1. **Buka Settings** → Klik Settings icon (⚙️)
2. **Atur Power Level** → Geser seekbar (1-30)
3. **Atur RSSI Threshold** → Geser seekbar (-120 to 0)
4. **Settings Otomatis Tersimpan** → Langsung diterapkan ke RFID scanning

### **🔍 DEBUGGING:**
```kotlin
// Log dari SettingsActivity
Log.d(TAG, "Power level saved: $powerLevel")
Log.d(TAG, "RSSI threshold saved: $rssiThreshold")

// Log dari RfidScanManager
Log.d(tag, "🔥 Settings refreshed from SharedPreferences: $newSettings")
Log.d(tag, "🔥 Power Level: ${settings.powerLevel}")
Log.d(tag, "🔥 RSSI Threshold: ${settings.rssiThreshold}")
```

**Sekarang SettingsActivity benar-benar terintegrasi dengan RfidScanManager dan perubahan settings langsung mempengaruhi RFID scanning!** 🚀
