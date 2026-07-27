package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.SalaryConfig
import com.borgeiz.meutcc2026.model.Transaction
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

/**
 * Ponto único de acesso a users/{uid}/salaryConfig e ao lançamento
 * automático de salário. Antes desta classe, MainActivity e ProfileFragment
 * tinham cada uma sua própria cópia de checkAndPostSalaryIfNeeded, e uma
 * delas divergiu (faltava o guard de dayOfMonth == 0, gerando transações
 * com data "...-00").
 */
class SalaryRepository(uid: String) {

    private val userRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("users").child(uid)
    private val configRef: DatabaseReference = userRef.child("salaryConfig")
    private val txRef: DatabaseReference = userRef.child("transactions")

    fun loadConfig(onResult: (SalaryConfig?) -> Unit) {
        configRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(snapshot.getValue(SalaryConfig::class.java))
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun observeConfig(onChange: (SalaryConfig?) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onChange(snapshot.getValue(SalaryConfig::class.java))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        configRef.addValueEventListener(listener)
        return listener
    }

    fun removeConfigObserver(listener: ValueEventListener) {
        configRef.removeEventListener(listener)
    }

    fun saveConfig(config: SalaryConfig): Task<Void> = configRef.setValue(config)

    /**
     * Lança as transações de salário pendentes do mês atual, uma por
     * entrada configurada com dia fixo (dayOfMonth > 0) cujo dia já
     * chegou e que ainda não tenha um lançamento na data esperada.
     * Entradas sem dia fixo (dayOfMonth == 0) nunca são lançadas
     * automaticamente.
     */
    fun checkAndPostSalaryIfNeeded(config: SalaryConfig, onPosted: (Transaction) -> Unit = {}) {
        val entries = config.resolvedEntries()
        if (entries.isEmpty()) return

        val cal   = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)

        txRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingDates = snapshot.children.mapNotNull { item ->
                    val t = item.getValue(Transaction::class.java)
                    if (t?.title == "Salário" && t.type == "receita") t.date else null
                }.toSet()

                entries.forEach { entry ->
                    if (entry.dayOfMonth == 0) return@forEach
                    if (today < entry.dayOfMonth) return@forEach
                    val expectedDate = "%04d-%02d-%02d".format(year, month, entry.dayOfMonth)
                    if (expectedDate !in existingDates) {
                        val key = txRef.push().key ?: return@forEach
                        val transaction = Transaction(
                            type = "receita", title = "Salário",
                            amount = entry.amount, category = "Salário",
                            date = expectedDate, description = "Salário automático"
                        )
                        txRef.child(key).setValue(transaction).addOnSuccessListener {
                            onPosted(transaction)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
