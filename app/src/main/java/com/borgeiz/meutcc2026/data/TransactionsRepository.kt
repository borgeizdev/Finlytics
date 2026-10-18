package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.Transaction
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TransactionsRepository(uid: String) {

    private val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("users").child(uid).child("transactions")
    fun observe(onChange: (List<Transaction>) -> Unit): ValueEventListener {

        val listener = object : ValueEventListener {


            override fun onDataChange(snapshot: DataSnapshot) {

                val list = snapshot.children.mapNotNull { item ->

                    item.getValue(Transaction::class.java)?.apply { id = item.key ?: "" }
                }

                onChange(list)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)

        return listener
    }

    fun removeObserver(listener: ValueEventListener) {
        ref.removeEventListener(listener)
    }

    fun add(transaction: Transaction): Task<Void> {

        val key = ref.push().key

            ?: return Tasks.forException(IllegalStateException("Não foi possível gerar uma chave"))
        return ref.child(key).setValue(transaction)
    }


    fun update(id: String, transaction: Transaction): Task<Void> = ref.child(id).setValue(transaction)


    fun delete(id: String): Task<Void> = ref.child(id).removeValue()
}
fun List<Transaction>.incomeTotal(): Double = filter { it.type == "receita" }.sumOf { it.amount }
fun List<Transaction>.expenseTotal(): Double = filter { it.type == "despesa" }.sumOf { it.amount }
