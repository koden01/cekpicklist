package com.example.cekpicklist.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Sound Settings Manager
 * Mengelola pengaturan sound untuk barcode scanner (hanya untuk mengganti sound)
 */
class SoundSettingsManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SoundSettingsManager"
        private const val PREFS_NAME = "barcode_sound_settings"
        
        // Sound settings keys
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_SUCCESS_SOUND = "success_sound"
        private const val KEY_FAILURE_SOUND = "failure_sound"
        private const val KEY_DOUBLE_SOUND = "double_sound"
        private const val KEY_START_SOUND = "start_sound"
        
        // Default values (same as web app)
        private const val DEFAULT_SOUND_ENABLED = true
        private const val DEFAULT_SUCCESS_SOUND = "beep_success"
        private const val DEFAULT_FAILURE_SOUND = "beep_failure"
        private const val DEFAULT_DOUBLE_SOUND = "beep_double"
        private const val DEFAULT_START_SOUND = "beep_start"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if sound is enabled
     */
    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
    }
    
    /**
     * Set sound enabled/disabled
     */
    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        Log.d(TAG, "🔊 Sound enabled: $enabled")
    }
    
    /**
     * Get success sound file name
     */
    fun getSuccessSound(): String {
        return prefs.getString(KEY_SUCCESS_SOUND, DEFAULT_SUCCESS_SOUND) ?: DEFAULT_SUCCESS_SOUND
    }
    
    /**
     * Set success sound file name
     */
    fun setSuccessSound(soundName: String) {
        prefs.edit().putString(KEY_SUCCESS_SOUND, soundName).apply()
        Log.d(TAG, "🔊 Success sound set to: $soundName")
    }
    
    /**
     * Get failure sound file name
     */
    fun getFailureSound(): String {
        return prefs.getString(KEY_FAILURE_SOUND, DEFAULT_FAILURE_SOUND) ?: DEFAULT_FAILURE_SOUND
    }
    
    /**
     * Set failure sound file name
     */
    fun setFailureSound(soundName: String) {
        prefs.edit().putString(KEY_FAILURE_SOUND, soundName).apply()
        Log.d(TAG, "🔊 Failure sound set to: $soundName")
    }
    
    /**
     * Get double/duplicate sound file name
     */
    fun getDoubleSound(): String {
        return prefs.getString(KEY_DOUBLE_SOUND, DEFAULT_DOUBLE_SOUND) ?: DEFAULT_DOUBLE_SOUND
    }
    
    /**
     * Set double/duplicate sound file name
     */
    fun setDoubleSound(soundName: String) {
        prefs.edit().putString(KEY_DOUBLE_SOUND, soundName).apply()
        Log.d(TAG, "🔊 Double sound set to: $soundName")
    }
    
    /**
     * Get start sound file name
     */
    fun getStartSound(): String {
        return prefs.getString(KEY_START_SOUND, DEFAULT_START_SOUND) ?: DEFAULT_START_SOUND
    }
    
    /**
     * Set start sound file name
     */
    fun setStartSound(soundName: String) {
        prefs.edit().putString(KEY_START_SOUND, soundName).apply()
        Log.d(TAG, "🔊 Start sound set to: $soundName")
    }
    
    /**
     * Reset all settings to default
     */
    fun resetToDefaults() {
        prefs.edit().apply {
            putBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
            putString(KEY_SUCCESS_SOUND, DEFAULT_SUCCESS_SOUND)
            putString(KEY_FAILURE_SOUND, DEFAULT_FAILURE_SOUND)
            putString(KEY_DOUBLE_SOUND, DEFAULT_DOUBLE_SOUND)
            putString(KEY_START_SOUND, DEFAULT_START_SOUND)
            apply()
        }
        Log.d(TAG, "🔊 Sound settings reset to defaults")
    }
    
    /**
     * Get all sound settings as a map
     */
    fun getAllSettings(): Map<String, Any> {
        return mapOf(
            "soundEnabled" to isSoundEnabled(),
            "successSound" to getSuccessSound(),
            "failureSound" to getFailureSound(),
            "doubleSound" to getDoubleSound(),
            "startSound" to getStartSound()
        )
    }
    
    /**
     * Get available sound files (same as web app)
     */
    fun getAvailableSounds(): List<String> {
        return listOf(
            "beep_success",
            "beep_failure", 
            "beep_double",
            "beep_start",
            "failure"
        )
    }
}
