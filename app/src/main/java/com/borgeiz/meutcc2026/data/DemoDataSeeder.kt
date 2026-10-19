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

    /**
     * Apaga a demonstração anterior (se houver) e grava a nova de uma vez
     * (escrita única e atômica). Apagar antes evita sobrar lançamentos de uma
     * geração antiga que tinha mais itens por mês.
     */
    fun generate(year: Int): Task<Void> {
        val updates = HashMap<String, Any?>()
        for (month in 1..12) {
            buildMonth(year, month).forEachIndexed { idx, t ->
                val key = "%s%04d_%02d_%03d".format(PREFIX, year, month, idx + 1)
                updates[key] = t
            }
        }
        return clear().continueWithTask { ref.updateChildren(updates) }
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

        // Perfil: estudante universitário que trabalha (CLT júnior), mora sozinho
        // numa kitnet alugada, anda de ônibus e faz um freela de vez em quando.
        // Sobra pouco por mês (uns R$ 200); o 13º e os freelas ajudam no saldo do ano.

        // ---------------- Receitas ----------------
        income(5, "Salário", "Salario", 2450.00, "Salário mensal")
        if (month == 12) income(18, "13º salário", "Salario", 2450.00)
        if (month in setOf(3, 6, 9, 11)) {
            val jobs = listOf("Arte para redes sociais", "Freela de formatação de TCC", "Ajuste em site de cliente")
            income(anyDay(), jobs[rnd.nextInt(jobs.size)], "Freelance", money(200.0, 480.0))
        }
        income(28, "Rendimento da poupança", "Investimentos", money(4.0, 14.0))
        if (month == 4) income(14, "Reembolso de compra online", "Reembolso", 89.90)
        if (month == 8) income(21, "Venda de celular antigo", "Venda", 350.00)
        if (month == 10) income(12, "Presente de aniversário", "Presente", 150.00)

        // ---------------- Moradia e contas fixas ----------------
        expense(10, "Aluguel da kitnet", "Moradia", 750.00, "Pix")
        // Conta de luz um pouco mais alta no verão (ventilador ligado direto).
        val summer = month in setOf(1, 2, 3, 12)
        expense(12, "Conta de luz", "Contas", if (summer) money(95.0, 135.0) else money(62.0, 90.0), "Pix")
        expense(15, "Conta de água", "Contas", money(38.0, 58.0), "Pix")
        expense(8, "Internet", "Contas", 79.90, "Cartão de Débito")
        expense(8, "Plano de celular", "Contas", 35.00, "Cartão de Crédito")

        // ---------------- Assinaturas ----------------
        expense(3, "Spotify", "Assinaturas", 21.90, "Cartão de Crédito")

        // ---------------- Educação ----------------
        expense(15, "Mensalidade da faculdade", "Educacao", 350.00, "Pix")
        if (month == 2 || month == 8) expense(anyDay(), "Xerox e material", "Educacao", money(45.0, 90.0), "Dinheiro")

        // ---------------- Alimentação ----------------
        for (week in 0 until 4) {
            val payment = if (rnd.nextBoolean()) "Cartão de Débito" else "Pix"
            expense(2 + week * 7, "Supermercado", "Alimentacao", money(80.0, 145.0), payment)
        }
        repeat(rnd.nextInt(1, 3)) {
            expense(anyDay(), "iFood", "Alimentacao", money(25.0, 48.0), "Cartão de Crédito")
        }
        repeat(rnd.nextInt(2, 4)) {
            expense(anyDay(), "Padaria", "Alimentacao", money(6.0, 18.0), "Dinheiro")
        }

        // ---------------- Transporte ----------------
        expense(1, "Recarga do cartão de ônibus", "Transporte", 176.00, "Pix")
        repeat(rnd.nextInt(0, 3)) {
            expense(anyDay(), "Uber", "Transporte", money(12.0, 28.0), "Cartão de Crédito")
        }

        // ---------------- Saúde ----------------
        expense(anyDay(), "Farmácia", "Saude", money(15.0, 60.0), "Cartão de Débito")
        if (month == 5) expense(anyDay(), "Consulta no dentista", "Saude", 150.00, "Pix")

        // ---------------- Lazer ----------------
        val fun_ = listOf("Cinema", "Lanche com amigos", "Pizza no fim de semana", "Açaí")
        repeat(rnd.nextInt(1, 3)) {
            expense(anyDay(), fun_[rnd.nextInt(fun_.size)], "Lazer", money(20.0, 70.0),
                if (rnd.nextBoolean()) "Pix" else "Cartão de Crédito")
        }
        if (month == 7) expense(14, "Viagem para a casa dos pais", "Lazer", 380.00, "Pix")

        // ---------------- Compras ----------------
        val shops = listOf("Camiseta", "Fone de ouvido", "Utensílios de casa", "Produtos de limpeza")
        if (rnd.nextBoolean()) {
            expense(anyDay(), shops[rnd.nextInt(shops.size)], "Compras", money(30.0, 120.0), "Cartão de Crédito")
        }
        if (month == 11) expense(27, "Tênis na Black Friday", "Compras", 229.90, "Cartão de Crédito")
        if (month == 12) expense(20, "Presentes de Natal", "Compras", 210.00, "Cartão de Crédito")

        // ---------------- Outros ----------------
        if (rnd.nextInt(3) == 0) expense(anyDay(), "Corte de cabelo", "Outros", 35.00, "Dinheiro")

        return list
    }

    companion object {
        const val PREFIX = "demo_"
    }
}
