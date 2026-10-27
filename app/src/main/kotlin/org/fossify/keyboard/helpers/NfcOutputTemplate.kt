package org.fossify.keyboard.helpers

import android.view.KeyEvent

/**
 * Turns a template like "{nome}{TAB}{cognome}" into the text and keys the keyboard types after an NFC read.
 * Placeholders map to the fields parsed by CnsCardReader, {TAB} and {ENTER} are sent as real key presses.
 */
object NfcOutputTemplate {
    const val DEFAULT = "{nome}{TAB}{cognome}{TAB}{cf}{TAB}{comune}"

    val PLACEHOLDERS = linkedMapOf(
        "nome" to "nome",
        "cognome" to "cognome",
        "cf" to "codice_fiscale",
        "comune" to "comune_residenza",
        "indirizzo" to "indirizzo_residenza",
        "sesso" to "sesso",
        "data_nascita" to "data_nascita",
        "comune_nascita" to "comune_nascita"
    )

    private val KEYS = mapOf("TAB" to KeyEvent.KEYCODE_TAB, "ENTER" to KeyEvent.KEYCODE_ENTER)

    sealed class Token {
        data class Text(val value: String) : Token()
        data class Key(val keyCode: Int) : Token()
    }

    fun helpText() = "Segnaposto disponibili:\n" +
        PLACEHOLDERS.keys.joinToString(" ") { "{$it}" } +
        "\n\nTasti: {TAB} passa al campo successivo, {ENTER} invia.\nPredefinito: $DEFAULT"

    fun build(template: String, fields: Map<String, String>): List<Token> {
        val tokens = ArrayList<Token>()
        val text = StringBuilder()
        var pos = 0
        while (pos < template.length) {
            val close = if (template[pos] == '{') template.indexOf('}', pos) else -1
            val name = if (close > pos) template.substring(pos + 1, close) else null
            when {
                name != null && KEYS.containsKey(name.uppercase()) -> {
                    if (text.isNotEmpty()) tokens.add(Token.Text(text.toString()))
                    text.clear()
                    tokens.add(Token.Key(KEYS.getValue(name.uppercase())))
                    pos = close + 1
                }

                name != null && PLACEHOLDERS.containsKey(name.lowercase()) -> {
                    text.append(fields[PLACEHOLDERS.getValue(name.lowercase())].orEmpty())
                    pos = close + 1
                }

                else -> {
                    text.append(template[pos])
                    pos++
                }
            }
        }
        if (text.isNotEmpty()) tokens.add(Token.Text(text.toString()))
        return tokens
    }
}
