package com.savoj.volumpdf.ui.reader

import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment

@OptIn(ExperimentalPdfApi::class)
class VolumPdfViewerFragment : PdfViewerFragment() {

    var onDocumentLoadedListener: ((pageCount: Int) -> Unit)? = null
    var onDocumentErrorListener: ((cause: Throwable) -> Unit)? = null
    private var attachedPdfView: PdfView? = null

    override fun onPdfViewCreated(pdfView: PdfView) {
        super.onPdfViewCreated(pdfView)
        attachedPdfView = pdfView
    }

    val currentPageIndex: Int
        get() = attachedPdfView?.firstVisiblePage ?: 0

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        onDocumentLoadedListener?.invoke(document.pageCount)
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        onDocumentErrorListener?.invoke(error)
    }
}
