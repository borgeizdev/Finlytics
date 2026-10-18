package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.Transaction
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.tasks.TaskCompletionSource
import kotlin.random.Random

/**
 * Gera um ano inteiro (janeiro a dezembro) de lançamentos fictícios, mas com
 * cara de reais, para demonstrar o app (ex: apresentação do TCC): salário,
 * freelas, aluguel, contas, mercado, transporte, lazer, compras de fim de ano...
 *
 * Todo lançamento de demonstração é gravado com uma chave começando por
 * [PREFIX] (ex: "demo_2026_03_007"), o que permite:
 * - gerar de novo sem duplicar (as mesmas chaves são sobrescritas);
 * - remover só os dados de demonstração, sem tocar nos lançamentos reais.
 *
 * Os valores saem de um Random com semente fixa por mês, então o "ano de
 * demonstração" é sempre o mesmo a cada geração.
 */
class DemoDataSeeder(uid: String) {

    private val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("users").child(uid).child("transactions")

    /** Grava todos os lançamentos de uma vez (escrita única e atômica). */
    fun generate(year: Int): Task<Void> {
        val updates = HashMap<String, Any?>()
        for (month in 1..12) {
            buildMonth(year, month).forEachIndexed { idx, t ->
                val key = "%s%04d_%02d_%03d".format(PREFIX, year, month, idx + 1)
                updates[key] = t
            }
        }
        return ref.updateChildren(updates)
    }

    /** Apaga só os lançamentos cuja chave começa com [PREFIX]. */
    fun clear(): Task<Void> {
        val source = TaskCompletionSource<Void>()
        ref.orderByKey().startAt(PREFIX).endAt(PREFIX + "")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val updates = HashMap<String, Any?>()
                    snapshot.children.forEach { child -> child.key?.let { updates[it] = null } }
                    if (updates.isEmpty()) { source.setResult(null); return }
                    ref.updateChildren(updates)
                        .addOnSuccessListener { source.setResult(null) }
                        .addOnFailureListener { source.setException(it) }
                }
                override fun onCancelled(error: DatabaseError) {
                    source.setException(error.toException())
                }
            })
        return source.task
    }

    // ------------------------------------------------------------------
    // Montagem de um mês
    // ------------------------------------------------------------------

    private fun buildMonth(year: Int, month: Int): List<Transaction> {
        val rnd = Random(year * 100 + month)
        val list = mutableListOf<Transaction>()

        fun date(day: Int) = "%04d-%02d-%02d".format(year, month, day)
        fun money(min: Double, max: Double) = Math.round(rnd.nextDouble(min, max) * 100) / 100.0
        fun anyDay() = rnd.nextInt(1, 29)

        fun income(day: Int, title: String, category: String, amount: Double, desc: String = "") =
            list.add(Transaction(type = "receita", title = title, amount = amount,
                category = category, date = date(day), description = desc))

        fun expense(day: Int, title: String, category: String, amount: Double, payment: String, desc: String = "") =
            list.add(Transaction(type = "despesa", title = title, amount = amount,
                category = category, date = date(day), description = desc, paymentMethod = payment))

        // ---------------- Receitas ----------------
        income(5, "Salário", "Salario", 4200.00, "Salário mensal")
        if (month == 12) income(18, "13º salário", "Salario", 4200.00)
        if (month in setOf(2, 4, 5, 7, 9, 11)) {
            val jobs = listOf("Site para cliente", "Projeto freelance", "Manutenção de sistema", "Landing page")
            income(anyDay(), jobs[rnd.nextInt(jobs.size)], "Freelance", money(450.0, 1500.0))
        }
        income(28, "Rendimento da poupança", "Investimentos", money(22.0, 58.0))
        if (month == 3) income(14, "Reembolso de compra online", "Reembolso", 189.90)
        if (month == 6) income(21, "Venda de bicicleta usada", "Venda", 650.00)
        if (month == 10) income(12, "Presente de aniversário", "Presente", 300.00)

        // ---------------- Moradia e contas fixas ----------------
        expense(10, "Aluguel", "Moradia", 1100.00, "Pix")
        // Conta de luz mais alta no verão (ar-condicionado/ventilador).
        val summer = month in setOf(1, 2, 3, 12)
        expense(12, "Conta de luz", "Contas", if (summer) money(170.0, 230.0) else money(105.0, 150.0), "Pix")
        expense(15, "Conta de água", "Contas", money(58.0, 92.0), "Pix")
        expense(8, "Internet", "Contas", 99.90, "Cartão de Débito")
        expense(8, "Plano de celular", "Contas", 49.90, "Cartão de Crédito")

        // ---------------- Assinaturas ----------------
        expense(3, "Netflix", "Assinaturas", 39.90, "Cartão de Crédito")
        expense(3, "Spotify", "Assinaturas", 21.90, "Cartão de Crédito")

        // ---------------- Educação ----------------
        expense(15, "Mensalidade da faculdade", "Educacao", 480.00, "Pix")
        if (month == 2 || month == 8) expense(anyDay(), "Livros do semestre", "Educacao", money(120.0, 240.0), "Cartão de Crédito")

        // ---------------- Alimentação ----------------
        for (week in 0 until 4) {
            val payment = if (rnd.nextBoolean()) "Cartão de Débito" else "Cartão de Crédito"
            expense(2 + week * 7, "Supermercado", "Alimentacao", money(140.0, 290.0), payment)
        }
        repeat(rnd.nextInt(2, 5)) {
            expense(anyDay(), "iFood", "Alimentacao", money(32.0, 85.0), "Cartão de Crédito")
        }
        repeat(rnd.nextInt(2, 4)) {
            expense(anyDay(), "Padaria", "Alimentacao", money(9.0, 28.0), "Dinheiro")
        }

        // ---------------- Transporte ----------------
        repeat(2) { i ->
            expense(6 + i * 14, "Combustível", "Transporte", money(120.0, 210.0), "Cartão de Crédito")
        }
        repeat(rnd.nextInt(1, 4)) {
            expense(anyDay(), "Uber", "Transporte", money(16.0, 42.0), "Cartão de Crédito")
        }
        if (month == 1) expense(20, "IPVA", "Transporte", 980.00, "Pix")
        if (month == 5) expense(17, "Revisão do carro", "Transporte", 420.00, "Cartão de Crédito")

        // ---------------- Saúde ----------------
        repeat(rnd.nextInt(1, 3)) {
            expense(anyDay(), "Farmácia", "Saude", money(22.0, 110.0), "Cartão de Débito")
        }
        if (month == 4 || month == 9) expense(anyDay(), "Consulta médica", "Saude", 250.00, "Pix")

        // ---------------- Lazer ----------------
        val fun_ = listOf("Cinema", "Barzinho com amigos", "Show", "Passeio no shopping", "Pizzaria")
        repeat(rnd.nextInt(1, 4)) {
            expense(anyDay(), fun_[rnd.nextInt(fun_.size)], "Lazer", money(35.0, 140.0),
                if (rnd.nextBoolean()) "Pix" else "Cartão de Crédito")
        }
        if (month == 7) expense(14, "Viagem de férias", "Lazer", 1250.00, "Cartão de Crédito")

        // ---------------- Compras ----------------
        val shops = listOf("Roupas", "Tênis", "Eletrônicos", "Utensílios de casa", "Presente para amigo")
        repeat(rnd.nextInt(0, 3)) {
            expense(anyDay(), shops[rnd.nextInt(shops.size)], "Compras", money(60.0, 320.0), "Cartão de Crédito")
        }
        if (month == 11) expense(27, "Compras da Black Friday", "Compras", 890.00, "Cartão de Crédito")
        if (month == 12) expense(20, "Presentes de Natal", "Compras", 640.00, "Cartão de Crédito")

        // ---------------- Outros ----------------
        if (rnd.nextInt(3) == 0) expense(anyDay(), "Corte de cabelo", "Outros", 45.00, "Dinheiro")

        return list
    }

    companion object {
        const val PREFIX = "demo_"
    }
}
