package com.borgeiz.meutcc2026

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    private lateinit var spFilterMonth: Spinner
    private lateinit var btnFilter: ImageButton
    private val allTransactions = mutableListOf<Transaction>()

    private val typeFilterOptions = listOf("Todos os tipos", "Receitas", "Despesas")
    private var filterType = typeFilterOptions[0]
    private var filterCategory = "Todas as categorias"
    private var filterPayment = "Todas as formas de pagamento"

    private var txRepo: TransactionsRepository? = null
    private var txListener: ValueEventListener? = null

    private val primaryBlue get() = 0xFF2563EB.toInt()

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

        recycler      = view.findViewById(R.id.recyclerTransactions)
        spFilterMonth = view.findViewById(R.id.spFilterMonth)
        btnFilter     = view.findViewById(R.id.btnFilter)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        // Pré-seleciona o mês atual
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        spFilterMonth.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            monthLabels
        )
        spFilterMonth.setSelection(currentMonth)
        spFilterMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) = applyFilter()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnFilter.setOnClickListener { showFilterDialog() }

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

    private fun categoryOptions() =
        listOf("Todas as categorias") + allTransactions.map { it.category.ifBlank { "Outros" } }.distinct().sorted()

    private fun refreshCategoryOptions() {
        // Se a categoria selecionada não existe mais nos lançamentos atuais, volta pro padrão.
        if (filterCategory !in categoryOptions()) filterCategory = "Todas as categorias"
    }

    private fun applyFilter() {
        if (!::spFilterMonth.isInitialized) return

        val monthPos = spFilterMonth.selectedItemPosition  // 0=Todos, 1-12=mês

        val filtered = allTransactions.filter { t ->
            val typeOk = when (filterType) {
                "Receitas" -> t.type == "receita"
                "Despesas" -> t.type == "despesa"
                else -> true
            }
            val dateOk = if (monthPos == 0) {
                true
            } else {
                // date formato "yyyy-MM-dd"
                val parts = t.date.split("-")
                parts.size >= 2 && parts[1].toIntOrNull() == monthPos
            }
            val categoryOk = filterCategory == "Todas as categorias" ||
                t.category.ifBlank { "Outros" }.equals(filterCategory, ignoreCase = true)
            val paymentOk = filterPayment == "Todas as formas de pagamento" ||
                t.paymentMethod.ifBlank { PaymentMethods.NAO_INFORMADO }.equals(filterPayment, ignoreCase = true)

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

    private fun showFilterDialog() {
        val ctx = requireContext()

        fun addLabel(container: LinearLayout, text: String) {
            container.addView(TextView(ctx).apply {
                this.text = text
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpToPx(14); it.bottomMargin = dpToPx(6) }
            })
        }

        fun spinnerFor(options: List<String>, selected: String): Spinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, options)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_spinner)
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46))
            setSelection(options.indexOf(selected).let { if (it >= 0) it else 0 })
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(4))
        }

        addLabel(root, "Tipo")
        val spType = spinnerFor(typeFilterOptions, filterType)
        root.addView(spType)

        val categories = categoryOptions()
        addLabel(root, "Categoria")
        val spCategory = spinnerFor(categories, filterCategory)
        root.addView(spCategory)

        addLabel(root, "Forma de pagamento")
        val spPayment = spinnerFor(paymentFilterOptions, filterPayment)
        root.addView(spPayment)

        AlertDialog.Builder(ctx)
            .setTitle("Filtros")
            .setView(root)
            .setPositiveButton("Aplicar") { _, _ ->
                filterType = typeFilterOptions.getOrElse(spType.selectedItemPosition) { typeFilterOptions[0] }
                filterCategory = categories.getOrElse(spCategory.selectedItemPosition) { categories[0] }
                filterPayment = paymentFilterOptions.getOrElse(spPayment.selectedItemPosition) { paymentFilterOptions[0] }
                applyFilter()
            }
            .setNeutralButton("Limpar") { _, _ ->
                filterType = typeFilterOptions[0]
                filterCategory = "Todas as categorias"
                filterPayment = paymentFilterOptions[0]
                applyFilter()
            }
            .setNegativeButton("Cancelar", null)
            .show()
            .also { d ->
                d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(primaryBlue)
                d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(primaryBlue)
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(primaryBlue)
                d.window?.setBackgroundDrawable(
                    GradientDrawable().apply {
                        setColor(ContextCompat.getColor(ctx, R.color.bg_card))
                        cornerRadius = dpToPx(20).toFloat()
                    }
                )
            }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
