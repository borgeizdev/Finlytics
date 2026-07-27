package com.borgeiz.meutcc2026

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.borgeiz.meutcc2026.adapter.TransactionAdapter
import com.borgeiz.meutcc2026.data.TransactionsRepository
import com.borgeiz.meutcc2026.data.expenseTotal
import com.borgeiz.meutcc2026.data.incomeTotal
import com.borgeiz.meutcc2026.model.Transaction
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class DashboardFragment : Fragment() {

    private lateinit var tvGreeting:     TextView
    private lateinit var tvBalance:      TextView
    private lateinit var tvIncome:       TextView
    private lateinit var tvExpense:      TextView
    private lateinit var tvIncomeTrend:  TextView
    private lateinit var tvExpenseTrend: TextView
    private lateinit var etSearch:       EditText
    private lateinit var btnClearSearch: TextView
    private lateinit var recyclerSearch: RecyclerView
    private lateinit var tvSearchEmpty:  TextView

    private val allTransactions = mutableListOf<Transaction>()

    // Listeners armazenados para serem removidos em onDestroyView
    private var txRepo:          TransactionsRepository? = null
    private var txListener:      ValueEventListener?      = null
    private var nameListener:    ValueEventListener?      = null
    private var nameRef:         DatabaseReference?       = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        tvGreeting     = view.findViewById(R.id.tvGreeting)
        tvBalance      = view.findViewById(R.id.tvBalance)
        tvIncome       = view.findViewById(R.id.tvIncome)
        tvExpense      = view.findViewById(R.id.tvExpense)
        tvIncomeTrend  = view.findViewById(R.id.tvIncomeTrend)
        tvExpenseTrend = view.findViewById(R.id.tvExpenseTrend)
        etSearch       = view.findViewById(R.id.etSearch)
        btnClearSearch = view.findViewById(R.id.btnClearSearch)
        recyclerSearch = view.findViewById(R.id.recyclerSearch)
        tvSearchEmpty  = view.findViewById(R.id.tvSearchEmpty)

        recyclerSearch.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<MaterialButton>(R.id.btnNovaTransferencia).setOnClickListener {
            (activity as? MainActivity)?.openAddTransaction()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                applySearch(query)
            }
        })

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
            recyclerSearch.visibility = View.GONE
            tvSearchEmpty.visibility  = View.GONE
        }

        loadUserName()
        loadSummaryAndTransactions()

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        txRepo?.let  { repo -> txListener?.let  { repo.removeObserver(it) } }
        nameRef?.let { ref -> nameListener?.let { ref.removeEventListener(it) } }
        txListener   = null
        nameListener = null
    }

    private fun applySearch(query: String) {
        if (query.isEmpty()) {
            recyclerSearch.visibility = View.GONE
            tvSearchEmpty.visibility  = View.GONE
            return
        }
        val results = allTransactions.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }
        if (results.isEmpty()) {
            recyclerSearch.visibility = View.GONE
            tvSearchEmpty.visibility  = View.VISIBLE
        } else {
            tvSearchEmpty.visibility  = View.GONE
            recyclerSearch.visibility = View.VISIBLE
            recyclerSearch.adapter    = TransactionAdapter(results)
        }
    }

    private fun loadUserName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        nameRef = FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("profile").child("name")
        nameListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.getValue(String::class.java)?.trim() ?: ""
                tvGreeting.text = if (name.isNotBlank()) "Olá, $name!" else "Olá!"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        nameRef!!.addValueEventListener(nameListener!!)
    }

    private fun loadSummaryAndTransactions() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val repo = TransactionsRepository(uid)
        txRepo = repo
        txListener = repo.observe { transactions ->
            allTransactions.clear()
            allTransactions.addAll(transactions)
            allTransactions.sortByDescending { it.date }

            val income  = transactions.incomeTotal()
            val expense = transactions.expenseTotal()
            val balance = income - expense
            tvBalance.text = "R$ %.2f".format(balance)
            tvBalance.setTextColor(
                if (balance >= 0) android.graphics.Color.WHITE
                else android.graphics.Color.parseColor("#FCA5A5")
            )
            tvIncome.text  = "R$ %.2f".format(income)
            tvExpense.text = "R$ %.2f".format(expense)
            updateTrends()

            val query = etSearch.text?.toString()?.trim() ?: ""
            if (query.isNotEmpty()) applySearch(query)
        }
    }

    private fun updateTrends() {
        if (!isAdded) return

        fun inMonth(t: Transaction, year: Int, month: Int): Boolean {
            val parts = t.date.split("-")
            return parts.size >= 2 && parts[0].toIntOrNull() == year && parts[1].toIntOrNull() == month
        }

        val cal      = Calendar.getInstance()
        val curYear  = cal.get(Calendar.YEAR)
        val curMonth = cal.get(Calendar.MONTH) + 1

        val prevCal   = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val prevYear  = prevCal.get(Calendar.YEAR)
        val prevMonth = prevCal.get(Calendar.MONTH) + 1

        val curExpense  = allTransactions.filter { it.type == "despesa" && inMonth(it, curYear, curMonth) }.sumOf { it.amount }
        val prevExpense = allTransactions.filter { it.type == "despesa" && inMonth(it, prevYear, prevMonth) }.sumOf { it.amount }
        val curIncome   = allTransactions.filter { it.type == "receita" && inMonth(it, curYear, curMonth) }.sumOf { it.amount }
        val prevIncome  = allTransactions.filter { it.type == "receita" && inMonth(it, prevYear, prevMonth) }.sumOf { it.amount }

        // Para despesas, subir é ruim (vermelho); para receitas, subir é bom (verde).
        applyTrend(tvExpenseTrend, curExpense, prevExpense, upIsGood = false)
        applyTrend(tvIncomeTrend, curIncome, prevIncome, upIsGood = true)
    }

    private fun applyTrend(target: TextView, current: Double, previous: Double, upIsGood: Boolean) {
        val ctx = context ?: return
        if (previous <= 0.0) {
            target.text = "Sem dado do mês passado"
            target.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
            return
        }
        val pct = (current - previous) / previous * 100.0
        val isUp   = pct > 0.5
        val isDown = pct < -0.5
        val arrow  = if (isUp) "▲" else if (isDown) "▼" else "—"
        val colorRes = when {
            !isUp && !isDown -> R.color.text_hint
            isUp == upIsGood -> R.color.income
            else             -> R.color.expense
        }
        target.text = "$arrow ${"%.0f".format(kotlin.math.abs(pct))}% vs mês passado"
        target.setTextColor(ContextCompat.getColor(ctx, colorRes))
    }
}
