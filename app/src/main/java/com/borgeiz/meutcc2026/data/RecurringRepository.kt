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
     * cujo dia já chegou, que já estava cadastrado nessa data (startDate) e que
     * ainda não tenha lançamento no mês (identificado pelo par recurringId + mês).
     */
    // ==========================================================================
    // checkAndPostIfNeeded() — o "motor" da recorrência, linha a linha
    // ==========================================================================
    // Parâmetros: "config" é a configuração de recorrências do usuário (lista de
    // RecurringItem); "onPosted" é uma lambda OPCIONAL (repare no "= {}" — um
    // valor padrão, uma lambda vazia que não faz nada) chamada uma vez pra cada
    // transação lançada automaticamente, útil pra quem chamou mostrar um aviso.
    fun checkAndPostIfNeeded(config: RecurringConfig, onPosted: (Transaction) -> Unit = {}) {
        // Descarta qualquer item com "dia do mês" fora do intervalo válido
        // (proteção contra dado inconsistente). "in 1..31" testa se o número está
        // dentro do INTERVALO (range) de 1 até 31, incluindo as pontas.
        val items = config.items.filter { it.dayOfMonth in 1..31 }
        // Se não sobrou nenhum item recorrente configurado, nem vale a pena
        // consultar o Firebase — sai cedo.
        if (items.isEmpty()) return

        // Pega o dia, mês (+1 porque Calendar.MONTH é zero-indexado) e ano de HOJE.
        val cal   = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)
        // Último dia do mês atual (28, 29, 30 ou 31). Usado abaixo pra que uma
        // recorrência no dia 31 caia no dia 30 em abril, no 28 em fevereiro etc.
        val lastDayOfMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // addListenerForSingleValueEvent: lê o nó de transações UMA VEZ (diferente
        // de observe(), que fica escutando pra sempre) — faz sentido aqui porque
        // isso roda só ao abrir o app, não precisa ficar reagindo a mudanças.
        txRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                // Monta o conjunto (Set) de tudo que JÁ FOI LANÇADO antes, pra
                // depois checar duplicidade. Em 3 passos encadeados:
                val existing = snapshot.children
                    // (1) desserializa cada filho em Transaction (descartando os
                    // que não conseguirem, com mapNotNull).
                    .mapNotNull { child -> child.getValue(Transaction::class.java) }
                    // (2) mantém só as que TÊM um recurringId preenchido — ou
                    // seja, que foram criadas por uma recorrência (transações
                    // manuais não entram nessa checagem).
                    .filter { it.recurringId.isNotBlank() }
                    // (3) transforma cada Transaction num PAR (Pair) de
                    // (recurringId, mês) — "it.recurringId to ..." cria esse par
                    // usando a função infixa "to" do Kotlin; take(7) corta a data
                    // "yyyy-MM-dd" em "yyyy-MM" (só ano e mês). É pelo MÊS, e não
                    // pela data exata, para cada recorrência lançar no máximo uma
                    // vez por mês: se o usuário troca o dia de 5 para 25 depois do
                    // dia 5 já ter sido lançado, o dia 25 não lança de novo no
                    // mesmo mês. .toSet() joga tudo num Set (conjunto sem
                    // repetição), que permite checar "isso já existe?" muito rápido.
                    .map { it.recurringId to it.date.take(7) }
                    .toSet()

                // forEach percorre cada item recorrente configurado, um de cada vez.
                items.forEach { recurring ->
                    // Dia em que o item cai NESTE mês: o dia configurado, ou o
                    // último dia do mês se o configurado não existir nele (ex:
                    // dia 31 em abril vira 30). Sem isso, "today < 31" seria
                    // sempre verdadeiro em meses curtos e o item nunca seria lançado.
                    val day = minOf(recurring.dayOfMonth, lastDayOfMonth)

                    // Se o dia desse item ainda não chegou este mês
                    // (ex: hoje é dia 5 e o salário cai no dia 10), pula ele —
                    // "return@forEach" sai só dessa iteração do loop, não da
                    // função inteira (o rótulo @forEach identifica qual bloco
                    // estamos "retornando").
                    if (today < day) return@forEach

                    // Monta a data que ESSE lançamento deveria ter, no formato
                    // "yyyy-MM-dd" — "%04d-%02d-%02d" são máscaras de formatação:
                    // %04d = número inteiro com 4 dígitos, completando com zeros à
                    // esquerda se precisar (ex: ano 2026 -> "2026"); %02d = 2
                    // dígitos (ex: mês 3 -> "03", dia 5 -> "05"). É o que garante
                    // o formato sempre igual, essencial pra comparação de texto
                    // funcionar em outros lugares do app.
                    val expectedDate = "%04d-%02d-%02d".format(year, month, day)

                    // Se a recorrência foi cadastrada DEPOIS dessa data (ex: hoje é
                    // 19/10 e acabei de cadastrar uma do dia 5), o dia 5 deste mês já
                    // tinha passado quando ela passou a existir — não lança
                    // retroativo, o primeiro lançamento fica para o mês seguinte.
                    // Comparar como texto funciona porque as duas datas estão em
                    // "yyyy-MM-dd". Itens antigos sem startDate não têm esse limite.
                    if (recurring.startDate.isNotBlank() && expectedDate < recurring.startDate) return@forEach

                    // AQUI é a checagem de duplicidade: monta o mesmo tipo de par
                    // (id, mês) que foi montado no Set "existing" acima, e testa
                    // se ele já está lá dentro com o operador "in". Se essa
                    // recorrência já lançou neste mês (em qualquer dia), pula —
                    // não lança de novo.
                    if ((recurring.id to expectedDate.take(7)) in existing) return@forEach

                    // Gera uma chave nova pra essa transação (mesmo mecanismo do
                    // cadastro manual, ver AddTransactionFragment).
                    val key = txRef.push().key ?: return@forEach

                    // Monta o objeto Transaction copiando os dados do
                    // RecurringItem, e preenchendo "recurringId" com o id da
                    // recorrência que a gerou — é esse campo que alimenta o Set
                    // "existing" na próxima vez que essa função rodar, fechando o
                    // ciclo de deduplicação.
                    val transaction = Transaction(
                        type = recurring.type,
                        title = recurring.title,
                        amount = recurring.amount,
                        category = recurring.category,
                        date = expectedDate,
                        description = "Lançamento automático",
                        recurringId = recurring.id
                    )
                    // Grava no banco; se der certo, chama a lambda onPosted
                    // (recebida como parâmetro lá em cima) avisando qual
                    // transação foi lançada.
                    txRef.child(key).setValue(transaction).addOnSuccessListener {
                        onPosted(transaction)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
