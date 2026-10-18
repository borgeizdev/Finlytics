package com.borgeiz.meutcc2026.model

/**
 * Meta financeira mensal configurada em Perfil > Metas. O campo "type" faz esta
 * mesma estrutura servir pra dois conceitos opostos (ver ReportsFragment.buildGoalsProgress):
 *   "gasto" -> é um teto: não gastar mais que targetAmount. Progresso alto = ruim.
 *   "lucro" -> é um piso: sobrar pelo menos targetAmount (receita - despesa). Progresso alto = bom.
 */
data class Goal(
    var id: String = "",
    var category: String = "", // usado só quando type == "gasto"; "" = meta geral, soma todas as despesas do mês
    var targetAmount: Double = 0.0,
    var period: String = "mensal",
    // "gasto" = limite de gasto, geral ou por categoria (progresso ruim quando alto)
    // "lucro" = meta de lucro mensal, receita menos despesa (progresso bom quando alto)
    var type: String = "gasto"
)

data class GoalConfig(
    var items: List<Goal> = emptyList()
)
