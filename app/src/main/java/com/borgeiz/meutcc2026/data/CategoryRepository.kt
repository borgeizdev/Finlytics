package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.CategoryConfig
import com.borgeiz.meutcc2026.model.PaymentMethods
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class CategoryRepository(uid: String) {

    private val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("users").child(uid).child("categories")

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
