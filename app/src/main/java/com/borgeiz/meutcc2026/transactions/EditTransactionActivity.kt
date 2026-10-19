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

        // Essa tela não busca a transação no Firebase de novo: recebe tudo pronto
        // via Intent (montado em TransactionAdapter, no clique de um item da lista).
        // Evita uma nova consulta ao banco só pra reabrir dados que a lista já tinha.
        val id       = intent.getStringExtra("id") ?: ""
        val type     = intent.getStringExtra("type") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val paymentMethod = intent.getStringExtra("paymentMethod") ?: ""

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }

        // Receita não tem "forma de pagamento" no sentido de Pix/cartão/dinheiro.
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
                { _, y, m, d -> etDate.setText(formatDateDisplay(y, m + 1, d)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Categorias conforme tipo (carregadas do Firebase, com fallback pra categoria antiga)
        CategoryRepository(uid).loadConfig { config ->
            val baseCats = if (type == "receita") config.income else config.expense
            val cats = baseCats.toMutableList()
            if (category.isNotBlank() && cats.none { it.equals(category, ignoreCase = true) }) {
                // Categoria salva não está mais na lista atual: preserva o valor original
                // como opção em vez de deixar o spinner cair no índice 0 e trocá-la sem avisar.
                cats.add(0, category)
            }
            spCategory.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                cats
            )
            val idx = cats.indexOfFirst { it.equals(category, ignoreCase = true) }
            if (idx >= 0) spCategory.setSelection(idx)

            // Forma de pagamento (transações antigas podem não ter o campo preenchido).
            // Lista personalizada em Perfil > Categorias > Pagamento; se a forma salva
            // não estiver mais nela, entra como opção para não ser trocada sem aviso.
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

        // Atualizar segue o mesmo padrão do cadastro (AddTransactionFragment): monta
        // um Transaction novo com os valores atuais dos campos e chama setValue —
        // só que "ref" já aponta pro id existente, então em vez de criar um nó novo,
        // SOBRESCREVE o que já existia. Não existe "update parcial" campo por campo.
        btnUpdate.setOnClickListener {
            val titleStr  = etTitle.text?.toString()?.trim() ?: ""
            if (titleStr.isEmpty()) { etTitle.error = "Informe o titulo"; return@setOnClickListener }
            val amount = parseAmountPtBr(etAmount.text?.toString())
            if (amount == null || amount <= 0.0) { etAmount.error = "Informe um valor valido"; return@setOnClickListener }

            val selectedPaymentMethod = spPaymentMethod.selectedItem?.toString() ?: ""
            val transaction = Transaction(
                type        = type,
                title       = titleStr,
                amount      = amount,
                category    = spCategory.selectedItem?.toString() ?: "",
                date        = toStoredDate(etDate.text?.toString() ?: ""),
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
                // removeValue() apaga o nó inteiro daquele id no Firebase — exclusão
                // de verdade, não uma marcação de "inativo". Por isso a confirmação
                // antes: não tem como desfazer depois.
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
