package com.borgeiz.meutcc2026

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
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
import com.borgeiz.meutcc2026.data.GoalRepository
import com.borgeiz.meutcc2026.data.TransactionsRepository
import com.borgeiz.meutcc2026.data.expenseTotal
import com.borgeiz.meutcc2026.data.incomeTotal
import com.borgeiz.meutcc2026.model.Goal
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.util.buildBreakdownRows
import com.borgeiz.meutcc2026.util.categoryTotalsForMonth
import com.borgeiz.meutcc2026.util.chartPaletteHex
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

    companion object {
        // Em memória (não persiste no disco): mantém a seleção do usuário ao navegar
        // entre abas durante a sessão, mas volta pro padrão "Todos os meses" quando
        // o processo do app morre e é reaberto.
        private var lastCatMonthSelection = 0
    }

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

    private data class CategoryDelta(
        val category: String,
        val current: Double,
        val previous: Double,
        val pct: Double
    )

    private var txRepoRef: TransactionsRepository? = null
    private var txListenerRef: ValueEventListener? = null
    private var goalRepoRef: GoalRepository? = null
    private var goalListenerRef: ValueEventListener? = null

    override fun onDestroyView() {
        super.onDestroyView()
        txRepoRef?.let { repo -> txListenerRef?.let { repo.removeObserver(it) } }
        txRepoRef = null
        txListenerRef = null
        goalRepoRef?.let { repo -> goalListenerRef?.let { repo.removeObserver(it) } }
        goalRepoRef = null
        goalListenerRef = null
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

        val tvAvgPerDay        = view.findViewById<TextView>(R.id.tvAvgPerDay)
        val tvTopExpenseValue  = view.findViewById<TextView>(R.id.tvTopExpenseValue)
        val tvTopExpenseTitle  = view.findViewById<TextView>(R.id.tvTopExpenseTitle)
        val tvTopIncomeValue   = view.findViewById<TextView>(R.id.tvTopIncomeValue)
        val tvTopIncomeTitle   = view.findViewById<TextView>(R.id.tvTopIncomeTitle)
        val tvHeroBadge        = view.findViewById<TextView>(R.id.tvHeroBadge)
        val tvHeroValue        = view.findViewById<TextView>(R.id.tvHeroValue)
        val tvHeroDetail       = view.findViewById<TextView>(R.id.tvHeroDetail)
        val llCategoryVariation = view.findViewById<LinearLayout>(R.id.llCategoryVariation)
        val weekdayChart       = view.findViewById<BarChart>(R.id.weekdayChart)
        val tvWeekdayEmpty     = view.findViewById<TextView>(R.id.tvWeekdayEmpty)
        val llGoalsProgress    = view.findViewById<LinearLayout>(R.id.llGoalsProgress)
        weekdayChart.setNoDataText("")

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
        spCatMonth.setSelection(lastCatMonthSelection)

        pieChart.setNoDataText("")
        barChart.setNoDataText("")

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return view
        val db   = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        val txRepo = TransactionsRepository(uid)
        txRepoRef = txRepo

        var allTransactions = listOf<Transaction>()
        var adjustment = 0.0
        var goals = listOf<Goal>()

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

        fun inMonth(t: Transaction, year: Int, month: Int): Boolean {
            val parts = t.date.split("-")
            return parts.size >= 2 && parts[0].toIntOrNull() == year && parts[1].toIntOrNull() == month
        }

        fun refreshInsights() {
            if (!isAdded) return
            val ctx = requireContext()

            val cal      = Calendar.getInstance()
            val curYear  = cal.get(Calendar.YEAR)
            val curMonth = cal.get(Calendar.MONTH) + 1
            val today    = cal.get(Calendar.DAY_OF_MONTH)

            val prevCal   = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            val prevYear  = prevCal.get(Calendar.YEAR)
            val prevMonth = prevCal.get(Calendar.MONTH) + 1

            val curExpenses  = allTransactions.filter { it.type == "despesa" && inMonth(it, curYear, curMonth) }
            val prevExpenses = allTransactions.filter { it.type == "despesa" && inMonth(it, prevYear, prevMonth) }
            val curIncomes   = allTransactions.filter { it.type == "receita" && inMonth(it, curYear, curMonth) }

            val curTotal  = curExpenses.sumOf { it.amount }
            val prevTotal = prevExpenses.sumOf { it.amount }

            // Média diária e maiores lançamentos
            tvAvgPerDay.text = "R$ ${"%.2f".format(if (today > 0) curTotal / today else 0.0)}"

            val topExpense = curExpenses.maxByOrNull { it.amount }
            tvTopExpenseValue.text = topExpense?.let { "R$ ${"%.2f".format(it.amount)}" } ?: "—"
            tvTopExpenseTitle.text = topExpense?.title?.ifBlank { "Sem título" } ?: "Nenhuma despesa"

            val topIncome = curIncomes.maxByOrNull { it.amount }
            tvTopIncomeValue.text = topIncome?.let { "R$ ${"%.2f".format(it.amount)}" } ?: "—"
            tvTopIncomeTitle.text = topIncome?.title?.ifBlank { "Sem título" } ?: "Nenhuma receita"

            // Hero: variação total do mês
            val prevMonthName = monthLabelsFull.getOrElse(prevMonth) { "" }
            if (prevTotal <= 0.0) {
                tvHeroBadge.visibility = View.GONE
                tvHeroValue.text = "R$ ${"%.2f".format(curTotal)}"
                tvHeroDetail.text = "Sem dados de $prevMonthName para comparar."
            } else {
                val deltaPct = (curTotal - prevTotal) / prevTotal * 100.0
                val isIncrease = deltaPct > 0.5
                val isDecrease = deltaPct < -0.5
                val arrow = if (isIncrease) "▲" else if (isDecrease) "▼" else "—"
                val badgeBgRes  = if (isIncrease) R.color.expense_bg else R.color.income_bg
                val badgeTxtRes = if (isIncrease) R.color.expense else R.color.income

                tvHeroBadge.visibility = View.VISIBLE
                tvHeroBadge.text = "$arrow ${"%.0f".format(kotlin.math.abs(deltaPct))}%"
                tvHeroBadge.setTextColor(ContextCompat.getColor(ctx, badgeTxtRes))
                tvHeroBadge.background = GradientDrawable().apply {
                    cornerRadius = 999f
                    setColor(ContextCompat.getColor(ctx, badgeBgRes))
                }
                tvHeroValue.text = "R$ ${"%.2f".format(curTotal)}"

                val diff = kotlin.math.abs(curTotal - prevTotal)
                val comparativo = if (isIncrease) "a mais" else if (isDecrease) "a menos" else "praticamente igual a"
                tvHeroDetail.text =
                    "Você gastou R$ ${"%.2f".format(diff)} $comparativo que em $prevMonthName (R$ ${"%.2f".format(prevTotal)})"
            }

            // Variação por categoria
            val curCat  = curExpenses.groupBy { it.category.ifBlank { "Outros" } }.mapValues { (_, v) -> v.sumOf { it.amount } }
            val prevCat = prevExpenses.groupBy { it.category.ifBlank { "Outros" } }.mapValues { (_, v) -> v.sumOf { it.amount } }
            val allCats = curCat.keys + prevCat.keys
            val deltas = allCats.map { cat ->
                val cur  = curCat[cat] ?: 0.0
                val prev = prevCat[cat] ?: 0.0
                val pct = when {
                    prev > 0.0 -> (cur - prev) / prev * 100.0
                    cur > 0.0  -> 100.0
                    else       -> 0.0
                }
                CategoryDelta(cat, cur, prev, pct)
            }.sortedByDescending { it.pct }.take(6)
            buildCategoryVariationRows(llCategoryVariation, deltas)

            // Dia da semana com mais gasto
            if (curExpenses.isEmpty()) {
                weekdayChart.visibility = View.GONE
                tvWeekdayEmpty.visibility = View.VISIBLE
            } else {
                weekdayChart.visibility = View.VISIBLE
                tvWeekdayEmpty.visibility = View.GONE
                setupWeekdayChart(weekdayChart, curExpenses)
            }
        }

        fun refreshGoalsProgress() {
            if (!isAdded) return
            val cal = Calendar.getInstance()
            val curYear  = cal.get(Calendar.YEAR)
            val curMonth = cal.get(Calendar.MONTH) + 1
            val categoryTotals = allTransactions.categoryTotalsForMonth("despesa", curYear, curMonth)
            val overallTotal = categoryTotals.values.sum()
            buildGoalsProgress(llGoalsProgress, goals, categoryTotals, overallTotal)
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
            refreshInsights()
            refreshGoalsProgress()
        }

        spCatMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                lastCatMonthSelection = pos
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

        val goalRepo = GoalRepository(uid)
        goalRepoRef = goalRepo
        goalListenerRef = goalRepo.observe { config ->
            if (!isAdded) return@observe
            goals = config.items
            refreshGoalsProgress()
        }

        return view
    }

    private fun buildCategoryVariationRows(container: LinearLayout, deltas: List<CategoryDelta>) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        container.removeAllViews()

        if (deltas.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "Sem dados suficientes para comparar categorias."
                setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                textSize = 14f
            })
            return
        }

        deltas.forEachIndexed { idx, d ->
            val isLast = idx == deltas.size - 1
            val dotColor = Color.parseColor(chartPaletteHex[idx % chartPaletteHex.size])

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (!isLast) bottomMargin = (10 * dp).toInt() }
            }

            val dot = View(ctx).apply {
                val size = (9 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = (10 * dp).toInt() }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dotColor) }
            }

            val textBlock = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            nameRow.addView(TextView(ctx).apply {
                text = d.category
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_heading))
            })
            if (idx == 0 && d.pct > 0.5) {
                nameRow.addView(TextView(ctx).apply {
                    text = "  MAIOR ALTA"
                    textSize = 9.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.expense))
                })
            }

            val tvValues = TextView(ctx).apply {
                text = "R$ ${"%.2f".format(d.previous)} → R$ ${"%.2f".format(d.current)}"
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
            }

            textBlock.addView(nameRow)
            textBlock.addView(tvValues)

            val arrow = if (d.pct > 0.5) "▲" else if (d.pct < -0.5) "▼" else "—"
            val deltaColorRes = when {
                d.pct > 0.5  -> R.color.expense
                d.pct < -0.5 -> R.color.income
                else         -> R.color.text_hint
            }
            val tvDelta = TextView(ctx).apply {
                text = "$arrow ${"%.0f".format(kotlin.math.abs(d.pct))}%"
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, deltaColorRes))
            }

            row.addView(dot)
            row.addView(textBlock)
            row.addView(tvDelta)
            container.addView(row)
        }
    }

    private fun buildGoalsProgress(
        container: LinearLayout,
        goals: List<Goal>,
        categoryTotals: Map<String, Double>,
        overallTotal: Double
    ) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        container.removeAllViews()

        if (goals.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "Nenhuma meta configurada. Adicione em Perfil > Configurações > Metas."
                setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                textSize = 14f
            })
            return
        }

        val isDark = isDarkMode(ctx)
        val trackColor = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")

        goals.forEachIndexed { idx, goal ->
            val spent = if (goal.category.isBlank()) overallTotal else (categoryTotals[goal.category] ?: 0.0)
            val pct = if (goal.targetAmount > 0) (spent / goal.targetAmount * 100.0) else 0.0
            val label = goal.category.ifBlank { "Geral (todas as despesas)" }
            val isLast = idx == goals.size - 1

            val barColor = when {
                pct >= 100.0 -> ContextCompat.getColor(ctx, R.color.expense)
                pct >= 80.0  -> Color.parseColor("#D97706")
                else         -> ContextCompat.getColor(ctx, R.color.income)
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (!isLast) bottomMargin = (16 * dp).toInt() }
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (6 * dp).toInt() }
            }
            topRow.addView(TextView(ctx).apply {
                text = label
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_heading))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            topRow.addView(TextView(ctx).apply {
                text = "${"%.0f".format(pct)}%"
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(barColor)
            })

            val tvValues = TextView(ctx).apply {
                text = "R$ ${"%.2f".format(spent)} de R$ ${"%.2f".format(goal.targetAmount)}"
                textSize = 11f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (6 * dp).toInt() }
            }

            val track = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (7 * dp).toInt()
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 3.5f * dp
                    setColor(trackColor)
                }
            }
            val fillPct = pct.toFloat().coerceIn(0f, 100f)
            val fill = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fillPct.coerceAtLeast(1f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 3.5f * dp
                    setColor(barColor)
                }
            }
            val rest = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100f - fillPct).coerceAtLeast(0f))
            }
            track.addView(fill)
            track.addView(rest)

            row.addView(topRow)
            row.addView(tvValues)
            row.addView(track)
            container.addView(row)
        }
    }

    private fun setupWeekdayChart(barChart: BarChart, expenses: List<Transaction>) {
        val totals = DoubleArray(7) // 0=Seg .. 6=Dom
        for (t in expenses) {
            val parts = t.date.split("-")
            if (parts.size != 3) continue
            val y = parts[0].toIntOrNull() ?: continue
            val m = parts[1].toIntOrNull() ?: continue
            val d = parts[2].toIntOrNull() ?: continue
            val c = Calendar.getInstance()
            c.set(y, m - 1, d, 0, 0, 0)
            val dow = c.get(Calendar.DAY_OF_WEEK) // 1=Dom .. 7=Sáb
            val idx = (dow + 5) % 7
            totals[idx] += t.amount
        }

        val labels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
        val maxIdx = totals.indices.maxByOrNull { totals[it] } ?: -1

        val isDark = isDarkMode(requireContext())
        val neutralColor = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
        val peakColor = Color.parseColor("#2563EB")
        val axisTextColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#6B7280")

        val entries = totals.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
        val dataSet = BarDataSet(entries, "").apply {
            colors = totals.indices.map { if (it == maxIdx && totals[it] > 0.0) peakColor else neutralColor }.toMutableList()
            setDrawValues(false)
        }

        barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textSize = 11f
                textColor = axisTextColor
            }
            axisLeft.isEnabled = false
            axisRight.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            animateY(600)
            invalidate()
        }
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
