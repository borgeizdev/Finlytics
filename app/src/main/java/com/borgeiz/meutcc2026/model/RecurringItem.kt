package com.borgeiz.meutcc2026.model

// Um lançamento que se repete todo mês no mesmo dia (salário, Netflix, aluguel).
// Não é uma Transaction — é a "receita" que RecurringRepository usa pra GERAR
// transações de verdade automaticamente (ver checkAndPostIfNeeded).
data class RecurringItem(
    var id: String = "",
    var title: String = "",
    var type: String = "",
    var amount: Double = 0.0,
    var category: String = "",
    var dayOfMonth: Int = 1
)

data class RecurringConfig(
    var items: List<RecurringItem> = emptyList()
)
