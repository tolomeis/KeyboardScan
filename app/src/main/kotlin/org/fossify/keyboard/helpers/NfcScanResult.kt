package org.fossify.keyboard.helpers

import android.os.SystemClock

/** Hands the fields read by NfcReadActivity over to the keyboard, which types them once the original field is focused again. */
object NfcScanResult {
    private const val MAX_AGE_MS = 30_000L

    data class Pending(val fields: Map<String, String>, val mode: NfcOutputTemplate.Mode)

    private var pending: Pending? = null
    private var timestamp = 0L

    @Synchronized
    fun set(fields: Map<String, String>, mode: NfcOutputTemplate.Mode) {
        pending = Pending(fields, mode)
        timestamp = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun consume(): Pending? {
        val result = pending?.takeIf { SystemClock.elapsedRealtime() - timestamp < MAX_AGE_MS }
        pending = null
        return result
    }
}
