package com.borgeiz.meutcc2026.model

// Duas listas de categorias do usuário (uma pra receita, outra pra despesa),
// editáveis em Perfil > Categorias. Alimenta os seletores de categoria em toda
// tela que cadastra ou edita uma transação, recorrência ou meta. A terceira
// lista (payment) são as formas de pagamento das despesas, editáveis na aba
// "Pagamento" da mesma tela.
data class CategoryConfig(
    var income: List<String> = emptyList(),
    var expense: List<String> = emptyList(),
    var payment: List<String> = emptyList()
)
