package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.GoalConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * users/{uid}/goals — metas de gasto, gerais ou por categoria, sempre mensais.
 */
class GoalRepository(uid: String) {

    private val ref: DatabaseReference = FirebaseDatabase.getInstance().reference
        .child("users").child(uid).child("goals")

    /** Leitura única, usada pelo diálogo de edição (evita sobrescrever o que o usuário está digitando). */
    fun loadConfig(onResult: (GoalConfig) -> Unit) {
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(snapshot.getValue(GoalConfig::class.java) ?: GoalConfig())
            }
            override fun onCancelled(error: DatabaseError) {
                onResult(GoalConfig())
            }
        })
    }

    /** Leitura contínua, usada pela tela de progresso em Reports. */
    fun observe(onChange: (GoalConfig) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onChange(snapshot.getValue(GoalConfig::class.java) ?: GoalConfig())
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeObserver(listener: ValueEventListener) {
        ref.removeEventListener(listener)
    }

    fun saveConfig(config: GoalConfig): Task<Void> = ref.setValue(config)
}
