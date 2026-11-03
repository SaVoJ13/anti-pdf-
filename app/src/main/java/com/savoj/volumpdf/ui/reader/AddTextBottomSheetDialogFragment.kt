package com.savoj.volumpdf.ui.reader

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.DialogAddTextBottomSheetBinding

class AddTextBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogAddTextBottomSheetBinding? = null
    private val binding get() = _binding!!

    var onTextConfirmed: ((text: String, fontSize: Float, color: Int) -> Unit)? = null

    private var selectedColor: Int = Color.BLACK

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddTextBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // За замовчуванням
        binding.toggleColorGroup.check(R.id.btnColorBlack)
        updateFontSizeLabel(binding.sliderFontSize.value)

        binding.sliderFontSize.addOnChangeListener { _, value, _ ->
            updateFontSizeLabel(value)
        }

        binding.toggleColorGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedColor = when (checkedId) {
                    R.id.btnColorRed -> 0xFFD32F2F.toInt()
                    R.id.btnColorBlue -> 0xFF1976D2.toInt()
                    R.id.btnColorGreen -> 0xFF388E3C.toInt()
                    else -> 0xFF000000.toInt()
                }
            }
        }

        binding.btnAddTextConfirm.setOnClickListener {
            val text = binding.etAnnotationText.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                binding.textInputLayout.error = getString(R.string.text_error_empty)
                return@setOnClickListener
            }
            binding.textInputLayout.error = null

            val fontSize = binding.sliderFontSize.value
            onTextConfirmed?.invoke(text, fontSize, selectedColor)
            dismiss()
        }
    }

    private fun updateFontSizeLabel(sizePt: Float) {
        binding.tvFontSizeLabel.text = getString(R.string.text_size_format, sizePt.toInt())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddTextBottomSheetDialogFragment"

        fun newInstance(): AddTextBottomSheetDialogFragment {
            return AddTextBottomSheetDialogFragment()
        }
    }
}
