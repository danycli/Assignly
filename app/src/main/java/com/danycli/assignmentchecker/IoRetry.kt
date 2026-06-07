package com.danycli.assignmentchecker

import kotlinx.coroutines.delay
import java.io.IOException

suspend fun <T> retryIo(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 500,
    block: suspend () -> T
): T {
    var delayMs = initialDelayMs
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (e: IOException) {
            if (attempt >= maxAttempts) throw e
            delay(delayMs)
            delayMs *= 2
            attempt++
        }
    }
}
