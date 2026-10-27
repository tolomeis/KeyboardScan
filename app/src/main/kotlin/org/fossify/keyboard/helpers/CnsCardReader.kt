package org.fossify.keyboard.helpers

import android.nfc.tech.IsoDep

/**
 * Reads the personal data file (EF_DatiPersonali, 3F00/1100/1102) of an Italian CNS card (TS-CNS, old CIE)
 * over NFC, logging every APDU so that failures can be diagnosed on real cards.
 *
 * File layout (see OpenSC pkcs15-itacns.c): ASCII, 6 hex chars with the total length, then fields each
 * prefixed by 2 hex chars with their length.
 */
class CnsCardReader(private val isoDep: IsoDep, private val log: (String) -> Unit) {
    companion object {
        val FIELD_NAMES = listOf(
            "emettitore", "data_rilascio", "data_scadenza", "cognome", "nome", "data_nascita", "sesso", "statura",
            "codice_fiscale", "cittadinanza", "comune_nascita", "stato_nascita", "atto_nascita", "comune_residenza",
            "indirizzo_residenza", "annotazioni_espatrio"
        )

        private const val MAX_FILE_SIZE = 1024
        private const val CHUNK_SIZE = 0xE0

        fun parsePersonalData(data: ByteArray): Map<String, String>? {
            val text = String(data, Charsets.ISO_8859_1)
            if (text.length < 6) return null
            val fileSize = text.substring(0, 6).toIntOrNull(16) ?: return null
            val end = minOf(text.length, 6 + fileSize)
            val fields = LinkedHashMap<String, String>()
            var pos = 6
            var index = 0
            while (pos + 2 <= end) {
                val len = text.substring(pos, pos + 2).toIntOrNull(16) ?: break
                pos += 2
                if (pos + len > end) break
                val name = FIELD_NAMES.getOrElse(index) { "campo_$index" }
                fields[name] = text.substring(pos, pos + len).trim()
                pos += len
                index++
            }
            return fields.takeIf { it.isNotEmpty() }
        }
    }

    private class Response(val data: ByteArray, val sw: Int)

    /** Selects 3F00/1100/1102 trying several SELECT variants, then reads the whole file. */
    fun readPersonalData(): ByteArray? {
        // baseline: EF_IDCarta (card serial) should be readable on any CNS
        log("--- EF_IDCarta 3F00/1000/1003 ---")
        if (selectStepByStep(listOf("3F00", "1000", "1003"), p2 = 0x00) || selectStepByStep(listOf("3F00", "1000", "1003"), p2 = 0x0C)) {
            readBinary()?.let { log("ID carta: ${it.toPrintable()}") }
        }

        log("--- EF_DatiPersonali 3F00/1100/1102 ---")
        val selected = selectStepByStep(listOf("3F00", "1100", "1102"), p2 = 0x00) ||
            selectStepByStep(listOf("3F00", "1100", "1102"), p2 = 0x0C) ||
            selectByPath("11001102", p2 = 0x00) ||
            selectByPath("11001102", p2 = 0x0C)

        if (!selected) {
            log("Impossibile selezionare EF_DatiPersonali")
            return null
        }

        return readBinary()
    }

    private fun selectStepByStep(fids: List<String>, p2: Int): Boolean {
        log("SELECT passo-passo P2=%02X".format(p2))
        return fids.all { fid ->
            val resp = transmit(byteArrayOf(0x00, 0xA4.toByte(), 0x00, p2.toByte(), 0x02) + fid.hexToBytes())
            resp != null && resp.isOk()
        }
    }

    private fun selectByPath(path: String, p2: Int): Boolean {
        log("SELECT per path P1=08 P2=%02X".format(p2))
        if (transmit(byteArrayOf(0x00, 0xA4.toByte(), 0x00, p2.toByte(), 0x02) + "3F00".hexToBytes())?.isOk() != true) {
            return false
        }
        val bytes = path.hexToBytes()
        val resp = transmit(byteArrayOf(0x00, 0xA4.toByte(), 0x08, p2.toByte(), bytes.size.toByte()) + bytes)
        return resp != null && resp.isOk()
    }

    private fun readBinary(): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var offset = 0
        var le = CHUNK_SIZE
        while (offset < MAX_FILE_SIZE) {
            val resp = transmit(
                byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), (offset and 0xFF).toByte(), le.toByte())
            ) ?: break

            when {
                resp.sw and 0xFF00 == 0x6C00 -> {
                    // wrong length, the card tells us the right one
                    le = resp.sw and 0xFF
                    if (le == 0) break
                    continue
                }

                resp.sw == 0x9000 || resp.sw == 0x6282 -> {
                    out.write(resp.data)
                    offset += resp.data.size
                    if (resp.sw == 0x6282 || resp.data.size < le) break
                }

                else -> break
            }
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun transmit(apdu: ByteArray): Response? {
        return try {
            var raw = isoDep.transceive(apdu)
            var resp = raw.toResponse()
            // 61xx: more data available, fetch it with GET RESPONSE
            if (resp.sw and 0xFF00 == 0x6100) {
                raw = isoDep.transceive(byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, (resp.sw and 0xFF).toByte()))
                resp = Response(resp.data + raw.toResponse().data, raw.toResponse().sw)
            }
            log("> ${apdu.toHex()}\n< ${resp.data.size}B SW=%04X %s".format(resp.sw, describeSw(resp.sw)))
            resp
        } catch (e: Exception) {
            log("> ${apdu.toHex()}\n< ERRORE ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun Response.isOk() = sw == 0x9000 || sw and 0xFF00 == 0x6100

    private fun ByteArray.toResponse(): Response {
        if (size < 2) return Response(ByteArray(0), 0)
        val sw = ((this[size - 2].toInt() and 0xFF) shl 8) or (this[size - 1].toInt() and 0xFF)
        return Response(copyOfRange(0, size - 2), sw)
    }

    private fun describeSw(sw: Int) = when {
        sw == 0x9000 -> "OK"
        sw == 0x6282 -> "fine file"
        sw == 0x6982 -> "SERVE AUTENTICAZIONE (PIN)"
        sw == 0x6988 || sw == 0x6987 -> "SERVE SECURE MESSAGING"
        sw == 0x6985 -> "condizioni d'uso non soddisfatte"
        sw == 0x6A82 -> "file non trovato"
        sw == 0x6A86 -> "P1/P2 errati"
        sw == 0x6D00 -> "istruzione non supportata"
        sw and 0xFF00 == 0x6C00 -> "Le errato"
        else -> ""
    }
}

fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }

fun ByteArray.toPrintable() = String(this, Charsets.ISO_8859_1).map { if (it.code in 0x20..0x7E) it else '.' }.joinToString("")

private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
