package com.borgeiz.meutcc2026.data

import com.borgeiz.meutcc2026.model.RecurringConfig
import com.borgeiz.meutcc2026.model.RecurringItem
import com.borgeiz.meutcc2026.model.SalaryConfig
import com.borgeiz.meutcc2026.model.Transaction
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar
import java.util.UUID

/**
 * Generaliza o antigo mecanismo de salário fixo (SalaryConfig/SalaryEntry)
 * para qualquer receita ou despesa recorrente com dia fixo no mês (salário,
 * Netflix, aluguel, academia etc.).
 *
 * Limitação conhecida: o lançamento automático só roda quando o app é
 * aberto (chamado a partir de MainActivity.onCreate) — não há
 * WorkManager/AlarmManager para rodar em background.
 */
class RecurringRepository(uid: String) {

    private val userRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("users").child(uid)
    private val configRef: DatabaseReference = userRef.child("recurringConfig")
    private val migratedRef: DatabaseReference = userRef.child("recurringConfigMigrated")
    private val legacySalaryConfigRef: DatabaseReference = userRef.child("salaryConfig")
    private val txRef: DatabaseReference = userRef.child("transactions")

    /**
     * Carrega a config atual. Se ainda não existir (usuário nunca configurou
     * recorrências no novo formato) E a migração ainda não tiver rodado, migra
     * uma única vez a configuração antiga de salário fixo, se houver.
     *
     * O Firebase Realtime Database remove nodes cujo valor serializado fica
     * vazio (ex: RecurringConfig com items = []), então salvar uma lista vazia
     * apaga o node "recurringConfig" inteiro. Sem o marcador "recurringConfigMigrated"
     * separado, o próximo loadConfig() interpretaria isso como "nunca configurado"
     * e re-rodaria a migração, ressuscitando o salário antigo.
     */
    fun loadConfig(onResult: (RecurringConfig?) -> Unit) {
        configRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    onResult(snapshot.getValue(RecurringConfig::class.java))
                    return
                }
                migratedRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(migratedSnapshot: DataSnapshot) {
                        if (migratedSnapshot.getValue(Boolean::class.java) == true) {
                            onResult(RecurringConfig(items = emptyList()))
                            return
                        }
                        legacySalaryConfigRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(legacySnapshot: DataSnapshot) {
                                val legacy = legacySnapshot.getValue(SalaryConfig::class.java)
                                val items = legacy?.resolvedEntries().orEmpty().map { entry ->
                                    RecurringItem(
                                        id = UUID.randomUUID().toString(),
                                        title = "Salário",
                                        type = "receita",
                                        amount = entry.amount,
                                        category = "Salário",
                                        dayOfMonth = entry.dayOfMonth
                                    )
                                }
                                val migrated = RecurringConfig(items = items)
                                migratedRef.setValue(true)
                                if (items.isEmpty()) {
                                    onResult(migrated)
                                } else {
                                    // Persiste a migração para os ids gerados ficarem estáveis entre
                                    // execuções — sem isso, o dedup de checkAndPostIfNeeded (que usa
                                    // recurringId) quebraria e o salário seria relançado repetidamente.
                                    configRef.setValue(migrated).addOnCompleteListener {
                                        onResult(migrated)
                                    }
                                }
                            }
                            override fun onCancelled(error: DatabaseError) { onResult(null) }
                        })
                    }
                    override fun onCancelled(error: DatabaseError) { onResult(null) }
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun saveConfig(config: RecurringConfig): Task<Void> {
        // Marca a migração como concluída em qualquer save explícito do usuário
        // (mesmo salvando uma lista vazia) para nunca mais re-rodar a migração
        // legada, já que o node "recurringConfig" pode desaparecer do Firebase
        // quando fica vazio (ver comentário em loadConfig).
        migratedRef.setValue(true)
        return configRef.setValue(config)
    }

    /**
     * Lança as transações pendentes do mês atual, uma por item recorrente
     * cujo dia já chegou e que ainda não tenha lançamento no mês
     * (identificado pelo par recurringId + data esperada).
     */
    fun checkAndPostIfNeeded(config: RecurringConfig, onPosted: (Transaction) -> Unit = {}) {
        val items = config.items.filter { it.dayOfMonth in 1..31 }
        if (items.isEmpty()) return

        val cal   = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)

        txRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existing = snapshot.children.mapNotNull { child ->
                    child.getValue(Transaction::class.java)
                }.filter { it.recurringId.isNotBlank() }
                    .map { it.recurringId to it.date }
                    .toSet()

                items.forEach { recurring ->
                    if (today < recurring.dayOfMonth) return@forEach
                    val expectedDate = "%04d-%02d-%02d".format(year, month, recurring.dayOfMonth)
                    if ((recurring.id to expectedDate) in existing) return@forEach

                    val key = txRef.push().key ?: return@forEach
                    val transaction = Transaction(
                        type = recurring.type,
                        title = recurring.title,
                        amount = recurring.amount,
                        category = recurring.category,
                        date = expectedDate,
                        description = "Lançamento automático",
                        recurringId = recurring.id
                    )
                    txRef.child(key).setValue(transaction).addOnSuccessListener {
                        onPosted(transaction)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
