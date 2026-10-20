package com.borgeiz.meutcc2026.util

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
