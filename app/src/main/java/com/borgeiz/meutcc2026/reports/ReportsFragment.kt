package com.borgeiz.meutcc2026.reports

import com.borgeiz.meutcc2026.R
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
import com.borgeiz.meutcc2026.util.formatIntegerPtBr
import com.borgeiz.meutcc2026.util.formatMoneyPtBr
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
        // entre abas durante a sessão, mas volta pro padrão "Todos" quando
        // o processo do app morre e é reaberto.
        private var lastCatMonthSelection = 0
        // Detalhamento de "Gastos por categoria" aberto ou recolhido (seta).
        private var catBreakdownExpanded = true
    }

    private val monthLabels = listOf(
        "Todos", "Jan", "Fev", "Mar", "Abr",
        "Mai", "Jun", "Jul", "Ago",
        "Set", "Out", "Nov", "Dez"
    )

    private val monthLabelsFull = listOf(
        "Todos",
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
        val llCatToggle     = view.findViewById<LinearLayout>(R.id.llCatToggle)
        val ivCatToggle     = view.findViewById<android.widget.ImageView>(R.id.ivCatToggle)
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

        // getView é o item exibido no próprio spinner fechado: centraliza o texto
        // ("Todos", "Março"...) dentro da caixa.
        spCatMonth.adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            monthLabelsFull
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setPadding(0, 0, 0, 0)
                }
        }
        spCatMonth.setSelection(lastCatMonthSelection)

        pieChart.setNoDataText("")
        barChart.setNoDataText("")

        // Seta do detalhamento por categoria: para baixo = aberto, gira -90° = recolhido.
        fun paintCatToggle(animate: Boolean) {
            llCatBreakdown.visibility = if (catBreakdownExpanded) View.VISIBLE else View.GONE
            val rotation = if (catBreakdownExpanded) 0f else -90f
            if (animate) ivCatToggle.animate().rotation(rotation).setDuration(200).start()
            else ivCatToggle.rotation = rotation
        }
        paintCatToggle(animate = false)
        llCatToggle.setOnClickListener {
            catBreakdownExpanded = !catBreakdownExpanded
            paintCatToggle(animate = true)
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return view
        val db   = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        val txRepo = TransactionsRepository(uid)
        txRepoRef = txRepo

        var allTransactions = listOf<Transaction>()
        var adjustment = 0.0
        var goals = listOf<Goal>()

        // Gastos por categoria: filtra as despesas pelo mês escolhido no spinner
        // (monthFilter == 0 significa "todos os meses"), soma manualmente por
        // categoria num Map, e usa esse Map pra alimentar tanto o gráfico de pizza
        // quanto a lista com barrinha de progresso logo abaixo dele (ambos vêm de
        // ChartHelpers.kt, reaproveitados também pela análise por forma de pagamento).
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

            // Sem despesa no período não há o que recolher: esconde a seta e deixa
            // a mensagem de "nenhuma despesa" sempre visível.
            llCatToggle.visibility = if (categoryTotals.isEmpty()) View.GONE else View.VISIBLE
            if (categoryTotals.isEmpty()) llCatBreakdown.visibility = View.VISIBLE
            else paintCatToggle(animate = false)

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

        // "Insights": bloco que calcula tudo que aparece na parte de análise dos
        // Relatórios num só lugar — sempre comparando o mês atual (curXxx) com o
        // mês anterior (prevXxx), calculados a partir de allTransactions em memória
        // (nada disso é uma nova consulta ao Firebase). Dentro dela:
        //   • média diária e maior receita/despesa do mês
        //   • "hero": variação percentual do total de despesas vs. mês passado
        //   • variação por categoria (delega para buildCategoryVariationRows)
        //   • gasto por dia da semana (delega para setupWeekdayChart)
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
            tvAvgPerDay.text = formatMoneyPtBr(if (today > 0) curTotal / today else 0.0)

            val topExpense = curExpenses.maxByOrNull { it.amount }
            tvTopExpenseValue.text = topExpense?.let { formatMoneyPtBr(it.amount) } ?: "—"
            tvTopExpenseTitle.text = topExpense?.title?.ifBlank { "Sem título" } ?: "Nenhuma despesa"

            val topIncome = curIncomes.maxByOrNull { it.amount }
            tvTopIncomeValue.text = topIncome?.let { formatMoneyPtBr(it.amount) } ?: "—"
            tvTopIncomeTitle.text = topIncome?.title?.ifBlank { "Sem título" } ?: "Nenhuma receita"

            // Hero: variação total do mês
            val prevMonthName = monthLabelsFull.getOrElse(prevMonth) { "" }
            if (prevTotal <= 0.0) {
                tvHeroBadge.visibility = View.GONE
                tvHeroValue.text = formatMoneyPtBr(curTotal)
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
                tvHeroValue.text = formatMoneyPtBr(curTotal)

                val diff = kotlin.math.abs(curTotal - prevTotal)
                val comparativo = if (isIncrease) "a mais" else if (isDecrease) "a menos" else "praticamente igual a"
                tvHeroDetail.text =
                    "Você gastou ${formatMoneyPtBr(diff)} $comparativo que em $prevMonthName (${formatMoneyPtBr(prevTotal)})"
            }

            // Variação por categoria
            val curCat  = curExpenses.groupBy { it.category.ifBlank { "Outros" } }.mapValues { (_, v) -> v.sumOf { it.amount } }
            val prevCat = prevExpenses.groupBy { it.category.ifBlank { "Outros" } }.mapValues { (_, v) -> v.sumOf { it.amount } }
            // Junta as categorias dos dois meses sem repetir (é um Set) — importante
            // porque uma categoria pode existir num mês e não no outro.
            val allCats = curCat.keys + prevCat.keys
            val deltas = allCats.map { cat ->
                val cur  = curCat[cat] ?: 0.0
                val prev = prevCat[cat] ?: 0.0
                // 3 casos: variação normal quando existia no mês passado; 100% fixo
                // quando é uma categoria totalmente nova (não dá pra calcular
                // "variação" de um valor que não existia); 0% quando não teve gasto
                // em nenhum dos dois meses.
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

        // Metas do mês: recalcula o gasto (por categoria e geral) e o lucro do mês
        // atual a partir de allTransactions, e repassa pra buildGoalsProgress, que
        // desenha uma barra de progresso por meta configurada em Perfil > Metas.
        // Roda tanto quando as transações mudam quanto quando a config de metas
        // muda (dois listeners diferentes, mesma função — ver onCreateView abaixo).
        fun refreshGoalsProgress() {
            if (!isAdded) return
            val cal = Calendar.getInstance()
            val curYear  = cal.get(Calendar.YEAR)
            val curMonth = cal.get(Calendar.MONTH) + 1
            val categoryTotals = allTransactions.categoryTotalsForMonth("despesa", curYear, curMonth)
            val overallExpense = categoryTotals.values.sum()
            val overallIncome = allTransactions.categoryTotalsForMonth("receita", curYear, curMonth).values.sum()
            val monthlyProfit = overallIncome - overallExpense
            buildGoalsProgress(llGoalsProgress, goals, categoryTotals, overallExpense, monthlyProfit)
        }

        fun renderSummary() {
            val totalIncome  = allTransactions.incomeTotal()
            val totalExpense = allTransactions.expenseTotal()
            val balance = totalIncome - totalExpense + adjustment
            tvIncome.text  = formatMoneyPtBr(totalIncome)
            tvExpense.text = formatMoneyPtBr(totalExpense)
            tvBalance.text = formatMoneyPtBr(balance)

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

    // Desenha a lista "variação por categoria" (bolinha colorida + nome + valores +
    // seta de %). Os deltas já vêm prontos de refreshInsights: para cada categoria,
    // pct é (atual - anterior) / anterior * 100 — ou 100% se a categoria não existia
    // no mês passado — ordenados do maior aumento pro menor, limitado às 6 primeiras.
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
                text = "${formatMoneyPtBr(d.previous)} → ${formatMoneyPtBr(d.current)}"
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

    // Desenha uma barra de progresso por meta (Goal). "current" é o gasto do mês
    // (por categoria, ou o total geral se a meta não tem categoria) para metas do
    // tipo "gasto", ou o lucro do mês (receita - despesa) para metas do tipo "lucro".
    // pct = current / targetAmount * 100. As cores da barra são intencionalmente
    // opostas entre os dois tipos: numa meta de gasto, 100%+ é vermelho (estourou);
    // numa meta de lucro, 100%+ é verde (bateu a meta).
    private fun buildGoalsProgress(
        container: LinearLayout,
        goals: List<Goal>,
        categoryTotals: Map<String, Double>,
        overallExpense: Double,
        monthlyProfit: Double
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

        // forEachIndexed percorre a lista de metas dando também o índice (idx) de
        // cada uma, junto com a meta em si (goal) — usado só pra saber se é a
        // última (isLast), pra não desenhar margem extra depois dela.
        goals.forEachIndexed { idx, goal ->
            // isLucro é um Boolean: true se essa meta específica é do tipo
            // "lucro", false se é do tipo "gasto".
            val isLucro = goal.type == "lucro"

            // "spent" = quanto já foi gasto no mês relevante pra essa meta. Se a
            // meta não tem categoria (goal.category vazio), usa o total geral de
            // despesas (overallExpense). Senão, busca no Map categoryTotals pela
            // categoria específica — "categoryTotals[goal.category]" pode devolver
            // null se essa categoria não teve gasto nenhum este mês, por isso o
            // "?: 0.0" (usa zero nesse caso).
            val spent = if (goal.category.isBlank()) overallExpense else (categoryTotals[goal.category] ?: 0.0)

            // "current" é o valor de fato usado no cálculo de progresso: pra meta
            // de lucro, é o lucro do mês (monthlyProfit, calculado em
            // refreshGoalsProgress); pra meta de gasto, é "spent" de cima.
            val current = if (isLucro) monthlyProfit else spent

            // A fórmula central: percentual = valor atual dividido pela meta,
            // vezes 100. "if (goal.targetAmount > 0)" evita dividir por zero
            // caso a meta tenha sido salva com valor zero por algum motivo.
            val pct = if (goal.targetAmount > 0) (current / goal.targetAmount * 100.0) else 0.0

            val label = if (isLucro) "Lucro mensal" else goal.category.ifBlank { "Geral (todas as despesas)" }
            val caption = if (isLucro) {
                "Meta de lucro: receita menos despesa do mês"
            } else {
                "Limite de gasto: " + if (goal.category.isBlank()) "soma de todas as despesas do mês" else "categoria \"${goal.category}\""
            }
            val isLast = idx == goals.size - 1

            // A cor da barra é o ponto mais importante de explicar: é a MESMA
            // barra visual, mas com a leitura do percentual INVERTIDA conforme o
            // tipo da meta.
            val barColor = if (isLucro) {
                // Meta de LUCRO: 100%+ é bom (bateu a meta) -> verde (income).
                // Entre 50% e 100% -> amarelo/laranja (ainda no caminho).
                // Abaixo de 50% -> vermelho (longe do objetivo).
                when {
                    pct >= 100.0 -> ContextCompat.getColor(ctx, R.color.income)
                    pct >= 50.0  -> Color.parseColor("#D97706")
                    else         -> ContextCompat.getColor(ctx, R.color.expense)
                }
            } else {
                // Meta de GASTO: 100%+ é ruim (estourou o limite) -> vermelho
                // (expense). Entre 80% e 100% -> amarelo/laranja (atenção,
                // chegando perto do limite). Abaixo de 80% -> verde (tranquilo).
                when {
                    pct >= 100.0 -> ContextCompat.getColor(ctx, R.color.expense)
                    pct >= 80.0  -> Color.parseColor("#D97706")
                    else         -> ContextCompat.getColor(ctx, R.color.income)
                }
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
                ).apply { bottomMargin = (2 * dp).toInt() }
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

            val tvCaption = TextView(ctx).apply {
                text = caption
                textSize = 10.5f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (4 * dp).toInt() }
            }

            val tvValues = TextView(ctx).apply {
                text = if (isLucro) {
                    "Lucro atual: ${formatMoneyPtBr(current)}  ·  meta: ${formatMoneyPtBr(goal.targetAmount)}"
                } else {
                    "${formatMoneyPtBr(current)} de ${formatMoneyPtBr(goal.targetAmount)}"
                }
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
            row.addView(tvCaption)
            row.addView(tvValues)
            row.addView(track)
            container.addView(row)
        }
    }

    // Gastos por dia da semana: soma o valor de cada despesa do mês atual no
    // "balde" (0-6) do dia da semana em que ela aconteceu. A data é guardada como
    // texto "yyyy-MM-dd", então é reconstruída num Calendar só pra descobrir o dia
    // da semana (DAY_OF_WEEK do Java começa no domingo=1; "(dow + 5) % 7" reindexa
    // pra a semana começar na segunda, que é como o gráfico exibe as barras).
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
        // Todos os dias ficam coloridos (não só o de maior gasto) para dar pra
        // comparar as barras entre si; o dia de pico só se destaca com um tom
        // mais forte, sem apagar os demais em cinza.
        val regularColor = Color.parseColor("#93C5FD")
        val peakColor = Color.parseColor("#2563EB")
        val axisTextColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#6B7280")

        val entries = totals.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
        val dataSet = BarDataSet(entries, "").apply {
            colors = totals.indices.map { if (it == maxIdx && totals[it] > 0.0) peakColor else regularColor }.toMutableList()
            setDrawValues(false)
        }

        barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            legend.isEnabled = false
            // Sem zoom (pinça ou toque duplo); os 7 dias já cabem na tela.
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
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

    // Gráfico de colunas de receitas x despesas por mês: agrupa TODAS as transações
    // (não só o mês selecionado) por mês, somando receita e despesa separadamente.
    // Se só existir um dos dois tipos no período (hasIncome != hasExpense), desenha
    // uma única coluna por mês em vez de duas, pra não ter uma barra sempre zerada.
    private fun setupBarChart(barChart: BarChart, transactions: List<Transaction>) {
        // Chave = ano * 100 + mês (ex: 202603), para março de 2025 e março de 2026
        // não serem somados no mesmo mês.
        val monthlyIncome  = mutableMapOf<Int, Float>()
        val monthlyExpense = mutableMapOf<Int, Float>()

        for (t in transactions) {
            val parts = t.date.split("-")
            if (parts.size < 2) continue
            val year  = parts[0].toIntOrNull() ?: continue
            val month = parts[1].toIntOrNull() ?: continue
            val key = year * 100 + month
            if (t.type == "receita") monthlyIncome[key]  = (monthlyIncome[key]  ?: 0f) + t.amount.toFloat()
            if (t.type == "despesa") monthlyExpense[key] = (monthlyExpense[key] ?: 0f) + t.amount.toFloat()
        }

        val allMonths = (monthlyIncome.keys + monthlyExpense.keys).toSortedSet().toList()
        if (allMonths.isEmpty()) {
            barChart.visibility = View.GONE
            return
        }
        barChart.visibility = View.VISIBLE

        // Com mais de um ano no gráfico, o rótulo leva o ano junto (ex: "Mar/26").
        val multiYear = allMonths.map { it / 100 }.distinct().size > 1
        val labels = allMonths.map { key ->
            val name = monthLabels.getOrElse(key % 100) { "${key % 100}" }
            if (multiYear) "$name/${(key / 100) % 100}" else name
        }

        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val axisTextColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#6B7280")
        val axisGridColor = if (isDark) Color.parseColor("#2D3748") else Color.parseColor("#E5E7EB")

        // Rótulos bem curtos em cima das barras ("950", "1,2k", "3,4M"), sem "R$":
        // com receita e despesa lado a lado, rótulos longos se encostavam.
        fun hideZeroFormatter() = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float) = if (value == 0f) "" else formatBarLabel(value)
        }

        // Só receita ou só despesa têm dados no período todo: em vez de um grupo de
        // 2 colunas com uma delas sempre zerada (e descentralizada), desenha uma
        // única coluna por mês, centralizada sob o próprio mês.
        val hasIncome  = monthlyIncome.values.any { it > 0f }
        val hasExpense = monthlyExpense.values.any { it > 0f }
        val singleSeries = hasIncome != hasExpense

        // barSpace maior separa a barra de receita da de despesa, abrindo espaço
        // entre os rótulos de valor das duas.
        val groupSpace = 0.24f
        val barSpace   = 0.1f
        val barWidth   = if (singleSeries) 0.5f else 0.28f
        // Largura que cada mês ocupa no eixo X.
        val slotWidth  = if (singleSeries) 1f else barWidth * 2 + barSpace * 2 + groupSpace

        if (singleSeries) {
            val label = if (hasIncome) "Receitas" else "Despesas"
            val color = if (hasIncome) Color.parseColor("#16A34A") else Color.parseColor("#DC2626")
            val entries = allMonths.mapIndexed { idx, key ->
                BarEntry(idx.toFloat(), (if (hasIncome) monthlyIncome[key] else monthlyExpense[key]) ?: 0f)
            }
            val dataSet = BarDataSet(entries, label).apply {
                this.color = color
                valueTextSize = 9f
                valueTextColor = axisTextColor
                valueFormatter = hideZeroFormatter()
            }
            barChart.data = BarData(dataSet).apply { this.barWidth = barWidth }
        } else {
            val incomeEntries  = mutableListOf<BarEntry>()
            val expenseEntries = mutableListOf<BarEntry>()
            allMonths.forEachIndexed { idx, key ->
                incomeEntries.add(BarEntry(idx.toFloat(), monthlyIncome[key]  ?: 0f))
                expenseEntries.add(BarEntry(idx.toFloat(), monthlyExpense[key] ?: 0f))
            }

            val incomeSet = BarDataSet(incomeEntries, "Receitas").apply {
                color = Color.parseColor("#16A34A")
                valueTextSize = 9f
                valueTextColor = axisTextColor
                valueFormatter = hideZeroFormatter()
            }
            val expenseSet = BarDataSet(expenseEntries, "Despesas").apply {
                color = Color.parseColor("#DC2626")
                valueTextSize = 9f
                valueTextColor = axisTextColor
                valueFormatter = hideZeroFormatter()
            }

            val barData = BarData(incomeSet, expenseSet).apply { this.barWidth = barWidth }
            barChart.data = barData
            barData.groupBars(0f, groupSpace, barSpace)
        }

        // Poucos meses na tela de uma vez (4 com receita+despesa, 6 com uma série
        // só); o resto fica acessível arrastando para os lados. Antes, um ano
        // inteiro (24 barras) dividia 280dp e os valores ficavam encavalados.
        val maxVisibleMonths = if (singleSeries) 6 else 4

        barChart.apply {
            fitScreen()
            description.isEnabled = false
            setFitBars(true)
            setScaleEnabled(false)
            isDragEnabled = true
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            setDrawValueAboveBar(true)
            extraTopOffset = 8f
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textSize = 11f
                textColor = axisTextColor
                labelCount = minOf(allMonths.size, maxVisibleMonths)
                if (singleSeries) {
                    setCenterAxisLabels(false)
                    axisMinimum = -0.5f
                    axisMaximum = allMonths.size - 0.5f
                } else {
                    setCenterAxisLabels(true)
                    if (allMonths.size == 1) {
                        // Um único mês: soma um espaço vazio simétrico dos dois lados do
                        // grupo pra ele ficar centralizado em vez de colado na borda.
                        axisMinimum = -slotWidth
                        axisMaximum = slotWidth * 2
                    } else {
                        axisMinimum = 0f
                        axisMaximum = slotWidth * allMonths.size
                    }
                }
            }
            axisLeft.apply {
                axisMinimum = 0f
                // Espaço acima da barra mais alta para o rótulo de valor não ser cortado.
                spaceTop = 18f
                textColor = axisTextColor
                gridColor = axisGridColor
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float) = formatCompactMoney(value)
                }
            }
            axisRight.isEnabled = false
            legend.apply {
                textColor = axisTextColor
                xEntrySpace = 24f
                formToTextSpace = 8f
                yOffset = 8f
            }
            // Espaço extra entre os nomes dos meses e a legenda de cores.
            extraBottomOffset = 16f
            // Toque não destaca barra (não há mais resumo para mostrar).
            setHighlightPerTapEnabled(false)
            setBackgroundColor(Color.TRANSPARENT)
            notifyDataSetChanged()
            if (allMonths.size > maxVisibleMonths) {
                setVisibleXRangeMaximum(slotWidth * maxVisibleMonths)
                // Abre mostrando os meses mais recentes (o fim do eixo).
                moveViewToX(xAxis.axisMaximum)
            }
            animateY(600)
            invalidate()
        }
    }

    /** Rótulo curto de barra: "950", "1,2k", "3,4M". */
    private fun formatBarLabel(value: Float): String {
        val v = value.toDouble()
        return when {
            v >= 1_000_000 -> formatOneDecimal(v / 1_000_000) + "M"
            v >= 1_000     -> formatOneDecimal(v / 1_000) + "k"
            else           -> formatIntegerPtBr(v)
        }
    }

    /** Valor abreviado para rótulos de gráfico: "R$950", "R$1,2 mil", "R$3,4 mi". */
    private fun formatCompactMoney(value: Float): String {
        val v = value.toDouble()
        return when {
            v >= 1_000_000 -> "R$" + formatOneDecimal(v / 1_000_000) + " mi"
            v >= 1_000     -> "R$" + formatOneDecimal(v / 1_000) + " mil"
            else           -> "R$" + formatIntegerPtBr(v)
        }
    }

    /** Uma casa decimal com vírgula, sem ",0" no fim (ex: 1.25 -> "1,3", 12.0 -> "12"). */
    private fun formatOneDecimal(v: Double): String {
        val rounded = Math.round(v * 10) / 10.0
        return if (rounded % 1.0 == 0.0) formatIntegerPtBr(rounded)
        else String.format(java.util.Locale("pt", "BR"), "%.1f", rounded)
    }
}
