package com.pdfreader.core.pdf.model

import com.savoj.volumpdf.core.pdf.model.FormField as ActualFormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate as ActualFormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.FreeTextData as ActualFreeTextData
import com.savoj.volumpdf.core.pdf.model.InkStroke as ActualInkStroke
import com.savoj.volumpdf.core.pdf.model.PdfDocumentMetadata as ActualPdfDocumentMetadata
import com.savoj.volumpdf.core.pdf.model.PdfLoadResult as ActualPdfLoadResult
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult as ActualPdfOperationResult

typealias PdfDocumentMetadata = ActualPdfDocumentMetadata
typealias PdfLoadResult = ActualPdfLoadResult
typealias PdfOperationResult = ActualPdfOperationResult
typealias InkStroke = ActualInkStroke
typealias FreeTextData = ActualFreeTextData
typealias FormField = ActualFormField
typealias FormFieldUpdate = ActualFormFieldUpdate
