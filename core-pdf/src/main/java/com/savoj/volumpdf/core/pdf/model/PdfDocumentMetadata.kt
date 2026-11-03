package com.savoj.volumpdf.core.pdf.model

import android.net.Uri

data class PdfDocumentMetadata(
    val uri: Uri,
    val title: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val isEncrypted: Boolean
)
