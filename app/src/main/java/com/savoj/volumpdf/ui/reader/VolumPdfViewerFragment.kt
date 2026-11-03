package com.savoj.volumpdf.ui.reader

import androidx.pdf.PdfDocument
import androidx.pdf.viewer.fragment.PdfViewerFragment

class VolumPdfViewerFragment : PdfViewerFragment() {

    var onDocumentLoadedListener: ((pageCount: Int) -> Unit)? = null
    var onDocumentErrorListener: ((cause: Throwable) -> Unit)? = null

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        onDocumentLoadedListener?.invoke(document.pageCount)
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        onDocumentErrorListener?.invoke(error)
    }
}
