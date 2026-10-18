package com.borgeiz.meutcc2026.transactions

import com.borgeiz.meutcc2026.R
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.borgeiz.meutcc2026.data.CategoryRepository
import com.borgeiz.meutcc2026.data.TransactionsRepository
import com.borgeiz.meutcc2026.model.PaymentMethods
import com.borgeiz.meutcc2026.model.Transaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener

class TransactionsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var btnFilter: ImageButton
    private val allTransactions = mutableListOf<Transaction>()

    private val typeFilterOptions = listOf("Todos os tipos", "Receitas", "Despesas")

    companion object {
        // Em memória (não persiste no disco): mantém a seleção do usuário ao
        // navegar entre abas durante a sessão, mas volta pro padrão "Todos"
        // quando o processo do app morre e é reaberto.
        private var filterType = "Todos os tipos"
        private var filterCategory = "Todas as categorias"
        private var filterPayment = "Todas as formas de pagamento"
        private var filterMonthPos = 0
    }

    private var txRepo: TransactionsRepository? = null
    private var txListener: ValueEventListener? = null

    private val primaryBlue get() = 0xFF2563EB.toInt()

    private val monthLabels = listOf(
        "Todos os meses",
        "Janeiro", "Fevereiro", "Março", "Abril",
        "Maio", "Junho", "Julho", "Agosto",
        "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    // Formas de pagamento personalizadas em Perfil > Categorias > Pagamento
    // (começa com as padrão até a lista do usuário chegar do Firebase).
    private var userPaymentMethods: List<String> = PaymentMethods.ALL

    // Lista do usuário + formas já usadas em lançamentos antigos (que podem ter
    // sido removidas da lista depois), para continuarem filtráveis.
    private fun paymentFilterOptions(): List<String> {
        val used = allTransactions.map { it.paymentMethod }.filter { it.isNotBlank() }
        val methods = (userPaymentMethods + used).distinctBy { it.lowercase() }
        return listOf("Todas as formas de pagamento") + methods + listOf(PaymentMethods.NAO_INFORMADO)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_transactions, container, false)

        recycler  = view.findViewById(R.id.recyclerTransactions)
        btnFilter = view.findViewById(R.id.btnFilter)

        recycler.layoutManager = LinearLayoutManager(requireContext())

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

    // Busca todas as transações do usuário (tempo real, ver TransactionsRepository.observe)
    // e guarda em allTransactions. A lista completa nunca é exibida direto — sempre
    // passa por applyFilter() antes de ir pro adapter, então qualquer mudança nos
    // filtros ou uma nova transação chegando refaz esse recorte automaticamente.
    private fun loadTransactions() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CategoryRepository(uid).loadConfig { config ->
            if (isAdded && config.payment.isNotEmpty()) userPaymentMethods = config.payment
        }
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

    // ==========================================================================
    // applyFilter() — os 4 filtros combinados, linha a linha
    // ==========================================================================
    // Não é uma nova busca no Firebase — é um FILTRO EM MEMÓRIA sobre
    // allTransactions, que já está toda carregada (ver loadTransactions()). Os 4
    // valores atuais (filterType, filterCategory, filterPayment, filterMonthPos)
    // ficam no companion object da classe (ver mais acima) pra sobreviver a trocar
    // de aba.
    private fun applyFilter() {
        // Copia o valor atual do filtro de mês pra uma val local só por clareza
        // de leitura (0 = "Todos os meses", 1 a 12 = o mês escolhido).
        val monthPos = filterMonthPos

        // .filter { t -> ... } percorre allTransactions e mantém só os itens em
        // que o bloco dentro das chaves devolver "true". "t" é o nome que demos
        // pro parâmetro da lambda (uma transação de cada vez).
        val filtered = allTransactions.filter { t ->

            // typeOk: "when" testa o valor de filterType contra cada opção, de
            // cima pra baixo, e usa a primeira que bater; "else -> true" cobre
            // o caso "Todos os tipos" (não filtra nada).
            val typeOk = when (filterType) {
                "Receitas" -> t.type == "receita"
                "Despesas" -> t.type == "despesa"
                else -> true
            }

            // dateOk: se o filtro de mês está em "Todos" (posição 0), passa
            // direto (true). Senão, quebra a data ("yyyy-MM-dd") em pedaços
            // usando o hífen como separador — parts[1] é o mês — e compara com
            // o mês escolhido no filtro. .toIntOrNull() converte texto pra
            // número (ou null se não for válido, o que faria a comparação
            // falhar com segurança em vez de travar).
            val dateOk = if (monthPos == 0) {
                true
            } else {
                val parts = t.date.split("-")
                parts.size >= 2 && parts[1].toIntOrNull() == monthPos
            }

            // categoryOk: true se o filtro está em "Todas as categorias" OU se a
            // categoria da transação bate com a escolhida. t.category.ifBlank
            // {'{' } "Outros" {'}'} usa "Outros" quando a categoria está vazia — mesma
            // regra usada em Relatórios pra transações sem categoria definida.
            // .equals(..., ignoreCase = true) compara ignorando maiúscula/
            // minúscula.
            val categoryOk = filterCategory == "Todas as categorias" ||
                t.category.ifBlank { "Outros" }.equals(filterCategory, ignoreCase = true)

            // paymentOk: mesma lógica de categoryOk, mas pra forma de pagamento —
            // aqui o valor de reserva é PaymentMethods.NAO_INFORMADO em vez de
            // "Outros", cobrindo transações antigas sem esse campo preenchido.
            val paymentOk = filterPayment == "Todas as formas de pagamento" ||
                t.paymentMethod.ifBlank { PaymentMethods.NAO_INFORMADO }.equals(filterPayment, ignoreCase = true)

            // A transação só sobrevive ao filtro se passar em TODOS os 4 testes
            // ao mesmo tempo — "&&" é "E lógico": só é true se os dois lados
            // forem true.
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

        addLabel(root, "Mês")
        val spMonth = spinnerFor(monthLabels, monthLabels.getOrElse(filterMonthPos) { monthLabels[0] })
        root.addView(spMonth)

        addLabel(root, "Tipo")
        val spType = spinnerFor(typeFilterOptions, filterType)
        root.addView(spType)

        val categories = categoryOptions()
        addLabel(root, "Categoria")
        val spCategory = spinnerFor(categories, filterCategory)
        root.addView(spCategory)

        addLabel(root, "Forma de pagamento")
        val paymentFilterOptions = paymentFilterOptions()
        val spPayment = spinnerFor(paymentFilterOptions, filterPayment)
        root.addView(spPayment)

        AlertDialog.Builder(ctx)
            .setTitle("Filtros")
            .setView(root)
            .setPositiveButton("Aplicar") { _, _ ->
                filterMonthPos = spMonth.selectedItemPosition
                filterType = typeFilterOptions.getOrElse(spType.selectedItemPosition) { typeFilterOptions[0] }
                filterCategory = categories.getOrElse(spCategory.selectedItemPosition) { categories[0] }
                filterPayment = paymentFilterOptions.getOrElse(spPayment.selectedItemPosition) { paymentFilterOptions[0] }
                applyFilter()
            }
            .setNeutralButton("Limpar") { _, _ ->
                filterMonthPos = 0
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
