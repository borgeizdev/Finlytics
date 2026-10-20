package com.borgeiz.meutcc2026.transactions

import com.borgeiz.meutcc2026.util.formatDateDisplay
import com.borgeiz.meutcc2026.util.toStoredDate
import com.borgeiz.meutcc2026.R
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.borgeiz.meutcc2026.data.CategoryRepository
import com.borgeiz.meutcc2026.model.CategoryConfig
import com.borgeiz.meutcc2026.model.PaymentMethods
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.util.parseAmountPtBr
import com.borgeiz.meutcc2026.util.setupMoneyInputPtBr
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class AddTransactionFragment : Fragment() {

    private var categoryConfig = CategoryConfig()

    private var selectedType = "receita"

    private lateinit var btnTypeReceita: MaterialButton
    private lateinit var btnTypeDespesa: MaterialButton
    private lateinit var actvCategory: AutoCompleteTextView
    private lateinit var actvPaymentMethod: AutoCompleteTextView
    private lateinit var tilPaymentMethod: com.google.android.material.textfield.TextInputLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_add_transaction, container, false)

        btnTypeReceita = view.findViewById(R.id.btnTypeReceita)
        btnTypeDespesa = view.findViewById(R.id.btnTypeDespesa)
        actvCategory   = view.findViewById(R.id.actvCategory)
        actvPaymentMethod = view.findViewById(R.id.actvPaymentMethod)
        tilPaymentMethod = view.findViewById(R.id.tilPaymentMethod)

        setPaymentOptions(PaymentMethods.ALL)

        val etTitle       = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etAmount      = view.findViewById<TextInputEditText>(R.id.etAmount)
        val etDate        = view.findViewById<TextInputEditText>(R.id.etDate)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnSaveTransaction)

        etAmount.setupMoneyInputPtBr()

        setType("receita")

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            CategoryRepository(uid).loadConfig { config ->
                if (!isAdded) return@loadConfig
                categoryConfig = config
                if (config.payment.isNotEmpty()) setPaymentOptions(config.payment)
                setType(selectedType)
            }
        }

        btnTypeReceita.setOnClickListener { setType("receita") }
        btnTypeDespesa.setOnClickListener { setType("despesa") }

        etDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, y, m, d -> etDate.setText(formatDateDisplay(y, m + 1, d)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnSave.setOnClickListener {

            val uid = FirebaseAuth.getInstance().currentUser?.uid

            if (uid == null) {
                Toast.makeText(requireContext(), "Usuário não autenticado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val titleStr  = etTitle.text?.toString()?.trim() ?: ""
            val dateStr   = toStoredDate(etDate.text?.toString() ?: "")
            val descStr   = etDescription.text?.toString()?.trim() ?: ""

            if (titleStr.isEmpty()) { etTitle.error = "Informe o título"; return@setOnClickListener }

            val amount = parseAmountPtBr(etAmount.text?.toString())
            if (amount == null || amount <= 0.0) { etAmount.error = "Informe um valor válido"; return@setOnClickListener }

            val typeSnapshot  = selectedType

            val ref = FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("transactions")

            val key = ref.push().key ?: return@setOnClickListener

            ref.child(key).setValue(
                Transaction(
                    type        = typeSnapshot,
                    title       = titleStr,
                    amount      = amount,
                    category    = actvCategory.text.toString(),
                    date        = dateStr,
                    description = descStr,
                    paymentMethod = if (typeSnapshot == "receita") "" else actvPaymentMethod.text.toString()
                )
            )
                .addOnSuccessListener {
                    val ctx = context ?: return@addOnSuccessListener
                    Toast.makeText(ctx, "Transação salva!", Toast.LENGTH_SHORT).show()
                    etTitle.text?.clear()
                    etAmount.text?.clear()
                    etDate.text?.clear()
                    etDescription.text?.clear()
                }
                .addOnFailureListener {
                    val ctx = context ?: return@addOnFailureListener
                    Toast.makeText(ctx, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
                }

        }

        return view
    }

    private fun setPaymentOptions(options: List<String>) {
        actvPaymentMethod.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, options)
        )
        val current = actvPaymentMethod.text.toString()
        if (options.none { it.equals(current, ignoreCase = true) }) {
            actvPaymentMethod.setText(options[0], false)
        }
    }

    private fun setType(type: String) {
        if (!isAdded) return
        selectedType = type
        val ctx           = requireContext()
        val activeColor   = ctx.getColor(R.color.primary)
        val activeText    = ctx.getColor(R.color.on_primary)
        val inactiveBg    = ctx.getColor(R.color.bg_card_subtle)
        val inactiveText  = ctx.getColor(R.color.text_secondary)
        val borderColor   = ctx.getColor(R.color.border)

        if (type == "receita") {
            btnTypeReceita.backgroundTintList = ColorStateList.valueOf(activeColor)
            btnTypeReceita.setTextColor(activeText)
            btnTypeReceita.setStrokeColor(ColorStateList.valueOf(activeColor))
            btnTypeDespesa.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            btnTypeDespesa.setTextColor(inactiveText)
            btnTypeDespesa.setStrokeColor(ColorStateList.valueOf(borderColor))
        } else {
            btnTypeDespesa.backgroundTintList = ColorStateList.valueOf(activeColor)
            btnTypeDespesa.setTextColor(activeText)
            btnTypeDespesa.setStrokeColor(ColorStateList.valueOf(activeColor))
            btnTypeReceita.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            btnTypeReceita.setTextColor(inactiveText)
            btnTypeReceita.setStrokeColor(ColorStateList.valueOf(borderColor))
        }

        val cats = if (type == "receita") categoryConfig.income else categoryConfig.expense
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, cats)
        actvCategory.setAdapter(adapter)
        actvCategory.setText(cats.firstOrNull() ?: "", false)

        tilPaymentMethod.visibility = if (type == "receita") View.GONE else View.VISIBLE
    }
}
