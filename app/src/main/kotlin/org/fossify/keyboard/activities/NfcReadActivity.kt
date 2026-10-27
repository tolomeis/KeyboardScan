package org.fossify.keyboard.activities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.fossify.keyboard.helpers.CnsCardReader
import org.fossify.keyboard.helpers.NfcScanResult
import org.fossify.keyboard.helpers.toHex

/**
 * NFC reader mode needs a foreground activity, so the keyboard launches this small dialog-like activity.
 * It reads the personal data of a TS-CNS card, logs every APDU for diagnosis and hands
 * the parsed fields over to the keyboard through NfcScanResult.
 */
class NfcReadActivity : Activity(), NfcAdapter.ReaderCallback {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        buildLayout()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        when {
            nfcAdapter == null -> statusView.text = "NFC non disponibile su questo dispositivo"
            !nfcAdapter!!.isEnabled -> statusView.text = "NFC disattivato: attivalo nelle impostazioni"
            else -> statusView.text = "Appoggia la tessera sul retro del telefono…"
        }
    }

    override fun onResume() {
        super.onResume()
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000) }
        nfcAdapter?.enableReaderMode(this, this, flags, extras)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        runOnUiThread {
            statusView.text = "Lettura in corso, non muovere la tessera…"
            logView.text = ""
        }

        log("UID: ${tag.id.toHex()}")
        log("Tecnologie: ${tag.techList.joinToString { it.substringAfterLast('.') }}")

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            log("La tessera non supporta ISO-DEP (ISO 14443-4)")
            setStatus("Tessera non supportata")
            return
        }

        var fields: Map<String, String>? = null
        try {
            isoDep.connect()
            isoDep.timeout = 5000
            log("Historical bytes: ${isoDep.historicalBytes?.toHex()}")
            log("HiLayerResponse: ${isoDep.hiLayerResponse?.toHex()}")
            log("Max transceive: ${isoDep.maxTransceiveLength}, extended APDU: ${isoDep.isExtendedLengthApduSupported}")

            val data = CnsCardReader(isoDep, ::log).readPersonalData()
            if (data != null) {
                log("--- Dati grezzi (${data.size}B) ---\n${String(data, Charsets.ISO_8859_1)}")
                fields = CnsCardReader.parsePersonalData(data)
                fields?.forEach { (key, value) -> log("$key = $value") }
            }
        } catch (e: Exception) {
            log("ERRORE ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try {
                isoDep.close()
            } catch (_: Exception) {
            }
        }

        if (fields == null || listOf("nome", "cognome", "codice_fiscale").any { fields[it].isNullOrEmpty() }) {
            setStatus("Lettura non riuscita: usa \"Copia log\" e mandamelo")
            return
        }

        NfcScanResult.set(fields)
        setStatus("Letto: ${fields["nome"]} ${fields["cognome"]}. Ritorno al campo…")
        runOnUiThread { statusView.postDelayed({ finish() }, 1500) }
    }

    private fun log(line: String) {
        runOnUiThread {
            logView.append(line + "\n")
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusView.text = text }
    }

    private fun buildLayout() {
        val padding = (16 * resources.displayMetrics.density).toInt()
        statusView = TextView(this).apply {
            textSize = 18f
            setPadding(0, 0, 0, padding)
        }

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
        }

        scrollView = ScrollView(this).apply {
            addView(logView)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val copyButton = Button(this).apply {
            text = "Copia log"
            setOnClickListener {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("NFC log", logView.text))
                Toast.makeText(this@NfcReadActivity, "Log copiato", Toast.LENGTH_SHORT).show()
            }
        }

        val closeButton = Button(this).apply {
            text = "Chiudi"
            setOnClickListener { finish() }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(copyButton)
            addView(closeButton)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(statusView)
            addView(scrollView)
            addView(buttons)
        }

        val height = (resources.displayMetrics.heightPixels * 0.6).toInt()
        setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
    }
}
