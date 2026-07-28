package com.borgeiz.meutcc2026

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.borgeiz.meutcc2026.adapter.TransactionAdapter
import com.borgeiz.meutcc2026.data.TransactionsRepository
import com.borgeiz.meutcc2026.model.PaymentMethods
import com.borgeiz.meutcc2026.model.Transaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class TransactionsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var spFilterType: Spinner
    private lateinit var spFilterMonth: Spinner
    private lateinit var spFilterCategory: Spinner
    private lateinit var spFilterPayment: Spinner
    private val allTransactions = mutableListOf<Transaction>()

    private var txRepo: TransactionsRepository? = null
    private var txListener: ValueEventListener? = null

    private val monthLabels = listOf(
        "Todos os meses",
        "Janeiro", "Fevereiro", "Março", "Abril",
        "Maio", "Junho", "Julho", "Agosto",
        "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    private val paymentFilterOptions = listOf("Todas as formas de pagamento") + PaymentMethods.ALL + listOf(PaymentMethods.NAO_INFORMADO)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_transactions, container, false)

        recycler          = view.findViewById(R.id.recyclerTransactions)
        spFilterType      = view.findViewById(R.id.spFilterType)
        spFilterMonth     = view.findViewById(R.id.spFilterMonth)
        spFilterCategory  = view.findViewById(R.id.spFilterCategory)
        spFilterPayment   = view.findViewById(R.id.spFilterPayment)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        val typeOptions = listOf("Todos os tipos", "Receitas", "Despesas")
        spFilterType.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            typeOptions
        )

        // Pré-seleciona o mês atual
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        spFilterMonth.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            monthLabels
        )
        spFilterMonth.setSelection(currentMonth)

        spFilterPayment.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            paymentFilterOptions
        )

        val filterListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) = applyFilter()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spFilterType.onItemSelectedListener     = filterListener
        spFilterMonth.onItemSelectedListener    = filterListener
        spFilterCategory.onItemSelectedListener = filterListener
        spFilterPayment.onItemSelectedListener  = filterListener

        loadTransactions()
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        txRepo?.let { repo -> txListener?.let { repo.removeObserver(it) } }
        txListener = null
        txRepo = null
    }

    private fun loadTransactions() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val repo = TransactionsRepository(uid)
        txRepo = repo
        txListener = repo.observe { transactions ->
            if (!isAdded) return@observe
            allTransactions.clear()
            allTransactions.addAll(transactions)
            allTransactions.sortByDescending { it.date }
            refreshCategoryOptions()
            applyFilter()
        }
    }

    private fun refreshCategoryOptions() {
        val current = spFilterCategory.selectedItem?.toString()
        val categories = listOf("Todas as categorias") + allTransactions.map { it.category.ifBlank { "Outros" } }.distinct().sorted()
        spFilterCategory.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
        val idx = current?.let { categories.indexOf(it) } ?: -1
        spFilterCategory.setSelection(if (idx >= 0) idx else 0)
    }

    private fun applyFilter() {
        if (!::spFilterType.isInitialized || !::spFilterMonth.isInitialized) return

        val typePos  = spFilterType.selectedItemPosition   // 0=Todos, 1=Receitas, 2=Despesas
        val monthPos = spFilterMonth.selectedItemPosition  // 0=Todos, 1-12=mês
        val categoryPos = spFilterCategory.selectedItemPosition
        val paymentPos  = spFilterPayment.selectedItemPosition
        val selectedCategory = spFilterCategory.selectedItem?.toString()
        val selectedPayment  = spFilterPayment.selectedItem?.toString()

        val filtered = allTransactions.filter { t ->
            val typeOk = when (typePos) {
                1 -> t.type == "receita"
                2 -> t.type == "despesa"
                else -> true
            }
            val dateOk = if (monthPos == 0) {
                true
            } else {
                // date formato "yyyy-MM-dd"
                val parts = t.date.split("-")
                parts.size >= 2 && parts[1].toIntOrNull() == monthPos
            }
            val categoryOk = categoryPos == 0 ||
                t.category.ifBlank { "Outros" }.equals(selectedCategory, ignoreCase = true)
            val paymentOk = paymentPos == 0 ||
                t.paymentMethod.ifBlank { PaymentMethods.NAO_INFORMADO }.equals(selectedPayment, ignoreCase = true)

            typeOk && dateOk && categoryOk && paymentOk
        }

        val tvEmpty = view?.findViewById<TextView>(R.id.tvEmptyTransactions)
        if (filtered.isEmpty()) {
            tvEmpty?.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            tvEmpty?.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
        recycler.adapter = TransactionAdapter(filtered)
    }
}
