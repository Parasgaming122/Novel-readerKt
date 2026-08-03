package com.example

import android.content.Context
import kotlinx.coroutines.delay

object NetworkErrorHandler {
    
    suspend fun <T> executeWithRetry(
        context: Context,
        maxRetries: Int = 3,
        backoffMs: Long = 1000L,
        block: suspend () -> T
    ): Result<T> {
        try {
            return Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            var lastException = e
            for (attempt in 1..maxRetries) {
                try {
                    com.example.WtrLogManager.log(context, "Retry attempt $attempt/$maxRetries after ${e.message}")
                    delay(backoffMs * attempt)
                    return Result.success(block())
                } catch (retryException: kotlinx.coroutines.CancellationException) {
                    throw retryException
                } catch (retryException: Exception) {
                    lastException = retryException
                }
            }
            return Result.failure(lastException)
        }
    }
}
