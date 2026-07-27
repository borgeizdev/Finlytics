package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.CategoryConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Substitui as listas de categoria hardcoded e duplicadas em
 * AddTransactionFragment/EditTransactionActivity por uma config única em
 * users/{uid}/categories, editável pelo usuário. Na primeira leitura (sem
 * config salva ainda) semeia e persiste os valores que já eram hardcoded,
 * para não quebrar o formulário de ninguém.
 */
class CategoryRepository(uid: String) {

    private val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("users").child(uid).child("categories")

    fun loadConfig(onResult: (CategoryConfig) -> Unit) {
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    onResult(snapshot.getValue(CategoryConfig::class.java) ?: CategoryConfig())
                    return
                }
                val defaults = CategoryConfig(income = DEFAULT_INCOME, expense = DEFAULT_EXPENSE)
                ref.setValue(defaults).addOnCompleteListener { onResult(defaults) }
            }
            override fun onCancelled(error: DatabaseError) {
                onResult(CategoryConfig(income = DEFAULT_INCOME, expense = DEFAULT_EXPENSE))
            }
        })
    }

    fun saveConfig(config: CategoryConfig): Task<Void> = ref.setValue(config)

    companion object {
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
