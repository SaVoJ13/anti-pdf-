package com.savoj.volumpdf.core.pdf.model

import android.net.Uri

sealed interface PdfOperationResult {
    data class Success(val outputUri: Uri, val pagesCount: Int) : PdfOperationResult
    data class Progress(val currentStep: Int, val totalSteps: Int) : PdfOperationResult
    data class Error(val cause: Throwable, val userMessage: String) : PdfOperationResult
}
