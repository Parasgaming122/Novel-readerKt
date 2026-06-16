package com.paras.novelreaderkt

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object WtrLogManager {
    private var loggingEnabled = true
    private val _logs = mutableStateListOf<String>()
    val logs: List<String> get() = _logs

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private val loggerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val dateFormat = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
    }

    // Debounce persistence — coalesce rapid log writes into one disk write
    private var persistJob: Job? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            val sharedPrefs = context.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
            loggingEnabled = sharedPrefs.getBoolean("enable_logs", true)
            val savedLogs = sharedPrefs.getString("saved_logs_serialized", "") ?: ""

            mainHandler.post {
                _logs.clear()
                if (savedLogs.isNotEmpty()) {
                    savedLogs.split("||LC||").forEach {
                        if (it.isNotEmpty()) _logs.add(it)
                    }
                }
            }
        }
    }

    fun setLoggingEnabled(context: Context, enabled: Boolean) {
        synchronized(lock) {
            loggingEnabled = enabled
            val sharedPrefs = context.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("enable_logs", enabled).apply()

            mainHandler.post {
                if (!enabled) {
                    _logs.clear()
                    val sp = context.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
                    sp.edit().putString("saved_logs_serialized", "").apply()
                }
            }
        }
    }

    fun isLoggingEnabled(): Boolean = synchronized(lock) { loggingEnabled }

    fun log(context: Context?, msg: String) {
        synchronized(lock) {
            if (!loggingEnabled) return
            val formatter = dateFormat.get()
            val timestamp = if (formatter != null) formatter.format(java.util.Date()) else ""
            val formatted = "[$timestamp] $msg"

            mainHandler.post {
                _logs.add(0, formatted)
                if (_logs.size > 100) {
                    _logs.removeAt(_logs.size - 1)
                }
            }
        }

        // Debounced persistence — waits 2s after last log before writing to disk
        // Use applicationContext to avoid holding Activity references in the coroutine
        context?.let { ctx ->
            val appCtx = ctx.applicationContext
            synchronized(lock) {
                persistJob?.cancel()
                persistJob = loggerScope.launch {
                    delay(2000L)
                    val logsCopy = synchronized(_logs) { _logs.toList() }
                    if (logsCopy.isNotEmpty()) {
                        val serialized = logsCopy.joinToString("||LC||")
                        appCtx.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
                            .edit().putString("saved_logs_serialized", serialized).apply()
                    }
                }
            }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            mainHandler.post {
                _logs.clear()
                val sharedPrefs = context.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("saved_logs_serialized", "").apply()
            }
        }
    }
}