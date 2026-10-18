package com.borgeiz.meutcc2026.model

// Formas de pagamento padrão: semeiam CategoryConfig.payment na primeira leitura
// e servem de reserva se a lista do usuário vier vazia. O usuário personaliza a
// lista em Perfil > Categorias > Pagamento. Usada só em despesas — receita
// não tem "forma de pagamento" no sentido de Pix/cartão/dinheiro.
object PaymentMethods {
    val ALL = listOf(
        "Dinheiro", "Pix", "Cartão de Crédito",
        "Cartão de Débito", "Transferência", "Outros"
    )

    const val NAO_INFORMADO = "Não informado"
}
