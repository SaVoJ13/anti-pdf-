package com.savoj.volumpdf.ui.reader

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.DialogFillFormBottomSheetBinding
import kotlinx.coroutines.launch

/**
 * Шторка «Форми» (етап 3d). Файл вона НЕ пише: показує значення з файла й поверх них незбережені зміни та
 * редагує незбережений стан (ReaderViewModel.formEdits). Збереження — єдине «Зберегти» у читачі.
 * «Застосувати» кладе в незбережене лише поля, що відрізняються від файла.
 */
class FillFormBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogFillFormBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReaderViewModel by activityViewModels()
    private val adapter = FormFieldsAdapter()

    /** false — права документа не дозволяють заповнення: лише перегляд з поясненням. */
    private var canFillForms = true
    private var restoredValues: Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        canFillForms = arguments?.getBoolean(ARG_CAN_FILL, true) ?: true
        adapter.canFill = canFillForms

        // Введене в шторці, але ще не застосоване, переживає перестворення (am kill із відкритою шторкою).
        val bundle = savedInstanceState?.getBundle(KEY_SAVED_VALUES)
        if (bundle != null) {
            val map = LinkedHashMap<String, Any>()
            for (key in bundle.keySet()) {
                val v = bundle.get(key)
                if (v is String || v is Boolean) map[key] = v
            }
            restoredValues = map
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
        binding.formReadOnlyNote.visibility = if (canFillForms) View.GONE else View.VISIBLE
        binding.btnApplyForm.visibility = if (canFillForms) View.VISIBLE else View.GONE
        binding.btnResetForms.visibility = if (canFillForms) View.VISIBLE else View.GONE

        binding.btnCloseSheet.setOnClickListener { dismiss() }
        binding.btnCancelForm.setOnClickListener { dismiss() }

        binding.btnApplyForm.setOnClickListener {
            viewModel.applyFormEntered(adapter.getCurrentValues())
            dismiss()
        }

        binding.btnResetForms.setOnClickListener {
            // «Скинути всі зміни форм»: незбережені значення форм прибираються, шторка показує значення з файла.
            viewModel.resetFormEdits()
            restoredValues = null
            display(viewModel.formFields.value, emptyMap())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.formFields.collect { fields ->
                    // Не перезаписуємо те, що користувач уже вводить: оновлення лише коли список змінився.
                    if (fields != shownFields) display(fields, restoredValues ?: viewModel.formEdits.value)
                }
            }
        }
    }

    private var shownFields: List<com.savoj.volumpdf.core.pdf.model.FormField>? = null

    private fun display(fields: List<com.savoj.volumpdf.core.pdf.model.FormField>, values: Map<String, Any>) {
        shownFields = fields
        binding.formSheetSubtitle.text = getString(R.string.forms_sheet_subtitle, fields.size)
        adapter.submitList(fields, values)
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
                val maxHeight = (resources.displayMetrics.heightPixels * 0.88).toInt()
                bottomSheet.layoutParams.height = maxHeight
                behavior.peekHeight = maxHeight
            }
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val bundle = Bundle()
        for ((key, value) in adapter.getCurrentValues()) {
            when (value) {
                is Boolean -> bundle.putBoolean(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        outState.putBundle(KEY_SAVED_VALUES, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shownFields = null
        _binding = null
    }

    companion object {
        const val TAG = "FillFormBottomSheetDialog"
        private const val ARG_CAN_FILL = "arg_can_fill_forms"
        private const val KEY_SAVED_VALUES = "key_saved_form_values"

        fun newInstance(canFillForms: Boolean): FillFormBottomSheetDialogFragment =
            FillFormBottomSheetDialogFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_CAN_FILL, canFillForms) }
            }
    }
}
