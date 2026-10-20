package com.borgeiz.meutcc2026.transactions

import com.borgeiz.meutcc2026.util.formatDateDisplay
import com.borgeiz.meutcc2026.util.toStoredDate
import com.borgeiz.meutcc2026.util.splitDisplayDate
import com.borgeiz.meutcc2026.R
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.borgeiz.meutcc2026.data.CategoryRepository
import com.borgeiz.meutcc2026.model.PaymentMethods
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.util.formatAmountInputPtBr
import com.borgeiz.meutcc2026.util.parseAmountPtBr
import com.borgeiz.meutcc2026.util.setupMoneyInputPtBr
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class EditTransactionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_transaction)

        val etTitle       = findViewById<TextInputEditText>(R.id.etEditTitle)
        val etAmount      = findViewById<TextInputEditText>(R.id.etEditAmount)
        val spCategory    = findViewById<Spinner>(R.id.spEditCategory)
        val spPaymentMethod = findViewById<Spinner>(R.id.spEditPaymentMethod)
        val tvPaymentMethodLabel = findViewById<android.widget.TextView>(R.id.tvEditPaymentMethodLabel)
        val etDate        = findViewById<TextInputEditText>(R.id.etEditDate)
        val etDescription = findViewById<TextInputEditText>(R.id.etEditDescription)
        val btnUpdate     = findViewById<Button>(R.id.btnUpdate)
        val btnDelete     = findViewById<Button>(R.id.btnDelete)

        val id       = intent.getStringExtra("id") ?: ""
        val type     = intent.getStringExtra("type") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val paymentMethod = intent.getStringExtra("paymentMethod") ?: ""

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }

        if (type == "receita") {
            tvPaymentMethodLabel.visibility = android.view.View.GONE
            spPaymentMethod.visibility = android.view.View.GONE
        }

        etAmount.setupMoneyInputPtBr()

        etTitle.setText(intent.getStringExtra("title"))
        etAmount.setText(intent.getDoubleExtra("amount", 0.0).let {
            if (it == 0.0) "" else formatAmountInputPtBr(it)
        })
        etDate.setText(formatDateDisplay(intent.getStringExtra("date") ?: ""))
        etDescription.setText(intent.getStringExtra("description"))

        etDate.setOnClickListener {
            val parts = splitDisplayDate(etDate.text?.toString())
            val cal = Calendar.getInstance()
            if (parts != null) {
                parts[0].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
                parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
                parts[2].toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
            }
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    etDate.setText(formatDateDisplay(y, m + 1, d))
                    etDate.error = null
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        CategoryRepository(uid).loadConfig { config ->
            val baseCats = if (type == "receita") config.income else config.expense
            val cats = baseCats.toMutableList()
            if (category.isNotBlank() && cats.none { it.equals(category, ignoreCase = true) }) {
                cats.add(0, category)
            }
            spCategory.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                cats
            )
            val idx = cats.indexOfFirst { it.equals(category, ignoreCase = true) }
            if (idx >= 0) spCategory.setSelection(idx)

            val currentPaymentMethod = paymentMethod.ifBlank { PaymentMethods.NAO_INFORMADO }
            val paymentMethods = config.payment.ifEmpty { PaymentMethods.ALL }.toMutableList()
            if (paymentMethods.none { it.equals(currentPaymentMethod, ignoreCase = true) }) {
                paymentMethods.add(0, currentPaymentMethod)
            }
            spPaymentMethod.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                paymentMethods
            )
            val paymentIdx = paymentMethods.indexOfFirst { it.equals(currentPaymentMethod, ignoreCase = true) }
            if (paymentIdx >= 0) spPaymentMethod.setSelection(paymentIdx)
        }

        val ref = FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("transactions").child(id)

        btnUpdate.setOnClickListener {
            val titleStr  = etTitle.text?.toString()?.trim() ?: ""
            if (titleStr.isEmpty()) { etTitle.error = "Informe o titulo"; return@setOnClickListener }
            val amount = parseAmountPtBr(etAmount.text?.toString())
            if (amount == null || amount <= 0.0) { etAmount.error = "Informe um valor valido"; return@setOnClickListener }
            val dateStr = toStoredDate(etDate.text?.toString() ?: "")
            if (dateStr.isEmpty()) {
                etDate.error = "Informe a data"
                Toast.makeText(this, "Informe a data", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedPaymentMethod = spPaymentMethod.selectedItem?.toString() ?: ""
            val transaction = Transaction(
                type        = type,
                title       = titleStr,
                amount      = amount,
                category    = spCategory.selectedItem?.toString() ?: "",
                date        = dateStr,
                description = etDescription.text?.toString()?.trim() ?: "",
                paymentMethod = if (type == "receita" || selectedPaymentMethod == PaymentMethods.NAO_INFORMADO) "" else selectedPaymentMethod
            )
            ref.setValue(transaction).addOnSuccessListener {
                Toast.makeText(this, "Atualizado!", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener {
                Toast.makeText(this, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Excluir transacao")
                .setMessage("Tem certeza que deseja excluir esta transacao?")
                .setPositiveButton("Excluir") { _, _ ->
                    ref.removeValue().addOnSuccessListener {
                        Toast.makeText(this, "Excluido!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
                .also { d ->
                    d.window?.setBackgroundDrawable(
                        android.graphics.drawable.GradientDrawable().apply {
                            setColor(getColor(R.color.bg_card))
                            cornerRadius = 20 * resources.displayMetrics.density
                        }
                    )
                }
        }
    }
}
