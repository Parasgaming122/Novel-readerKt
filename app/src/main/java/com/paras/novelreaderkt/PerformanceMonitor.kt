package com.paras.novelreaderkt

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

object PerformanceMonitor {
    
    data class MemoryStats(
        val nativeHeap: Long,
        val javaHeap: Long,
        val maxJavaHeap: Long,
        val totalRss: Long
    )
    
    fun getMemoryStats(context: Context): MemoryStats {
        val runtime = Runtime.getRuntime()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        return MemoryStats(
            nativeHeap = Debug.getNativeHeapSize(),
            javaHeap = runtime.totalMemory() - runtime.freeMemory(),
            maxJavaHeap = runtime.maxMemory(),
            totalRss = memInfo.totalMem
        )
    }
    
    suspend fun monitorPerformance(
        context: Context,
        intervalMs: Long = 30000L // Every 30 seconds
    ) {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                delay(intervalMs)
                val stats = getMemoryStats(context)
                val maxHeapLimit = if (stats.maxJavaHeap > 0L) stats.maxJavaHeap else 1L
                val heapUsagePercent = (stats.javaHeap * 100) / maxHeapLimit
                
                if (heapUsagePercent > 80) {
                    WtrLogManager.log(
                        context,
                        "⚠️ High memory usage: ${heapUsagePercent}% (${stats.javaHeap / 1024 / 1024}MB)"
                    )
                }
                
                if (heapUsagePercent > 95) {
                    System.gc()
                    WtrLogManager.log(context, "🗑️ Triggered garbage collection due to high memory")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
