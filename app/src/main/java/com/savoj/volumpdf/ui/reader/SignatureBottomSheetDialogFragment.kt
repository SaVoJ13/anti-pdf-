package com.savoj.volumpdf.ui.reader

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import com.savoj.volumpdf.ui.common.SystemEdges
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.DialogSignatureBottomSheetBinding

class SignatureBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogSignatureBottomSheetBinding? = null
    private val binding get() = _binding!!

    var onSignatureConfirmed: ((Bitmap) -> Unit)? = null

    private var edges = SystemEdges.NONE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSignatureBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Шторка завжди відкривається повністю розгорнутою (без «згорнутого» стану). */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { applyOrientation() }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClearSignature.setOnClickListener {
            binding.signaturePadView.clear()
        }

        binding.btnInsertSignature.setOnClickListener { onInsertClicked() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            edges = SystemEdges.from(insets)
            applyOrientation()
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /**
     * Активність сама обробляє поворот (configChanges), тому вікно не перестворюється: розкладку під
     * орієнтацію застосовуємо програмно. Ландшафт: шторка на всю висоту, полотно гнучке, кнопки внизу видимі.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.post { applyOrientation() }
    }

    private fun applyOrientation() {
        val b = _binding ?: return
        val d = dialog as? BottomSheetDialog ?: return
        val sheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val density = resources.displayMetrics.density
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val rootLp = b.signatureRoot.layoutParams
        rootLp.height = if (landscape) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        b.signatureRoot.layoutParams = rootLp
        val pad = ((if (landscape) 12 else 20) * density).toInt()
        // Відступи від системних панелей: кнопки завжди над навігацією, шторка не лізе під рядок стану.
        b.signatureRoot.setPadding(pad + edges.left, if (landscape) edges.top else pad, pad + edges.right, pad + edges.bottom)
        b.dragHandle.visibility = if (landscape) View.GONE else View.VISIBLE

        val cardLp = b.signaturePadCard.layoutParams as LinearLayout.LayoutParams
        if (landscape) {
            cardLp.height = 0
            cardLp.weight = 1f
            cardLp.topMargin = (8 * density).toInt()
        } else {
            cardLp.height = (220 * density).toInt()
            cardLp.weight = 0f
            cardLp.topMargin = (16 * density).toInt()
        }
        b.signaturePadCard.layoutParams = cardLp

        sheet.layoutParams.height = if (landscape) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        sheet.requestLayout()
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.skipCollapsed = true
        behavior.isFitToContents = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun onInsertClicked() {
        if (binding.signaturePadView.isEmpty()) {
            Toast.makeText(requireContext(), R.string.signature_error_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = binding.signaturePadView.getTransparentSignatureBitmap()
        if (bitmap == null) {
            Toast.makeText(requireContext(), R.string.signature_error_empty, Toast.LENGTH_SHORT).show()
            return
        }
        // Захист від подвійного тапа: після першого натиску кнопки вимкнено, вікно закривається після успіху.
        binding.btnInsertSignature.isEnabled = false
        binding.btnClearSignature.isEnabled = false
        onSignatureConfirmed?.invoke(bitmap)
        dismissAllowingStateLoss()
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
