package com.borgeiz.meutcc2026.util

import java.util.Locale

/** Locale usado em toda a formatação numérica do app. */
private val LOCALE_PT_BR = Locale("pt", "BR")

/**
 * Converte texto digitado em formato monetário pt-BR para Double.
 * Se houver vírgula, ela é tratada como separador decimal e os pontos
 * (se houver) como separador de milhar (ex: "1.234,56" -> 1234.56).
 * Sem vírgula, o texto é interpretado como já está (ex: "150.00" -> 150.0).
 */
fun parseAmountPtBr(raw: String?): Double? {
    val trimmed = raw?.trim() ?: return null
    if (trimmed.isEmpty()) return null
    val normalized = if (trimmed.contains(',')) {
        trimmed.replace(".", "").replace(",", ".")
    } else {
        trimmed
    }
    return normalized.toDoubleOrNull()
}

/**
 * Formata um valor monetário no padrão pt-BR, com vírgula nos centavos e
 * ponto no milhar (ex: 1234.5 -> "1.234,50"). Sem prefixo de moeda.
 */
fun formatAmountPtBr(value: Double): String =
    String.format(LOCALE_PT_BR, "%,.2f", value)

/**
 * Formata um valor monetário com o prefixo "R$ " (ex: 1234.5 -> "R$ 1.234,50").
 */
fun formatMoneyPtBr(value: Double): String = "R$ " + formatAmountPtBr(value)

/**
 * Formata um valor para edição em campo de texto: vírgula nos centavos e
 * sem separador de milhar (ex: 1234.5 -> "1234,50"). Os campos de valor usam
 * android:digits="0123456789,", que descarta qualquer ponto do texto.
 */
fun formatAmountInputPtBr(value: Double): String =
    String.format(LOCALE_PT_BR, "%.2f", value)

/**
 * Formata uma porcentagem com uma casa decimal no padrão pt-BR (ex: 0.5 -> "0,5").
 */
fun formatPercentPtBr(value: Double): String =
    String.format(LOCALE_PT_BR, "%.1f", value)
