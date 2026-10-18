package com.borgeiz.meutcc2026.model

import com.google.firebase.database.Exclude

/**
 * Representa um lançamento de receita ou despesa. É a classe mais usada do app:
 * cada campo vira uma chave no JSON gravado em users/{uid}/transactions/{pushId}
 * no Firebase — o SDK mapeia o objeto pra JSON pelo NOME dos campos, via reflexão.
 *
 * - type: sempre o texto "receita" ou "despesa" (não é um enum).
 * - date: sempre string "yyyy-MM-dd", nunca um objeto Date — isso permite comparar
 *   e ordenar datas como texto em vários lugares do app (filtros, ordenação).
 * - recurringId: só é preenchido quando a transação foi criada automaticamente
 *   por uma recorrência (ver RecurringRepository.checkAndPostIfNeeded).
 * - Todo campo tem valor padrão porque o Firebase exige um construtor vazio pra
 *   conseguir desserializar o JSON de volta num objeto Kotlin.
 */
data class Transaction(
    // @Exclude porque o id já existe como a CHAVE do nó no Firebase (gerada por
    // push()). Sem essa anotação, o id apareceria duplicado dentro do próprio JSON.
    // Ele é preenchido manualmente a partir da chave em TransactionsRepository.observe.
    @get:Exclude @set:Exclude var id: String = "",
    var type: String = "",
    var title: String = "",
    var amount: Double = 0.0,
    var category: String = "",
    var date: String = "",
    var description: String = "",
    var paymentMethod: String = "",
    var recurringId: String = ""
)
