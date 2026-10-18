package com.borgeiz.meutcc2026.model

// Lista fixa no código, diferente de CategoryConfig: forma de pagamento é sempre
// a mesma pra todo mundo, o usuário não customiza. Usada só em despesas — receita
// não tem "forma de pagamento" no sentido de Pix/cartão/dinheiro.
object PaymentMethods {
    val ALL = listOf(
        "Dinheiro", "Pix", "Cartão de Crédito",
        "Cartão de Débito", "Transferência", "Outros"
    )

    const val NAO_INFORMADO = "Não informado"
}
