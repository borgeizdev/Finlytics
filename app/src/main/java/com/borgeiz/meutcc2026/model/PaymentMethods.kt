package com.borgeiz.meutcc2026.model

object PaymentMethods {
    val ALL = listOf(
        "Dinheiro", "Pix", "Cartão de Crédito",
        "Cartão de Débito", "Transferência", "Outros"
    )

    const val NAO_INFORMADO = "Não informado"

    fun isInformed(method: String): Boolean =
        method.isNotBlank() && !method.equals(NAO_INFORMADO, ignoreCase = true)
}
