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

class RecurringRepository(uid: String) {

    private val userRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("users").child(uid)
    private val configRef: DatabaseReference = userRef.child("recurringConfig")
    private val migratedRef: DatabaseReference = userRef.child("recurringConfigMigrated")
    private val legacySalaryConfigRef: DatabaseReference = userRef.child("salaryConfig")
    private val txRef: DatabaseReference = userRef.child("transactions")

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
        migratedRef.setValue(true)
        return configRef.setValue(config)
    }


    fun checkAndPostIfNeeded(config: RecurringConfig, onPosted: (Transaction) -> Unit = {}) {

        val items = config.items.filter { it.dayOfMonth in 1..31 }

        if (items.isEmpty()) return


        val cal   = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)

        val lastDayOfMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)


        txRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val existing = snapshot.children
                    .mapNotNull { child -> child.getValue(Transaction::class.java) }
                    .filter { it.recurringId.isNotBlank() }
                    .map { it.recurringId to it.date.take(7) }
                    .toSet()

                items.forEach { recurring ->
                    val day = minOf(recurring.dayOfMonth, lastDayOfMonth)

                    if (today < day) return@forEach

                    val expectedDate = "%04d-%02d-%02d".format(year, month, day)

                    if (recurring.startDate.isNotBlank() && expectedDate < recurring.startDate) return@forEach

                    if ((recurring.id to expectedDate.take(7)) in existing) return@forEach

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
