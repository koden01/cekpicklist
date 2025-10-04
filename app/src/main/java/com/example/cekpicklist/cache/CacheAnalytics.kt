package com.example.cekpicklist.cache

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Cache Analytics untuk monitoring dan optimasi performa
 * - Track cache hit/miss rates
 * - Monitor response times
 * - Analyze cache usage patterns
 * - Provide insights untuk optimasi
 */
class CacheAnalytics {
    
    companion object {
        private const val TAG = "CacheAnalytics"
    }
    
    // Metrics tracking
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val totalRequests = AtomicLong(0)
    
    // Response time tracking
    private val responseTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val averageResponseTimes = ConcurrentHashMap<String, AtomicLong>()
    
    // Cache usage patterns
    private val accessPatterns = ConcurrentHashMap<String, MutableList<Long>>()
    private val popularKeys = ConcurrentHashMap<String, AtomicLong>()
    
    // Performance metrics
    private val startTime = System.currentTimeMillis()
    
    /**
     * Record cache hit
     */
    fun recordCacheHit(key: String, responseTime: Long = 0) {
        cacheHits.incrementAndGet()
        totalRequests.incrementAndGet()
        
        if (responseTime > 0) {
            recordResponseTime(key, responseTime)
        }
        
        recordAccessPattern(key)
        popularKeys.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
        
        Log.d(TAG, "✅ Cache HIT: $key (${responseTime}ms)")
    }
    
    /**
     * Record cache miss
     */
    fun recordCacheMiss(key: String, responseTime: Long = 0) {
        cacheMisses.incrementAndGet()
        totalRequests.incrementAndGet()
        
        if (responseTime > 0) {
            recordResponseTime(key, responseTime)
        }
        
        recordAccessPattern(key)
        
        Log.d(TAG, "❌ Cache MISS: $key (${responseTime}ms)")
    }
    
    /**
     * Record response time
     */
    private fun recordResponseTime(key: String, responseTime: Long) {
        responseTimes.computeIfAbsent(key) { mutableListOf() }.add(responseTime)
        
        // Update average response time
        val times = responseTimes[key] ?: return
        val average = times.sum() / times.size
        averageResponseTimes.computeIfAbsent(key) { AtomicLong(0) }.set(average)
    }
    
    /**
     * Record access pattern
     */
    private fun recordAccessPattern(key: String) {
        val currentTime = System.currentTimeMillis()
        accessPatterns.computeIfAbsent(key) { mutableListOf() }.add(currentTime)
        
        // Keep only last 100 access times untuk memory efficiency
        val pattern = accessPatterns[key] ?: return
        if (pattern.size > 100) {
            pattern.removeAt(0)
        }
    }
    
    /**
     * Get cache hit rate
     */
    fun getCacheHitRate(): Double {
        val total = totalRequests.get()
        return if (total > 0) {
            (cacheHits.get().toDouble() / total.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * Get cache miss rate
     */
    fun getCacheMissRate(): Double {
        val total = totalRequests.get()
        return if (total > 0) {
            (cacheMisses.get().toDouble() / total.toDouble()) * 100.0
        } else {
            0.0
        }
    }
    
    /**
     * Get average response time untuk key tertentu
     */
    fun getAverageResponseTime(key: String): Long {
        return averageResponseTimes[key]?.get() ?: 0L
    }
    
    /**
     * Get most popular cache keys
     */
    fun getMostPopularKeys(limit: Int = 10): List<Pair<String, Long>> {
        return popularKeys.entries
            .sortedByDescending { it.value.get() }
            .take(limit)
            .map { it.key to it.value.get() }
    }
    
    /**
     * Get access frequency untuk key tertentu
     */
    fun getAccessFrequency(key: String, timeWindowMs: Long = 3600000L): Int { // 1 hour default
        val currentTime = System.currentTimeMillis()
        val pattern = accessPatterns[key] ?: return 0
        
        return pattern.count { currentTime - it <= timeWindowMs }
    }
    
    /**
     * Get comprehensive analytics report
     */
    suspend fun getAnalyticsReport(): CacheAnalyticsReport = withContext(Dispatchers.IO) {
        val uptime = System.currentTimeMillis() - startTime
        val hitRate = getCacheHitRate()
        val missRate = getCacheMissRate()
        val totalRequests = this@CacheAnalytics.totalRequests.get()
        val totalHits = cacheHits.get()
        val totalMisses = cacheMisses.get()
        
        val popularKeys = getMostPopularKeys(5)
        val averageResponseTimes = averageResponseTimes.entries
            .sortedByDescending { it.value.get() }
            .take(5)
            .map { it.key to it.value.get() }
        
        CacheAnalyticsReport(
            uptime = uptime,
            totalRequests = totalRequests,
            cacheHits = totalHits,
            cacheMisses = totalMisses,
            hitRate = hitRate,
            missRate = missRate,
            popularKeys = popularKeys,
            slowestKeys = averageResponseTimes,
            recommendations = generateRecommendations(hitRate, missRate, popularKeys)
        )
    }
    
    /**
     * Generate recommendations berdasarkan analytics
     */
    private fun generateRecommendations(
        hitRate: Double,
        missRate: Double,
        popularKeys: List<Pair<String, Long>>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (hitRate < 70.0) {
            recommendations.add("Cache hit rate rendah (${String.format("%.1f", hitRate)}%) - pertimbangkan untuk meningkatkan TTL atau cache warming")
        }
        
        if (missRate > 30.0) {
            recommendations.add("Cache miss rate tinggi (${String.format("%.1f", missRate)}%) - pertimbangkan untuk pre-load data yang sering digunakan")
        }
        
        if (popularKeys.isNotEmpty()) {
            val topKey = popularKeys.first()
            if (topKey.second > 100) {
                recommendations.add("Key '${topKey.first}' diakses sangat sering (${topKey.second} kali) - pertimbangkan untuk cache warming")
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Cache performance baik - tidak ada rekomendasi khusus")
        }
        
        return recommendations
    }
    
    /**
     * Reset analytics data
     */
    fun reset() {
        cacheHits.set(0)
        cacheMisses.set(0)
        totalRequests.set(0)
        responseTimes.clear()
        averageResponseTimes.clear()
        accessPatterns.clear()
        popularKeys.clear()
        
        Log.d(TAG, "🔄 Analytics data reset")
    }
    
    /**
     * Data class untuk analytics report
     */
    data class CacheAnalyticsReport(
        val uptime: Long,
        val totalRequests: Long,
        val cacheHits: Long,
        val cacheMisses: Long,
        val hitRate: Double,
        val missRate: Double,
        val popularKeys: List<Pair<String, Long>>,
        val slowestKeys: List<Pair<String, Long>>,
        val recommendations: List<String>
    )
}
