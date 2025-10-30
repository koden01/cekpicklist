package com.example.cekpicklist.utils

import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
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
    
    init {
        initSound()
    }
    
    private fun initSound() {
        try {
            soundPool = SoundPool(10, AudioManager.STREAM_MUSIC, 5)
            
            // Load sound files (jika ada)
            // soundMap[1] = soundPool?.load(context, R.raw.barcodebeep, 1) ?: 0
            // soundMap[2] = soundPool?.load(context, R.raw.serror, 1) ?: 0
            
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            volumeRatio = 0.5f
            
            Log.d(TAG, "✅ Sound system initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing sound: ${e.message}")
        }
    }
    
    override fun run() {
        while (isRunning) {
            try {
                val soundId = soundQueue.poll()
                if (soundId != null && soundId > 0) {
                    playSound(soundId)
                }
                sleep(50) // Check every 50ms
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
     */
    fun playSoundDelayed(value: Int) {
        try {
            // Map location value to sound
            val soundId = when {
                value >= 80 -> 1 // High value = success sound
                value >= 50 -> 1 // Medium value = success sound  
                else -> 2 // Low value = error sound
            }
            
            soundQueue.offer(soundId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error queuing sound: ${e.message}")
        }
    }
    
    /**
     * Play sound langsung
     */
    fun play(soundId: Int = 1) {
        soundQueue.offer(soundId)
    }
    
    /**
     * Clear sound queue
     */
    fun cleanData() {
        soundQueue.clear()
    }
    
    private fun playSound(soundId: Int) {
        try {
            val actualSoundId = soundMap[soundId] ?: return
            
            soundPool?.play(
                actualSoundId,
                volumeRatio,
                volumeRatio,
                1,
                0,
                1f
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error playing sound: ${e.message}")
        }
    }
    
    /**
     * Release sound resources
     */
    fun release() {
        isRunning = false
        interrupt()
        
        try {
            soundPool?.release()
            soundPool = null
            Log.d(TAG, "🔇 Sound resources released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing sound: ${e.message}")
        }
    }
}
