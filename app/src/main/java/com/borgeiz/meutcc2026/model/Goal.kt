package com.borgeiz.meutcc2026.model

data class Goal(
    var id: String = "",
    var category: String = "", // "" = meta geral, soma todas as despesas do mês
    var targetAmount: Double = 0.0,
    var period: String = "mensal"
)

data class GoalConfig(
    var items: List<Goal> = emptyList()
)
