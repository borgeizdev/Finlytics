package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.RecurringConfig
import com.borgeiz.meutcc2026.model.RecurringItem
import com.borgeiz.meutcc2026.model.Transaction
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class DemoDataSeeder(uid: String) {

    private val userRef: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("users").child(uid)

    fun generateUntilToday(): Task<Void> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val today = "%04d-%02d-%02d".format(year, currentMonth, cal.get(Calendar.DAY_OF_MONTH))

        val transactions = HashMap<String, Any?>()
        for (month in 1..currentMonth) {
            buildMonth(year, month)
                .filter { it.date <= today }
                .forEachIndexed { idx, t ->
                    val key = "%s%04d_%02d_%03d".format(PREFIX, year, month, idx + 1)
                    transactions[key] = t
                }
        }

        val recurring = RECURRING.map { it.copy(startDate = "%04d-01-01".format(year)) }
        val updates = hashMapOf<String, Any?>(
            "transactions" to transactions,
            "recurringConfig" to RecurringConfig(items = recurring),
            "recurringConfigMigrated" to true
        )
        return userRef.updateChildren(updates)
    }

    private fun buildMonth(year: Int, month: Int): List<Transaction> {
        val rnd = SeededRandom(year * 100 + month)
        val list = mutableListOf<Transaction>()

        val lastDay = Calendar.getInstance().apply { set(year, month - 1, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)

        fun date(day: Int) = "%04d-%02d-%02d".format(year, month, minOf(day, lastDay))
        fun anyDay() = rnd.int(1, lastDay + 1)

        fun income(day: Int, title: String, category: String, amount: Double) =
            list.add(Transaction(type = "receita", title = title, amount = amount,
                category = category, date = date(day)))

        fun expense(day: Int, title: String, category: String, amount: Double, payment: String) =
            list.add(Transaction(type = "despesa", title = title, amount = amount,
                category = category, date = date(day), paymentMethod = payment))

        RECURRING.forEach { r ->
            list.add(Transaction(
                type = r.type, title = r.title, amount = r.amount, category = r.category,
                date = date(r.dayOfMonth), description = "Lançamento automático",
                paymentMethod = r.paymentMethod, recurringId = r.id
            ))
        }

        if (month == 11) income(28, "13º salário (1ª parcela)", "Salario", 1225.00)
        if (month == 12) income(18, "13º salário (2ª parcela)", "Salario", 1012.40)
        if (month in setOf(3, 6, 9, 11)) {
            val jobs = listOf("Arte para redes sociais", "Formatação de TCC", "Ajuste em site de cliente")
            income(rnd.int(12, 26), jobs[rnd.int(0, jobs.size)], "Freelance", rnd.money(220.0, 420.0))
        }
        if (month == 4) income(14, "Reembolso de compra online", "Reembolso", 89.90)
        if (month == 8) income(21, "Venda do celular antigo", "Venda", 350.00)
        if (month == 10) income(12, "Presente de aniversário", "Presente", 150.00)

        val summer = month in setOf(1, 2, 3, 12)
        expense(12, "Conta de luz", "Contas", if (summer) rnd.money(105.0, 140.0) else rnd.money(68.0, 92.0), "Pix")
        expense(16, "Conta de água", "Contas", rnd.money(40.0, 58.0), "Pix")

        for (week in 0 until 4) {
            val payment = if (rnd.chance(60)) "Cartão de Débito" else "Pix"
            expense(6 + week * 7, "Supermercado", "Alimentacao", rnd.money(95.0, 150.0), payment)
        }
        repeat(rnd.int(1, 4)) {
            expense(anyDay(), "iFood", "Alimentacao", rnd.money(28.0, 52.0), "Cartão de Crédito")
        }
        repeat(rnd.int(3, 6)) {
            expense(anyDay(), "Padaria", "Alimentacao", rnd.money(8.0, 22.0), "Dinheiro")
        }

        repeat(rnd.int(0, 3)) {
            expense(anyDay(), "Uber", "Transporte", rnd.money(14.0, 32.0), "Cartão de Crédito")
        }
        expense(anyDay(), "Farmácia", "Saude", rnd.money(18.0, 65.0), "Cartão de Débito")

        val fun_ = listOf("Cinema", "Lanche com amigos", "Pizza no fim de semana", "Açaí", "Barzinho")
        repeat(rnd.int(1, 4)) {
            expense(anyDay(), fun_[rnd.int(0, fun_.size)], "Lazer", rnd.money(25.0, 75.0),
                if (rnd.chance(50)) "Pix" else "Cartão de Crédito")
        }
        val shops = listOf("Camiseta", "Fone de ouvido", "Utensílios de casa", "Produtos de limpeza")
        if (rnd.chance(50)) {
            expense(anyDay(), shops[rnd.int(0, shops.size)], "Compras", rnd.money(35.0, 120.0), "Cartão de Crédito")
        }
        if (rnd.chance(35)) expense(anyDay(), "Corte de cabelo", "Outros", 35.00, "Dinheiro")

        if (month == 2 || month == 8) expense(rnd.int(3, 20), "Xerox e material", "Educacao", rnd.money(50.0, 95.0), "Dinheiro")
        if (month == 5) expense(20, "Consulta no dentista", "Saude", 180.00, "Pix")
        if (month == 6) expense(22, "Festa junina", "Lazer", 60.00, "Dinheiro")
        if (month == 7) expense(14, "Viagem para a casa dos pais", "Lazer", 380.00, "Pix")
        if (month == 9) expense(9, "Conserto do notebook", "Compras", 280.00, "Cartão de Crédito")
        if (month == 11) expense(28, "Tênis na Black Friday", "Compras", 229.90, "Cartão de Crédito")
        if (month == 12) {
            expense(20, "Presentes de Natal", "Compras", 260.00, "Cartão de Crédito")
            expense(23, "Ceia de Natal", "Alimentacao", 140.00, "Pix")
        }

        return list
    }

    private class SeededRandom(seed: Int) {
        private var state = seed.toLong() and 0x7FFFFFFF

        private fun next(): Long {
            state = (state * 1103515245 + 12345) and 0x7FFFFFFF
            return state
        }

        fun int(from: Int, until: Int): Int = from + (next() % (until - from)).toInt()

        fun money(min: Double, max: Double): Double =
            Math.round((min + next() / 2147483648.0 * (max - min)) * 100) / 100.0

        fun chance(percent: Int): Boolean = next() % 100 < percent
    }

    companion object {
        const val PREFIX = "demo_"

        val RECURRING = listOf(
            RecurringItem(id = "demo_salario",   title = "Salário",                     type = "receita", amount = 2450.00, category = "Salario",     dayOfMonth = 5),
            RecurringItem(id = "demo_onibus",    title = "Recarga do cartão de ônibus", type = "despesa", amount = 176.00,  category = "Transporte",  paymentMethod = "Pix",               dayOfMonth = 1),
            RecurringItem(id = "demo_spotify",   title = "Spotify",                     type = "despesa", amount = 21.90,   category = "Assinaturas", paymentMethod = "Cartão de Crédito", dayOfMonth = 3),
            RecurringItem(id = "demo_internet",  title = "Internet",                    type = "despesa", amount = 79.90,   category = "Contas",      paymentMethod = "Cartão de Débito",  dayOfMonth = 8),
            RecurringItem(id = "demo_celular",   title = "Plano de celular",            type = "despesa", amount = 35.00,   category = "Contas",      paymentMethod = "Cartão de Crédito", dayOfMonth = 8),
            RecurringItem(id = "demo_aluguel",   title = "Aluguel da kitnet",           type = "despesa", amount = 750.00,  category = "Moradia",     paymentMethod = "Pix",               dayOfMonth = 10),
            RecurringItem(id = "demo_faculdade", title = "Mensalidade da faculdade",    type = "despesa", amount = 350.00,  category = "Educacao",    paymentMethod = "Pix",               dayOfMonth = 15)
        )
    }
}
