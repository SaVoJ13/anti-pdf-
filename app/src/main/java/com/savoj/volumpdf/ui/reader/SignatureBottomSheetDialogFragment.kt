package com.savoj.volumpdf.ui.reader

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.DialogSignatureBottomSheetBinding

class SignatureBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogSignatureBottomSheetBinding? = null
    private val binding get() = _binding!!

    var onSignatureConfirmed: ((Bitmap) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSignatureBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClearSignature.setOnClickListener {
            binding.signaturePadView.clear()
        }

        binding.btnInsertSignature.setOnClickListener {
            if (binding.signaturePadView.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    R.string.signature_error_empty,
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val bitmap = binding.signaturePadView.getTransparentSignatureBitmap()
            if (bitmap != null) {
                onSignatureConfirmed?.invoke(bitmap)
                dismiss()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.signature_error_empty,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SignatureBottomSheetDialogFragment"

        fun newInstance(): SignatureBottomSheetDialogFragment {
            return SignatureBottomSheetDialogFragment()
        }
    }
}
