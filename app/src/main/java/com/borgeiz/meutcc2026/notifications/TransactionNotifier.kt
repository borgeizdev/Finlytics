package com.borgeiz.meutcc2026.notifications

import android.content.Context
import com.borgeiz.meutcc2026.data.TransactionsRepository
import com.borgeiz.meutcc2026.data.expenseTotal
import com.borgeiz.meutcc2026.data.incomeTotal
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.util.formatMoneyPtBr
import com.google.firebase.database.ValueEventListener

/**
 * Observa users/{uid}/transactions enquanto o app está aberto (MainActivity) e
 * dispara notificações comparando cada nova leitura com a anterior:
 * - lançamento novo  -> "Receita registrada" / "Despesa registrada"
 *   (inclui os gerados automaticamente pelas recorrências)
 * - lançamento alterado -> "Lançamento atualizado"
 * - lançamento removido -> "Lançamento excluído"
 * - saldo (receitas - despesas, o mesmo do Dashboard) que cai abaixo de
 *   [LOW_BALANCE_THRESHOLD] ou fica negativo -> alerta de saldo.
 *
 * A primeira leitura só serve de base (não notifica o histórico inteiro).
 */
class TransactionNotifier(context: Context, private val uid: String) {

    private val appContext = context.applicationContext
    private val repo = TransactionsRepository(uid)
    private var listener: ValueEventListener? = null

    // null = ainda não recebeu a primeira leitura.
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

            // Várias de uma vez (ex: recorrências do mês lançadas juntas) viram um
            // aviso só, em vez de encher a barra de notificações.
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
            // Mesma ideia pra alterações e exclusões em massa (ex: "Simular ano
            // completo" troca todos os lançamentos de uma vez).
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

    // Estado do saldo salvo por usuário: só avisa quando PIORA (OK -> baixo,
    // baixo -> negativo), não a cada abertura do app nem a cada lançamento
    // enquanto o saldo continua baixo. Volta a OK quando o saldo se recupera.
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
        /** Abaixo deste saldo (em R$) o app avisa que está perto de zero. */
        const val LOW_BALANCE_THRESHOLD = 100.0

        private const val STATE_OK = 0
        private const val STATE_LOW = 1
        private const val STATE_NEGATIVE = 2
    }
}
