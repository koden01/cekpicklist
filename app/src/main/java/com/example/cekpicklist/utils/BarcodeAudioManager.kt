package com.example.cekpicklist.utils

import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import com.example.cekpicklist.R

/**
 * Audio Manager untuk Barcode Scanner
 * Mengelola suara beep untuk berbagai skenario scan
 */
class BarcodeAudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BarcodeAudioManager"
        
        // Sound IDs
        private const val SOUND_SUCCESS = 1
        private const val SOUND_FAILURE = 2
        private const val SOUND_DOUBLE = 3
        private const val SOUND_START = 4
        private const val SOUND_SABAR = 5
        private const val SOUND_BATAL = 6
        private const val SOUND_SALAH = 7
    }
    
    private var soundPool: SoundPool? = null
    private val soundMap = HashMap<Int, Int>()
    private var audioManager: AudioManager? = null
    private lateinit var soundSettingsManager: SoundSettingsManager
    private var isSoundPoolReady = false
    private val pendingBeeps = ArrayDeque<BarcodeBeepType>()
    private var soundsToLoad = 7 // Updated: +beep_batal +beep_salah
    private var soundsLoaded = 0
    
    /**
     * Initialize audio system
     */
    fun initAudio() {
        try {
            Log.d(TAG, "🔊 Initializing barcode audio system...")
            
            // Initialize sound settings manager
            soundSettingsManager = SoundSettingsManager(context)
            
            if (soundPool != null && isSoundPoolReady) {
                Log.d(TAG, "🔊 SoundPool already initialized and ready")
                return
            }

            // Create SoundPool
            soundPool = SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            
            // Set load complete listener
            soundsLoaded = 0
            soundPool?.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) {
                    soundsLoaded += 1
                    if (soundsLoaded >= soundsToLoad) {
                        isSoundPoolReady = true
                        Log.d(TAG, "✅ SoundPool ready - all sounds loaded ($soundsLoaded/$soundsToLoad)")
                        // Drain pending beeps
                        while (pendingBeeps.isNotEmpty()) {
                            playBeep(pendingBeeps.removeFirst())
                        }
                    }
                } else {
                    Log.e(TAG, "❌ SoundPool load failed with status: $status")
                }
            }
            
            // Load sound files (same as web app)
            soundMap[SOUND_SUCCESS] = soundPool?.load(context, R.raw.beep_success, 1) ?: 0
            soundMap[SOUND_FAILURE] = soundPool?.load(context, R.raw.beep_failure, 1) ?: 0
            soundMap[SOUND_DOUBLE] = soundPool?.load(context, R.raw.beep_double, 1) ?: 0
            soundMap[SOUND_START] = soundPool?.load(context, R.raw.beep_start, 1) ?: 0
            soundMap[SOUND_SABAR] = soundPool?.load(context, R.raw.failure, 1) ?: 0 // Using failure.mp3 for sabar
            soundMap[SOUND_BATAL] = soundPool?.load(context, R.raw.beep_batal, 1) ?: 0 // NEW: beep for cancelled resi
            soundMap[SOUND_SALAH] = soundPool?.load(context, R.raw.beep_salah, 1) ?: 0 // NEW: beep for mismatch expedisi
            
            // Get AudioManager
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            Log.d(TAG, "✅ Barcode audio system initialized successfully with web app sounds")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing barcode audio: ${e.message}", e)
        }
    }
    
    /**
     * Play beep sound
     * @param soundType Type of sound to play
     */
    fun playBeep(soundType: BarcodeBeepType) {
        try {
            // Check if sound is enabled globally
            if (!soundSettingsManager.isSoundEnabled()) {
                Log.d(TAG, "🔇 Sound disabled globally, skipping ${soundType.name} beep")
                return
            }
            
            // Check if SoundPool is ready
            if (!isSoundPoolReady) {
                Log.w(TAG, "⚠️ SoundPool not ready yet, queueing ${soundType.name} beep")
                if (pendingBeeps.size < 5) pendingBeeps.addLast(soundType)
                return
            }
            
            if (soundPool != null) {
                // Use preloaded sound IDs (SoundPool requires soundId, not raw resourceId)
                val mapKey = when (soundType) {
                    BarcodeBeepType.SUCCESS -> SOUND_SUCCESS
                    BarcodeBeepType.FAILURE -> SOUND_FAILURE
                    BarcodeBeepType.DOUBLE -> SOUND_DOUBLE
                    BarcodeBeepType.START -> SOUND_START
                    BarcodeBeepType.SABAR -> SOUND_SABAR
                    BarcodeBeepType.BATAL -> SOUND_BATAL
                    BarcodeBeepType.SALAH -> SOUND_SALAH
                }
                val soundId = soundMap[mapKey] ?: 0
                if (soundId != 0) {
                    val volume = audioManager?.let { am ->
                        val currentVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                        if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0.5f
                    } ?: 0.5f
                    soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
                    Log.d(TAG, "🔊 Playing ${soundType.name} beep (soundId=$soundId)")
                } else {
                    Log.w(TAG, "⚠️ SoundId not found for ${soundType.name}")
                }
            } else {
                Log.w(TAG, "⚠️ Sound pool not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error playing ${soundType.name} beep: ${e.message}", e)
        }
    }
    
    /**
     * Play success beep
     */
    fun playSuccessBeep() {
        playBeep(BarcodeBeepType.SUCCESS)
    }
    
    /**
     * Play failure beep
     */
    fun playFailureBeep() {
        playBeep(BarcodeBeepType.FAILURE)
    }
    
    /**
     * Play double/duplicate beep
     */
    fun playDoubleBeep() {
        playBeep(BarcodeBeepType.DOUBLE)
    }
    
    /**
     * Play start beep
     */
    fun playStartBeep() {
        playBeep(BarcodeBeepType.START)
    }
    
    /**
     * Play sabar beep
     */
    fun playSabarBeep() {
        playBeep(BarcodeBeepType.SABAR)
    }
    
    /**
     * Play batal beep (untuk resi yang sudah dibatalkan)
     */
    fun playBatalBeep() {
        playBeep(BarcodeBeepType.BATAL)
    }

    /**
     * Play mismatch/salah beep (untuk ekspedisi mismatch)
     */
    fun playMismatchBeep() {
        playBeep(BarcodeBeepType.SALAH)
    }
    
    /**
     * Get resource ID for sound file name
     */
    private fun getResourceIdForSound(soundFileName: String): Int {
        return when (soundFileName) {
            "barcodebeep" -> R.raw.barcodebeep
            "beep_success" -> R.raw.beep_success
            "beep_failure" -> R.raw.beep_failure
            "beep_double" -> R.raw.beep_double
            "beep_start" -> R.raw.beep_start
            "serror" -> R.raw.serror
            "celebration" -> R.raw.celebration
            "applause" -> R.raw.applause
            "failure" -> R.raw.failure
            else -> {
                Log.w(TAG, "⚠️ Unknown sound file: $soundFileName, using default")
                R.raw.barcodebeep
            }
        }
    }
    
    /**
     * Release audio resources
     */
    fun release() {
        try {
            isSoundPoolReady = false
            soundPool?.release()
            soundPool = null
            soundMap.clear()
            Log.d(TAG, "🔊 Barcode audio resources released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing barcode audio: ${e.message}", e)
        }
    }
}

/**
 * Enum untuk jenis beep sound
 */
enum class BarcodeBeepType(val soundId: Int) {
    SUCCESS(1),
    FAILURE(2),
    DOUBLE(3),
    START(4),
    SABAR(5),
    BATAL(6), // NEW: Sound untuk resi yang sudah dibatalkan
    SALAH(7) // NEW: Sound untuk mismatch expedisi
}
