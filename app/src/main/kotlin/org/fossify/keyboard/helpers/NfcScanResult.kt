package org.fossify.keyboard.helpers

import android.os.SystemClock

/** Hands the fields read by NfcReadActivity over to the keyboard, which types them once the original field is focused again. */
object NfcScanResult {
    private const val MAX_AGE_MS = 30_000L

    private var fields: Map<String, String>? = null
    private var timestamp = 0L

    @Synchronized
    fun set(values: Map<String, String>) {
        fields = values
        timestamp = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun consume(): Map<String, String>? {
        val result = fields?.takeIf { SystemClock.elapsedRealtime() - timestamp < MAX_AGE_MS }
        fields = null
        return result
    }
}
