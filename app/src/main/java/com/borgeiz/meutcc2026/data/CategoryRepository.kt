package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.CategoryConfig
import com.borgeiz.meutcc2026.model.PaymentMethods
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


data class CategoryRenames(
    val income: Map<String, String> = emptyMap(),
    val expense: Map<String, String> = emptyMap(),
    val payment: Map<String, String> = emptyMap()
) {
    fun isEmpty() = income.isEmpty() && expense.isEmpty() && payment.isEmpty()
}

class CategoryRepository(uid: String) {

    private val userRef: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("users").child(uid)

    private val ref: DatabaseReference = userRef.child("categories")

    fun loadConfig(onResult: (CategoryConfig) -> Unit) {
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val config = snapshot.getValue(CategoryConfig::class.java) ?: CategoryConfig()

                    if (config.payment.isEmpty()) {
                        config.payment = DEFAULT_PAYMENT
                        ref.child("payment").setValue(DEFAULT_PAYMENT)
                    }
                    onResult(config)
                    return
                }
                val defaults = defaultConfig()
                ref.setValue(defaults).addOnCompleteListener { onResult(defaults) }
            }
            override fun onCancelled(error: DatabaseError) {
                onResult(defaultConfig())
            }
        })
    }


    fun saveConfig(config: CategoryConfig): Task<Void> = ref.setValue(config)

    fun saveConfig(config: CategoryConfig, renames: CategoryRenames): Task<Void> {
        if (renames.isEmpty()) return saveConfig(config)

        return userRef.get().continueWithTask { task ->
            val snapshot = task.result
            val updates = mutableMapOf<String, Any?>(
                "categories/income" to config.income,
                "categories/expense" to config.expense,
                "categories/payment" to config.payment
            )

            snapshot.child("transactions").children.forEach { tx ->
                val type = tx.child("type").getValue(String::class.java)
                val category = tx.child("category").getValue(String::class.java).orEmpty()
                val payment = tx.child("paymentMethod").getValue(String::class.java).orEmpty()
                renames.forType(type).renamed(category)?.let {
                    updates["transactions/${tx.key}/category"] = it
                }
                renames.payment.renamed(payment)?.let {
                    updates["transactions/${tx.key}/paymentMethod"] = it
                }
            }

            snapshot.child("recurringConfig").child("items").children.forEach { item ->
                val type = item.child("type").getValue(String::class.java)
                val category = item.child("category").getValue(String::class.java).orEmpty()
                renames.forType(type).renamed(category)?.let {
                    updates["recurringConfig/items/${item.key}/category"] = it
                }
                val payment = item.child("paymentMethod").getValue(String::class.java).orEmpty()
                renames.payment.renamed(payment)?.let {
                    updates["recurringConfig/items/${item.key}/paymentMethod"] = it
                }
            }

            snapshot.child("goals").child("items").children.forEach { goal ->
                if (goal.child("type").getValue(String::class.java) == "lucro") return@forEach
                val category = goal.child("category").getValue(String::class.java).orEmpty()
                renames.expense.renamed(category)?.let {
                    updates["goals/items/${goal.key}/category"] = it
                }
            }

            userRef.updateChildren(updates)
        }
    }

    private fun CategoryRenames.forType(type: String?): Map<String, String> = when (type) {
        "receita" -> income
        "despesa" -> expense
        else -> emptyMap()
    }

    private fun Map<String, String>.renamed(value: String): String? {
        if (value.isBlank()) return null
        return entries.firstOrNull { it.key.equals(value, ignoreCase = true) }?.value
    }

    companion object {
        fun defaultConfig() = CategoryConfig(
            income = DEFAULT_INCOME, expense = DEFAULT_EXPENSE, payment = DEFAULT_PAYMENT
        )

        val DEFAULT_PAYMENT = PaymentMethods.ALL
        val DEFAULT_INCOME = listOf(
            "Salario", "Freelance", "Investimentos",
            "Venda", "Presente", "Reembolso", "Outros"
        )
        val DEFAULT_EXPENSE = listOf(
            "Alimentacao", "Transporte", "Moradia",
            "Contas", "Saude", "Lazer",
            "Educacao", "Compras", "Assinaturas", "Outros"
        )
    }
}
