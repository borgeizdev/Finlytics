package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.RecurringConfig
import com.borgeiz.meutcc2026.model.RecurringItem
import com.borgeiz.meutcc2026.model.Transaction
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

/**
 * Gera um ano inteiro (janeiro a dezembro) de lançamentos fictícios, mas com
 * cara de reais, para demonstrar o app (ex: apresentação do TCC).
 *
 * Perfil simulado: estudante universitário que trabalha (CLT júnior, salário
 * líquido de R$ 2.450 todo dia 5), mora sozinho numa kitnet, anda de ônibus e
 * faz um freela de vez em quando. Sobra pouco por mês — alguns meses fecham no
 * vermelho — e o 13º e os freelas ajudam no saldo do ano (fecha dezembro na
 * casa dos R$ 3 mil, não num valor irreal).
 *
 * ATENÇÃO: [generate] APAGA todos os lançamentos do usuário e substitui as
 * recorrências pelas da demonstração (salário, aluguel, faculdade...).
 *
 * As transações das recorrências são gravadas com o mesmo recurringId e a
 * mesma data que RecurringRepository.checkAndPostIfNeeded usaria, então o
 * lançamento automático reconhece que elas já existem e não duplica nada.
 */
class DemoDataSeeder(uid: String) {

    private val userRef: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("users").child(uid)

    /**
     * Escrita única e atômica em users/{uid}: o nó "transactions" inteiro é
     * trocado pelos lançamentos novos (o que apaga os antigos) e
     * "recurringConfig" recebe as recorrências da demonstração.
     */
    fun generate(year: Int): Task<Void> {
        val transactions = HashMap<String, Any?>()
        for (month in 1..12) {
            buildMonth(year, month).forEachIndexed { idx, t ->
                val key = "%s%04d_%02d_%03d".format(PREFIX, year, month, idx + 1)
                transactions[key] = t
            }
        }
        val updates = hashMapOf<String, Any?>(
            "transactions" to transactions,
            "recurringConfig" to RecurringConfig(items = RECURRING),
            // Mesmo marcador que RecurringRepository.saveConfig grava: impede que
            // a migração do salário legado rode depois e "ressuscite" um salário antigo.
            "recurringConfigMigrated" to true
        )
        return userRef.updateChildren(updates)
    }

    // ------------------------------------------------------------------
    // Montagem de um mês
    // ------------------------------------------------------------------

    private fun buildMonth(year: Int, month: Int): List<Transaction> {
        val rnd = SeededRandom(year * 100 + month)
        val list = mutableListOf<Transaction>()

        val lastDay = Calendar.getInstance().apply { set(year, month - 1, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)

        // Mesmo ajuste de RecurringRepository: dia que não existe no mês vira o último dia.
        fun date(day: Int) = "%04d-%02d-%02d".format(year, month, minOf(day, lastDay))
        fun anyDay() = rnd.int(1, lastDay + 1)

        fun income(day: Int, title: String, category: String, amount: Double) =
            list.add(Transaction(type = "receita", title = title, amount = amount,
                category = category, date = date(day)))

        fun expense(day: Int, title: String, category: String, amount: Double, payment: String) =
            list.add(Transaction(type = "despesa", title = title, amount = amount,
                category = category, date = date(day), paymentMethod = payment))

        // ---------------- Recorrências (salário e contas fixas) ----------------
        RECURRING.forEach { r ->
            list.add(Transaction(
                type = r.type, title = r.title, amount = r.amount, category = r.category,
                date = date(r.dayOfMonth), description = "Lançamento automático",
                paymentMethod = RECURRING_PAYMENT[r.id] ?: "", recurringId = r.id
            ))
        }

        // ---------------- Receitas extras ----------------
        // 13º em duas parcelas, como manda a CLT: 1ª até 30/11 (sem descontos),
        // 2ª até 20/12 (com INSS/IR descontados, por isso menor).
        if (month == 11) income(28, "13º salário (1ª parcela)", "Salario", 1225.00)
        if (month == 12) income(18, "13º salário (2ª parcela)", "Salario", 1012.40)
        if (month in setOf(3, 6, 9, 11)) {
            val jobs = listOf("Arte para redes sociais", "Formatação de TCC", "Ajuste em site de cliente")
            income(rnd.int(12, 26), jobs[rnd.int(0, jobs.size)], "Freelance", rnd.money(220.0, 420.0))
        }
        if (month == 4) income(14, "Reembolso de compra online", "Reembolso", 89.90)
        if (month == 8) income(21, "Venda do celular antigo", "Venda", 350.00)
        if (month == 10) income(12, "Presente de aniversário", "Presente", 150.00)

        // ---------------- Contas variáveis ----------------
        // Conta de luz mais alta no verão (ventilador ligado direto).
        val summer = month in setOf(1, 2, 3, 12)
        expense(12, "Conta de luz", "Contas", if (summer) rnd.money(105.0, 140.0) else rnd.money(68.0, 92.0), "Pix")
        expense(16, "Conta de água", "Contas", rnd.money(40.0, 58.0), "Pix")

        // ---------------- Alimentação ----------------
        // Mercado uma vez por semana, depois que o salário cai.
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

        // ---------------- Transporte, saúde ----------------
        repeat(rnd.int(0, 3)) {
            expense(anyDay(), "Uber", "Transporte", rnd.money(14.0, 32.0), "Cartão de Crédito")
        }
        expense(anyDay(), "Farmácia", "Saude", rnd.money(18.0, 65.0), "Cartão de Débito")

        // ---------------- Lazer e compras ----------------
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

        // ---------------- Gastos sazonais ----------------
        // Início de cada semestre da faculdade.
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

    /**
     * Gerador pseudoaleatório simples (congruencial linear) com semente fixa por
     * mês: o "ano de demonstração" sai sempre igual a cada geração, então os
     * números da apresentação não mudam de uma vez pra outra.
     */
    private class SeededRandom(seed: Int) {
        private var state = seed.toLong() and 0x7FFFFFFF

        private fun next(): Long {
            state = (state * 1103515245 + 12345) and 0x7FFFFFFF
            return state
        }

        /** Inteiro em [from, until). */
        fun int(from: Int, until: Int): Int = from + (next() % (until - from)).toInt()

        /** Valor em reais entre [min] e [max], arredondado em centavos. */
        fun money(min: Double, max: Double): Double =
            Math.round((min + next() / 2147483648.0 * (max - min)) * 100) / 100.0

        /** true com [percent]% de chance. */
        fun chance(percent: Int): Boolean = next() % 100 < percent
    }

    companion object {
        const val PREFIX = "demo_"

        /** Receitas e despesas fixas da demonstração (gravadas em Perfil > Recorrências). */
        val RECURRING = listOf(
            RecurringItem("demo_salario",   "Salário",                     "receita", 2450.00, "Salario",     5),
            RecurringItem("demo_onibus",    "Recarga do cartão de ônibus", "despesa", 176.00,  "Transporte",  1),
            RecurringItem("demo_spotify",   "Spotify",                     "despesa", 21.90,   "Assinaturas", 3),
            RecurringItem("demo_internet",  "Internet",                    "despesa", 79.90,   "Contas",      8),
            RecurringItem("demo_celular",   "Plano de celular",            "despesa", 35.00,   "Contas",      8),
            RecurringItem("demo_aluguel",   "Aluguel da kitnet",           "despesa", 750.00,  "Moradia",     10),
            RecurringItem("demo_faculdade", "Mensalidade da faculdade",    "despesa", 350.00,  "Educacao",    15)
        )

        // RecurringItem não tem forma de pagamento; na demonstração as recorrências
        // geradas recebem uma, pra aparecerem no relatório por forma de pagamento.
        private val RECURRING_PAYMENT = mapOf(
            "demo_onibus" to "Pix",
            "demo_spotify" to "Cartão de Crédito",
            "demo_internet" to "Cartão de Débito",
            "demo_celular" to "Cartão de Crédito",
            "demo_aluguel" to "Pix",
            "demo_faculdade" to "Pix"
        )
    }
}
