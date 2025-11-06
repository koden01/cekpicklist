package com.example.cekpicklist.utils

import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.example.cekpicklist.R
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * PlaySoundThread untuk sound feedback saat location valid
 * Sama seperti implementasi di demo UHF asli
 */
class PlaySoundThread(private val context: Context) : Thread() {
    
    companion object {
        private const val TAG = "PlaySoundThread"
    }
    
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<Int, Int>()
    private var volumeRatio: Float = 0f
    private var audioManager: AudioManager? = null
    private val soundQueue = ConcurrentLinkedQueue<Int>()
    private var isRunning = true
    
    // Untuk delayed sound (location feedback)
    private val objectLock = Object()
    private var interval = 500
    private var lastPlayTime = 0L
    
    init {
        initSound()
    }
    
    private fun initSound() {
        try {
            soundPool = SoundPool(10, AudioManager.STREAM_MUSIC, 5)
            
            // Load sound files (sama seperti demo UHF)
            soundMap[1] = soundPool?.load(context, R.raw.barcodebeep, 1) ?: 0
            soundMap[2] = soundPool?.load(context, R.raw.serror, 1) ?: 0
            
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Calculate volume ratio based on current volume
            val audioMaxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
            val audioCurrentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 1
            volumeRatio = if (audioMaxVolume > 0) {
                audioCurrentVolume.toFloat() / audioMaxVolume.toFloat()
            } else {
                0.5f
            }
            
            Log.d(TAG, "✅ Sound system initialized: barcodebeep=${soundMap[1]}, serror=${soundMap[2]}, volumeRatio=$volumeRatio")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing sound: ${e.message}", e)
        }
    }
    
    override fun run() {
        while (isRunning) {
            try {
                // Check untuk immediate sound dari queue
                val soundId = soundQueue.poll()
                if (soundId != null && soundId > 0) {
                    playSoundDirect(soundId)
                }
                
                // Handle delayed sound untuk location feedback (sama seperti demo UHF)
                var start = 0L
                synchronized(objectLock) {
                    while (isRunning) {
                        if (start == 0L) {
                            start = SystemClock.elapsedRealtime()
                        } else {
                            if (SystemClock.elapsedRealtime() - start >= interval) {
                                break
                            } else {
                                sleep(1)
                            }
                        }
                    }
                }
                
                // Play sound jika belum 500ms dari lastPlayTime
                if (SystemClock.elapsedRealtime() - lastPlayTime < 500) {
                    playSoundDirect(1) // Play barcodebeep sound
                }
                
            } catch (e: InterruptedException) {
                Log.d(TAG, "Sound thread interrupted")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in sound thread: ${e.message}")
            }
        }
    }
    
    /**
     * Play sound berdasarkan value (untuk location feedback)
     * Sama seperti implementasi di demo UHF: playSoundDelayed dengan interval dinamis
     */
    fun playSoundDelayed(value: Int) {
        try {
            // Implementasi sama seperti demo UHF
            // speed/value 1-100
            // 100-1 -> interval = 3
            // 99-10 -> interval = 100 - speed
            // 98-20 -> interval = (100 - speed) * 2
            // 97-30 -> interval = (100 - speed) * 3
            
            synchronized(objectLock) {
                val t = when {
                    value > 85 -> 3
                    value > 66 -> 100 - value
                    value > 33 -> (100 - value) * 2
                    else -> (100 - value) * 3
                }
                
                interval = t
                lastPlayTime = SystemClock.elapsedRealtime()
                
                Log.d(TAG, "📊 Location value=$value, interval=$interval")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error queuing delayed sound: ${e.message}")
        }
    }
    
    /**
     * Play sound langsung
     */
    fun play(soundId: Int = 1) {
        soundQueue.offer(soundId)
    }
    
    /**
     * Clear sound queue dan reset delayed sound
     */
    fun cleanData() {
        soundQueue.clear()
        synchronized(objectLock) {
            lastPlayTime = 0L
            interval = 500
        }
    }
    
    /**
     * Stop delayed sound playback
     */
    fun stopPlay() {
        synchronized(objectLock) {
            lastPlayTime = 0L
            interval = 500
            objectLock.notifyAll()
        }
    }
    
    /**
     * Play sound langsung menggunakan SoundPool
     */
    private fun playSoundDirect(soundId: Int) {
        try {
            val actualSoundId = soundMap[soundId] ?: 0
            if (actualSoundId == 0) {
                Log.w(TAG, "⚠️ Sound ID $soundId not loaded")
                return
            }
            
            soundPool?.play(
                actualSoundId,
                volumeRatio, // Left channel volume
                volumeRatio, // Right channel volume
                1, // Priority (0 = lowest)
                0, // Loop (0 = no loop, -1 = loop forever)
                1f // Rate (0.5-2.0, 1.0 = normal speed)
            )
            Log.d(TAG, "🔊 Playing sound ID=$soundId (actualSoundId=$actualSoundId)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error playing sound: ${e.message}", e)
        }
    }
    
    /**
     * Release sound resources
     */
    fun release() {
        isRunning = false
        interrupt()
        
        synchronized(objectLock) {
            objectLock.notifyAll()
        }
        
        try {
            soundPool?.release()
            soundPool = null
            soundMap.clear()
            Log.d(TAG, "🔇 Sound resources released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing sound: ${e.message}")
        }
    }
}
