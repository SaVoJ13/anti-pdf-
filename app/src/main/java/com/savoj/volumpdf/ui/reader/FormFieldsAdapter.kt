package com.savoj.volumpdf.ui.reader

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.savoj.volumpdf.R
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.databinding.ItemFormFieldCheckboxBinding
import com.savoj.volumpdf.databinding.ItemFormFieldChoiceBinding
import com.savoj.volumpdf.databinding.ItemFormFieldTextBinding

class FormFieldsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FormField>()
    private val valuesMap = mutableMapOf<String, Any>()

    fun submitList(fields: List<FormField>, initialValues: Map<String, Any>? = null) {
        items.clear()
        items.addAll(fields)

        valuesMap.clear()
        if (initialValues != null) {
            valuesMap.putAll(initialValues)
        }

        for (field in fields) {
            if (!valuesMap.containsKey(field.fullyQualifiedName)) {
                when (field) {
                    is FormField.Text -> valuesMap[field.fullyQualifiedName] = field.value
                    is FormField.CheckBox -> valuesMap[field.fullyQualifiedName] = field.isChecked
                    is FormField.Choice -> valuesMap[field.fullyQualifiedName] = field.selectedValue ?: field.options.firstOrNull() ?: ""
                }
            }
        }
        notifyDataSetChanged()
    }

    fun getFormUpdates(): List<FormFieldUpdate> {
        return items.map { field ->
            val value = valuesMap[field.fullyQualifiedName] ?: when (field) {
                is FormField.Text -> field.value
                is FormField.CheckBox -> field.isChecked
                is FormField.Choice -> field.selectedValue ?: ""
            }
            FormFieldUpdate(field.fullyQualifiedName, value)
        }
    }

    fun getCurrentValues(): Map<String, Any> {
        return HashMap(valuesMap)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FormField.Text -> VIEW_TYPE_TEXT
            is FormField.CheckBox -> VIEW_TYPE_CHECKBOX
            is FormField.Choice -> VIEW_TYPE_CHOICE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TEXT -> {
                val binding = ItemFormFieldTextBinding.inflate(inflater, parent, false)
                TextViewHolder(binding)
            }
            VIEW_TYPE_CHECKBOX -> {
                val binding = ItemFormFieldCheckboxBinding.inflate(inflater, parent, false)
                CheckBoxViewHolder(binding)
            }
            VIEW_TYPE_CHOICE -> {
                val binding = ItemFormFieldChoiceBinding.inflate(inflater, parent, false)
                ChoiceViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Невідомий viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val field = items[position]
        when (holder) {
            is TextViewHolder -> holder.bind(field as FormField.Text)
            is CheckBoxViewHolder -> holder.bind(field as FormField.CheckBox)
            is ChoiceViewHolder -> holder.bind(field as FormField.Choice)
        }
    }

    inner class TextViewHolder(
        private val binding: ItemFormFieldTextBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var textWatcher: TextWatcher? = null

        fun bind(field: FormField.Text) {
            val label = field.alternateTitle?.takeIf { it.isNotBlank() } ?: field.partialName
            binding.textInputLayout.hint = label

            if (field.isReadOnly) {
                binding.textInputLayout.helperText = binding.root.context.getString(R.string.form_read_only_badge)
                binding.editText.isEnabled = false
            } else {
                binding.textInputLayout.helperText = null
                binding.editText.isEnabled = true
            }

            if (field.isMultiline) {
                binding.editText.minLines = 3
                binding.editText.isSingleLine = false
                binding.editText.inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            } else {
                binding.editText.isSingleLine = true
                binding.editText.inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }

            textWatcher?.let { binding.editText.removeTextChangedListener(it) }

            val currentValue = (valuesMap[field.fullyQualifiedName] as? String) ?: field.value
            if (binding.editText.text?.toString() != currentValue) {
                binding.editText.setText(currentValue)
            }

            val newWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    valuesMap[field.fullyQualifiedName] = s?.toString().orEmpty()
                }
            }
            textWatcher = newWatcher
            binding.editText.addTextChangedListener(newWatcher)
        }
    }

    inner class CheckBoxViewHolder(
        private val binding: ItemFormFieldCheckboxBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(field: FormField.CheckBox) {
            val label = field.alternateTitle?.takeIf { it.isNotBlank() } ?: field.partialName
            binding.checkBox.text = label

            if (field.isReadOnly) {
                binding.checkboxHelperText.text = binding.root.context.getString(R.string.form_read_only_badge)
                binding.checkboxHelperText.visibility = View.VISIBLE
                binding.checkBox.isEnabled = false
            } else {
                binding.checkboxHelperText.visibility = View.GONE
                binding.checkBox.isEnabled = true
            }

            binding.checkBox.setOnCheckedChangeListener(null)
            val isChecked = (valuesMap[field.fullyQualifiedName] as? Boolean) ?: field.isChecked
            binding.checkBox.isChecked = isChecked

            binding.checkBox.setOnCheckedChangeListener { _, checked ->
                valuesMap[field.fullyQualifiedName] = checked
            }
        }
    }

    inner class ChoiceViewHolder(
        private val binding: ItemFormFieldChoiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var comboWatcher: TextWatcher? = null

        fun bind(field: FormField.Choice) {
            val label = field.alternateTitle?.takeIf { it.isNotBlank() } ?: field.partialName
            binding.choiceInputLayout.hint = label

            if (field.isReadOnly) {
                binding.choiceInputLayout.helperText = binding.root.context.getString(R.string.form_read_only_badge)
                binding.autoCompleteChoice.isEnabled = false
            } else {
                binding.choiceInputLayout.helperText = null
                binding.autoCompleteChoice.isEnabled = true
            }

            val context = binding.root.context
            val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, field.options)
            binding.autoCompleteChoice.setAdapter(adapter)

            comboWatcher?.let { binding.autoCompleteChoice.removeTextChangedListener(it) }

            val currentValue = (valuesMap[field.fullyQualifiedName] as? String)
                ?: field.selectedValue
                ?: field.options.firstOrNull().orEmpty()

            binding.autoCompleteChoice.setText(currentValue, false)

            binding.autoCompleteChoice.setOnItemClickListener { _, _, position, _ ->
                if (position in field.options.indices) {
                    valuesMap[field.fullyQualifiedName] = field.options[position]
                }
            }

            if (field.isEditableCombo) {
                binding.autoCompleteChoice.inputType = InputType.TYPE_CLASS_TEXT
                val watcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        valuesMap[field.fullyQualifiedName] = s?.toString().orEmpty()
                    }
                }
                comboWatcher = watcher
                binding.autoCompleteChoice.addTextChangedListener(watcher)
            } else {
                binding.autoCompleteChoice.inputType = InputType.TYPE_NULL
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_TEXT = 1
        private const val VIEW_TYPE_CHECKBOX = 2
        private const val VIEW_TYPE_CHOICE = 3
    }
}
