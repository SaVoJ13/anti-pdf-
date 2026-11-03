package com.savoj.volumpdf.core.pdf.model

sealed interface PdfLoadResult {
    data class Success(val metadata: PdfDocumentMetadata) : PdfLoadResult
    data class PasswordRequired(val isInvalidAttempt: Boolean) : PdfLoadResult
    data class Error(val cause: Throwable, val userMessage: String) : PdfLoadResult
}
