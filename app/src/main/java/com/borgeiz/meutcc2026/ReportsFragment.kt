package com.borgeiz.meutcc2026

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.borgeiz.meutcc2026.data.TransactionsRepository
import com.borgeiz.meutcc2026.data.expenseTotal
import com.borgeiz.meutcc2026.data.incomeTotal
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.util.buildBreakdownRows
import com.borgeiz.meutcc2026.util.isDarkMode
import com.borgeiz.meutcc2026.util.setupBreakdownPieChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class ReportsFragment : Fragment() {

    private val monthLabels = listOf(
        "Todos", "Jan", "Fev", "Mar", "Abr",
        "Mai", "Jun", "Jul", "Ago",
        "Set", "Out", "Nov", "Dez"
    )

    private val monthLabelsFull = listOf(
        "Todos os meses",
        "Janeiro", "Fevereiro", "Março", "Abril",
        "Maio", "Junho", "Julho", "Agosto",
        "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    private var txRepoRef: TransactionsRepository? = null
    private var txListenerRef: ValueEventListener? = null

    override fun onDestroyView() {
        super.onDestroyView()
        txRepoRef?.let { repo -> txListenerRef?.let { repo.removeObserver(it) } }
        txRepoRef = null
        txListenerRef = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_reports, container, false)

        val tvIncome        = view.findViewById<TextView>(R.id.tvIncome)
        val tvExpense       = view.findViewById<TextView>(R.id.tvExpense)
        val tvBalance       = view.findViewById<TextView>(R.id.tvBalance)
        val llCatBreakdown  = view.findViewById<LinearLayout>(R.id.llCatBreakdown)
        val pieChart        = view.findViewById<PieChart>(R.id.pieChart)
        val barChart        = view.findViewById<BarChart>(R.id.barChart)
        val spCatMonth      = view.findViewById<Spinner>(R.id.spCatMonth)
        val btnPaymentMethodAnalysis = view.findViewById<MaterialButton>(R.id.btnPaymentMethodAnalysis)

        btnPaymentMethodAnalysis.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.frameContainer, PaymentMethodReportFragment())
                .addToBackStack(null)
                .commit()
        }

        spCatMonth.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            monthLabelsFull
        )
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        spCatMonth.setSelection(currentMonth)

        pieChart.setNoDataText("")
        barChart.setNoDataText("")

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return view
        val db   = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        val txRepo = TransactionsRepository(uid)
        txRepoRef = txRepo

        var allTransactions = listOf<Transaction>()
        var adjustment = 0.0

        fun refreshCategoryChart(monthFilter: Int) {
            val expenses = allTransactions.filter { t ->
                t.type == "despesa" && (monthFilter == 0 || run {
                    val parts = t.date.split("-")
                    parts.size >= 2 && parts[1].toIntOrNull() == monthFilter
                })
            }

            val categoryTotals = mutableMapOf<String, Double>()
            var filteredExpenseTotal = 0.0
            for (t in expenses) {
                filteredExpenseTotal += t.amount
                val cat = t.category.ifBlank { "Outros" }
                categoryTotals[cat] = (categoryTotals[cat] ?: 0.0) + t.amount
            }

            if (categoryTotals.isEmpty()) {
                pieChart.visibility = View.GONE
                llCatBreakdown.removeAllViews()
                llCatBreakdown.addView(TextView(requireContext()).apply {
                    text = "Nenhuma despesa registrada para este período."
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                    textSize = 14f
                })
            } else {
                pieChart.visibility = View.VISIBLE
                val isDark = isDarkMode(requireContext())
                setupBreakdownPieChart(requireContext(), isDark, pieChart, categoryTotals)
                buildBreakdownRows(requireContext(), isDark, llCatBreakdown, categoryTotals, filteredExpenseTotal)
            }
        }

        fun renderSummary() {
            val totalIncome  = allTransactions.incomeTotal()
            val totalExpense = allTransactions.expenseTotal()
            val balance = totalIncome - totalExpense + adjustment
            tvIncome.text  = "R$ %.2f".format(totalIncome)
            tvExpense.text = "R$ %.2f".format(totalExpense)
            tvBalance.text = "R$ %.2f".format(balance)

            setupBarChart(barChart, allTransactions)
            refreshCategoryChart(spCatMonth.selectedItemPosition)
        }

        spCatMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                refreshCategoryChart(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        db.child("balanceAdjustment")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(adjSnapshot: DataSnapshot) {
                    adjustment = adjSnapshot.getValue(Double::class.java) ?: 0.0
                    renderSummary()
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        txListenerRef = txRepo.observe { transactions ->
            if (!isAdded) return@observe
            allTransactions = transactions
            renderSummary()
        }

        return view
    }

    private fun setupBarChart(barChart: BarChart, transactions: List<Transaction>) {
        val monthlyIncome  = mutableMapOf<Int, Float>()
        val monthlyExpense = mutableMapOf<Int, Float>()

        for (t in transactions) {
            val parts = t.date.split("-")
            if (parts.size < 2) continue
            val month = parts[1].toIntOrNull() ?: continue
            if (t.type == "receita") monthlyIncome[month]  = (monthlyIncome[month]  ?: 0f) + t.amount.toFloat()
            if (t.type == "despesa") monthlyExpense[month] = (monthlyExpense[month] ?: 0f) + t.amount.toFloat()
        }

        val allMonths = (monthlyIncome.keys + monthlyExpense.keys).toSortedSet()
        if (allMonths.isEmpty()) {
            barChart.visibility = View.GONE
            return
        }
        barChart.visibility = View.VISIBLE

        val groupSpace = 0.4f
        val barSpace   = 0.05f
        val barWidth   = 0.25f

        val incomeEntries  = mutableListOf<BarEntry>()
        val expenseEntries = mutableListOf<BarEntry>()
        val labels         = mutableListOf<String>()

        allMonths.forEachIndexed { idx, month ->
            incomeEntries.add(BarEntry(idx.toFloat(), monthlyIncome[month]  ?: 0f))
            expenseEntries.add(BarEntry(idx.toFloat(), monthlyExpense[month] ?: 0f))
            labels.add(monthLabels.getOrElse(month) { "$month" })
        }

        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val axisTextColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#6B7280")
        val axisGridColor = if (isDark) Color.parseColor("#2D3748") else Color.parseColor("#E5E7EB")

        val incomeSet = BarDataSet(incomeEntries, "Receitas").apply {
            color = Color.parseColor("#16A34A")
            valueTextSize = 9f
            valueTextColor = axisTextColor
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float) = if (value == 0f) "" else "R$${value.toInt()}"
            }
        }
        val expenseSet = BarDataSet(expenseEntries, "Despesas").apply {
            color = Color.parseColor("#DC2626")
            valueTextSize = 9f
            valueTextColor = axisTextColor
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float) = if (value == 0f) "" else "R$${value.toInt()}"
            }
        }

        val barData = BarData(incomeSet, expenseSet).apply { this.barWidth = barWidth }
        val groupWidthVal = barWidth * 2 + barSpace * 2 + groupSpace

        barChart.apply {
            data = barData
            barData.groupBars(0f, groupSpace, barSpace)
            description.isEnabled = false
            setFitBars(true)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                setCenterAxisLabels(true)
                granularity = 1f
                axisMinimum = 0f
                axisMaximum = groupWidthVal * allMonths.size
                setDrawGridLines(false)
                textSize = 11f
                textColor = axisTextColor
            }
            axisLeft.apply {
                axisMinimum = 0f
                textColor = axisTextColor
                gridColor = axisGridColor
            }
            axisRight.isEnabled = false
            legend.textColor = axisTextColor
            setBackgroundColor(Color.TRANSPARENT)
            animateY(600)
            invalidate()
        }
    }
}
