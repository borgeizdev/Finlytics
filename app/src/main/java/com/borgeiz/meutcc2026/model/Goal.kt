package com.borgeiz.meutcc2026.model

data class Goal(
    var id: String = "",
    var category: String = "",
    var targetAmount: Double = 0.0,
    var period: String = "mensal",
    var type: String = "gasto"
)

data class GoalConfig(
    var items: List<Goal> = emptyList()
)
