package com.savoj.volumpdf.ui.reader

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.savoj.volumpdf.R
import com.savoj.volumpdf.core.pdf.FormEngine
import com.savoj.volumpdf.core.pdf.PdfBoxFormEngine
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.databinding.DialogFillFormBottomSheetBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FillFormBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogFillFormBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val formEngine: FormEngine = PdfBoxFormEngine()
    private val adapter = FormFieldsAdapter()

    var formFields: List<FormField> = emptyList()
    var onFormSavedListener: ((Uri) -> Unit)? = null

    private var documentUri: Uri? = null
    private var restoredValues: Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriString = arguments?.getString(ARG_URI)
        if (uriString != null) {
            documentUri = Uri.parse(uriString)
        }

        if (savedInstanceState != null) {
            val bundle = savedInstanceState.getBundle(KEY_SAVED_VALUES)
            if (bundle != null) {
                val map = mutableMapOf<String, Any>()
                for (key in bundle.keySet()) {
                    val strVal = bundle.getString(key)
                    if (strVal != null) {
                        map[key] = strVal
                    } else if (bundle.containsKey(key)) {
                        map[key] = bundle.getBoolean(key)
                    }
                }
                restoredValues = map
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFillFormBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerFormFields.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFormFields.adapter = adapter

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        binding.btnCancelForm.setOnClickListener {
            dismiss()
        }

        binding.btnSaveForm.setOnClickListener {
            saveFormFields()
        }

        if (formFields.isNotEmpty()) {
            displayFields(formFields)
        } else {
            loadFieldsFromDocument()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                val displayMetrics = resources.displayMetrics
                val maxHeight = (displayMetrics.heightPixels * 0.88).toInt()
                bottomSheet.layoutParams.height = maxHeight
                behavior.peekHeight = maxHeight
            }
        }
        return dialog
    }

    private fun loadFieldsFromDocument() {
        val uri = documentUri ?: return
        binding.saveProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val fields = withContext(Dispatchers.IO) {
                try {
                    formEngine.extractFormFields(requireContext().applicationContext, uri)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }
            binding.saveProgressBar.visibility = View.GONE
            formFields = fields
            displayFields(fields)
        }
    }

    private fun displayFields(fields: List<FormField>) {
        binding.formSheetSubtitle.text = getString(R.string.forms_sheet_subtitle, fields.size)
        adapter.submitList(fields, restoredValues)
    }

    private fun saveFormFields() {
        val uri = documentUri ?: return
        val updates: List<FormFieldUpdate> = adapter.getFormUpdates()

        setSavingUiState(isSaving = true)

        lifecycleScope.launch {
            try {
                val result = formEngine.fillFormFields(
                    context = requireContext().applicationContext,
                    sourceUri = uri,
                    updates = updates,
                    outputUri = uri
                )

                setSavingUiState(isSaving = false)

                when (result) {
                    is PdfOperationResult.Success -> {
                        onFormSavedListener?.invoke(result.outputUri)
                        dismiss()
                    }
                    is PdfOperationResult.Error -> {
                        Snackbar.make(binding.root, result.userMessage, Snackbar.LENGTH_LONG).show()
                    }
                    is PdfOperationResult.Progress -> Unit
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                setSavingUiState(isSaving = false)
                val msg = e.localizedMessage ?: getString(R.string.error_title)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setSavingUiState(isSaving: Boolean) {
        binding.saveProgressBar.visibility = if (isSaving) View.VISIBLE else View.GONE
        binding.btnSaveForm.isEnabled = !isSaving
        binding.btnCancelForm.isEnabled = !isSaving
        binding.btnCloseSheet.isEnabled = !isSaving
        isCancelable = !isSaving
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentValues = adapter.getCurrentValues()
        val bundle = Bundle()
        for ((key, value) in currentValues) {
            when (value) {
                is Boolean -> bundle.putBoolean(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        outState.putBundle(KEY_SAVED_VALUES, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FillFormBottomSheetDialog"
        private const val ARG_URI = "arg_document_uri"
        private const val KEY_SAVED_VALUES = "key_saved_form_values"

        fun newInstance(uri: Uri, fields: List<FormField> = emptyList()): FillFormBottomSheetDialogFragment {
            return FillFormBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, uri.toString())
                }
                formFields = fields
            }
        }
    }
}
