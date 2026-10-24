package com.borgeiz.meutcc2026.util

import com.borgeiz.meutcc2026.model.PaymentMethods
import com.borgeiz.meutcc2026.model.Transaction

fun List<Transaction>.categoryTotalsForMonth(type: String, year: Int, month: Int): Map<String, Double> {
    return filter { t ->
        t.type == type && run {
            val parts = t.date.split("-")
            parts.size >= 2 && parts[0].toIntOrNull() == year && parts[1].toIntOrNull() == month
        }
    }.groupBy { it.category.ifBlank { "Outros" } }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
}

fun List<Transaction>.paymentTotalsForMonth(year: Int, month: Int): Map<String, Double> {
    return filter { t ->
        t.type == "despesa" && run {
            val parts = t.date.split("-")
            parts.size >= 2 && parts[0].toIntOrNull() == year && parts[1].toIntOrNull() == month
        }
    }.groupBy { it.paymentMethod.ifBlank { PaymentMethods.NAO_INFORMADO } }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
}
