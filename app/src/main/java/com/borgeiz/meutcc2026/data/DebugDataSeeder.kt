package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.Goal
import com.borgeiz.meutcc2026.model.GoalConfig
import com.borgeiz.meutcc2026.model.PaymentMethods
import com.borgeiz.meutcc2026.model.RecurringConfig
import com.borgeiz.meutcc2026.model.RecurringItem
import com.borgeiz.meutcc2026.model.Transaction
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

/**
 * Ferramenta de debug (não faz parte do fluxo normal do app): apaga os dados
 * financeiros da conta e gera alguns meses de lançamentos fictícios —
 * várias categorias, formas de pagamento e recorrências — só para
 * demonstração. Disparada pelo botão "Gerar dados de teste" em
 * Perfil > Configurações.
 */
object DebugDataSeeder {

    private val wipedChildren = listOf(
        "transactions", "categories", "recurringConfig",
        "recurringConfigMigrated", "salaryConfig", "goals", "balanceAdjustment"
    )

    fun resetAndSeed(uid: String, monthsBack: Int = 5, onDone: (Boolean) -> Unit) {
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)

        var pending = wipedChildren.size
        var failed = false
        wipedChildren.forEach { child ->
            userRef.child(child).removeValue().addOnCompleteListener { task ->
                if (!task.isSuccessful) failed = true
                pending--
                if (pending == 0) {
                    if (failed) onDone(false) else seed(uid, monthsBack, onDone)
                }
            }
        }
    }

    private fun seed(uid: String, monthsBack: Int, onDone: (Boolean) -> Unit) {
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        val txRef = userRef.child("transactions")
        val rnd = Random(System.currentTimeMillis())

        val recurringSalaryId = UUID.randomUUID().toString()
        val recurringRentId = UUID.randomUUID().toString()
        val recurringSubId = UUID.randomUUID().toString()

        val variableExpenses = listOf(
            Triple("Supermercado", "Alimentacao", 60..260),
            Triple("Restaurante", "Alimentacao", 25..90),
            Triple("iFood", "Alimentacao", 20..70),
            Triple("Uber", "Transporte", 12..45),
            Triple("Combustível", "Transporte", 80..220),
            Triple("Ônibus/Metrô", "Transporte", 8..40),
            Triple("Conta de luz", "Contas", 90..220),
            Triple("Conta de água", "Contas", 40..100),
            Triple("Internet", "Contas", 90..120),
            Triple("Farmácia", "Saude", 20..150),
            Triple("Plano de saúde", "Saude", 150..350),
            Triple("Cinema", "Lazer", 30..80),
            Triple("Bar com amigos", "Lazer", 40..150),
            Triple("Curso online", "Educacao", 50..300),
            Triple("Livros", "Educacao", 30..120),
            Triple("Roupas", "Compras", 60..300),
            Triple("Eletrônicos", "Compras", 100..600)
        )
        val variableIncomes = listOf(
            Triple("Freelance", "Freelance", 150..800),
            Triple("Venda usado", "Venda", 50..400),
            Triple("Presente", "Presente", 50..300),
            Triple("Reembolso", "Reembolso", 30..200)
        )
        val paymentOptions = PaymentMethods.ALL

        val transactions = mutableListOf<Transaction>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -(monthsBack - 1))
        cal.set(Calendar.DAY_OF_MONTH, 1)

        repeat(monthsBack) {
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            fun date(day: Int) = "%04d-%02d-%02d".format(year, month, day.coerceIn(1, maxDay))

            // Receita fixa (recorrência) + eventuais receitas extras do mês.
            transactions += Transaction(
                type = "receita", title = "Salário", amount = 3200.0 + rnd.nextInt(-100, 300),
                category = "Salario", date = date(5), description = "Lançamento automático",
                paymentMethod = "", recurringId = recurringSalaryId
            )
            repeat(rnd.nextInt(0, 3)) {
                val (title, category, range) = variableIncomes.random(rnd)
                transactions += Transaction(
                    type = "receita", title = title, amount = rnd.nextInt(range.first, range.last + 1).toDouble(),
                    category = category, date = date(rnd.nextInt(6, maxDay)), description = "",
                    paymentMethod = ""
                )
            }

            // Despesas fixas (recorrências).
            transactions += Transaction(
                type = "despesa", title = "Aluguel", amount = 1100.0,
                category = "Moradia", date = date(10), description = "Lançamento automático",
                paymentMethod = "Transferência", recurringId = recurringRentId
            )
            transactions += Transaction(
                type = "despesa", title = "Assinatura streaming", amount = 39.90,
                category = "Assinaturas", date = date(15), description = "Lançamento automático",
                paymentMethod = "Cartão de Crédito", recurringId = recurringSubId
            )

            // Despesas variadas, espalhadas pelo mês em categorias e formas de pagamento diferentes.
            repeat(rnd.nextInt(9, 15)) {
                val (title, category, range) = variableExpenses.random(rnd)
                transactions += Transaction(
                    type = "despesa", title = title, amount = rnd.nextInt(range.first, range.last + 1).toDouble(),
                    category = category, date = date(rnd.nextInt(1, maxDay + 1)), description = "",
                    paymentMethod = paymentOptions.random(rnd)
                )
            }

            cal.add(Calendar.MONTH, 1)
        }

        val updates = mutableMapOf<String, Any?>()
        transactions.forEach { t ->
            val key = txRef.push().key ?: return@forEach
            updates["transactions/$key"] = t
        }
        updates["recurringConfig"] = RecurringConfig(
            items = listOf(
                RecurringItem(id = recurringSalaryId, title = "Salário", type = "receita", amount = 3200.0, category = "Salario", dayOfMonth = 5),
                RecurringItem(id = recurringRentId, title = "Aluguel", type = "despesa", amount = 1100.0, category = "Moradia", dayOfMonth = 10),
                RecurringItem(id = recurringSubId, title = "Assinatura streaming", type = "despesa", amount = 39.90, category = "Assinaturas", dayOfMonth = 15)
            )
        )
        updates["recurringConfigMigrated"] = true
        updates["goals"] = GoalConfig(
            items = listOf(
                Goal(id = UUID.randomUUID().toString(), category = "Alimentacao", targetAmount = 500.0, period = "mensal", type = "gasto"),
                Goal(id = UUID.randomUUID().toString(), category = "", targetAmount = 1500.0, period = "mensal", type = "lucro")
            )
        )

        userRef.updateChildren(updates).addOnCompleteListener { task ->
            onDone(task.isSuccessful)
        }
    }
}
