package com.savoj.volumpdf.core.pdf.model

/** Що дозволено робити з документом за його правами (CONTRACTS v0.4, §1.5.1). */
data class DocumentEditability(val isEncrypted: Boolean, val canMarkup: Boolean, val canFillForms: Boolean)

/** Права документа забороняють зміну. [deniedAction]: "MODIFY" | "FILL_FORM". */
class PdfPermissionException(val deniedAction: String) : Exception(
    when (deniedAction) {
        "FILL_FORM" -> "Права документа забороняють заповнення форм"
        else -> "Права документа забороняють зміну"
    }
)
