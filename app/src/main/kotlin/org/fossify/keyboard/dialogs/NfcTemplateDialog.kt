package org.fossify.keyboard.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.DialogNfcTemplateBinding
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.helpers.NfcOutputTemplate

class NfcTemplateDialog(val activity: BaseSimpleActivity, val callback: () -> Unit) {
    init {
        val binding = DialogNfcTemplateBinding.inflate(activity.layoutInflater).apply {
            nfcTemplateValue.setText(activity.config.nfcOutputTemplate)
            nfcTemplateHelp.text = NfcOutputTemplate.helpText()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton("Ripristina", null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    alertDialog.showKeyboard(binding.nfcTemplateValue)
                    alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        binding.nfcTemplateValue.setText(NfcOutputTemplate.DEFAULT)
                    }
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val template = binding.nfcTemplateValue.text.toString().trim()
                        activity.config.nfcOutputTemplate = template.ifEmpty { NfcOutputTemplate.DEFAULT }
                        callback()
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
