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
    var dayOfMonth: Int = 1,
    // "yyyy-MM-dd" de quando a recorrência passou a valer (cadastro, ou última
    // troca do dia). Nenhum lançamento é gerado para datas anteriores a ela: quem
    // cadastra no dia 19 uma recorrência do dia 5 só recebe a partir do mês
    // seguinte. Vazio em itens antigos (salvos antes deste campo existir) = sem limite.
    var startDate: String = ""
)

data class RecurringConfig(
    var items: List<RecurringItem> = emptyList()
)
