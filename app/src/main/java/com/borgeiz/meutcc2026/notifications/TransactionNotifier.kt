package com.borgeiz.meutcc2026.notifications

import android.content.Context
import com.borgeiz.meutcc2026.data.TransactionsRepository
import com.borgeiz.meutcc2026.data.expenseTotal
import com.borgeiz.meutcc2026.data.incomeTotal
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.util.formatMoneyPtBr
import com.google.firebase.database.ValueEventListener

class TransactionNotifier(context: Context, private val uid: String) {

    private val appContext = context.applicationContext
    private val repo = TransactionsRepository(uid)
    private var listener: ValueEventListener? = null

    private var known: Map<String, Transaction>? = null

    fun start() {
        if (listener != null) return
        listener = repo.observe { list -> handle(list) }
    }

    fun stop() {
        listener?.let { repo.removeObserver(it) }
        listener = null
    }

    private fun handle(list: List<Transaction>) {
        val current = list.associateBy { it.id }
        val previous = known
        known = current

        if (previous != null) {
            val added = current.values.filter { it.id !in previous }
            val removed = previous.values.filter { it.id !in current }
            val changed = current.values.filter { t -> previous[t.id]?.let { it != t } == true }

            if (added.size > 3) {
                NotificationHelper.notifyTransaction(
                    appContext, "added_batch".hashCode(),
                    "${added.size} lançamentos registrados",
                    "Receitas: ${formatMoneyPtBr(added.incomeTotal())}  ·  Despesas: ${formatMoneyPtBr(added.expenseTotal())}"
                )
            } else {
                added.forEach { t ->
                    val kind = if (t.type == "receita") "Receita registrada" else "Despesa registrada"
                    NotificationHelper.notifyTransaction(
                        appContext, t.id.hashCode(), kind, describe(t)
                    )
                }
            }
            if (changed.size > 3) {
                NotificationHelper.notifyTransaction(
                    appContext, "changed_batch".hashCode(),
                    "${changed.size} lançamentos atualizados", ""
                )
            } else {
                changed.forEach { t ->
                    NotificationHelper.notifyTransaction(
                        appContext, t.id.hashCode(), "Lançamento atualizado", describe(t)
                    )
                }
            }
            if (removed.size > 3) {
                NotificationHelper.notifyTransaction(
                    appContext, "removed_batch".hashCode(),
                    "${removed.size} lançamentos excluídos", ""
                )
            } else {
                removed.forEach { t ->
                    NotificationHelper.notifyTransaction(
                        appContext, t.id.hashCode(), "Lançamento excluído", describe(t)
                    )
                }
            }
        }

        checkBalance(list.incomeTotal() - list.expenseTotal())
    }

    private fun describe(t: Transaction): String {
        val sign = if (t.type == "receita") "+" else "-"
        val title = t.title.ifBlank { t.category.ifBlank { "Sem título" } }
        return "$title  $sign${formatMoneyPtBr(t.amount)}"
    }

    private fun checkBalance(balance: Double) {
        val prefs = appContext.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val key = "balance_state_$uid"
        val newState = when {
            balance < 0 -> STATE_NEGATIVE
            balance < LOW_BALANCE_THRESHOLD -> STATE_LOW
            else -> STATE_OK
        }
        val oldState = prefs.getInt(key, STATE_OK)
        if (newState > oldState) {
            if (newState == STATE_NEGATIVE) {
                NotificationHelper.notifyBalance(
                    appContext, "Saldo negativo",
                    "Seu saldo está em ${formatMoneyPtBr(balance)}. Suas despesas passaram das receitas."
                )
            } else {
                NotificationHelper.notifyBalance(
                    appContext, "Saldo perto de zero",
                    "Seu saldo está em ${formatMoneyPtBr(balance)}. Cuidado com os próximos gastos."
                )
            }
        }
        if (newState != oldState) prefs.edit().putInt(key, newState).apply()
    }

    companion object {
        const val LOW_BALANCE_THRESHOLD = 100.0

        private const val STATE_OK = 0
        private const val STATE_LOW = 1
        private const val STATE_NEGATIVE = 2
    }
}
