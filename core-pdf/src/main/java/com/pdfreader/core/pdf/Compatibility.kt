package com.pdfreader.core.pdf

import com.savoj.volumpdf.core.pdf.AnnotationEngine as ActualAnnotationEngine
import com.savoj.volumpdf.core.pdf.FormEngine as ActualFormEngine
import com.savoj.volumpdf.core.pdf.PageOperationsEngine as ActualPageOperationsEngine
import com.savoj.volumpdf.core.pdf.PdfBoxAnnotationEngine as ActualPdfBoxAnnotationEngine
import com.savoj.volumpdf.core.pdf.PdfBoxFormEngine as ActualPdfBoxFormEngine
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine as ActualPdfBoxPageOperationsEngine

typealias PageOperationsEngine = ActualPageOperationsEngine
typealias PdfBoxPageOperationsEngine = ActualPdfBoxPageOperationsEngine
typealias AnnotationEngine = ActualAnnotationEngine
typealias PdfBoxAnnotationEngine = ActualPdfBoxAnnotationEngine
typealias FormEngine = ActualFormEngine
typealias PdfBoxFormEngine = ActualPdfBoxFormEngine
