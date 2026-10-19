package com.borgeiz.meutcc2026.util

// As datas continuam salvas no Firebase (e usadas em cálculos, filtros e
// ordenação) no formato "yyyy-MM-dd". Estas funções só mudam a data na hora de
// MOSTRAR para o usuário ("18/10/2026") e desfazem a mudança na hora de ler um
// campo de data da tela de volta para o formato salvo.

/** "2026-10-18" (como está salvo) -> "18/10/2026" (como aparece na tela). */
fun formatDateDisplay(stored: String): String {
    val parts = stored.trim().split('-')
    if (parts.size != 3) return stored
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

/** Data escolhida no calendário -> texto exibido no campo ("18/10/2026"). */
fun formatDateDisplay(year: Int, month: Int, day: Int): String =
    "%02d/%02d/%d".format(day, month, year)

/** Texto de um campo de data ("18/10/2026") -> formato salvo ("2026-10-18"). */
fun toStoredDate(display: String): String {
    val parts = splitDisplayDate(display) ?: return display.trim()
    return "${parts[0]}-${parts[1]}-${parts[2]}"
}

/**
 * Separa um campo de data em [ano, mês, dia]. Aceita "18/10/2026" (como
 * aparece na tela) e também "2026-10-18" (formato salvo).
 */
fun splitDisplayDate(display: String?): List<String>? {
    val text = display?.trim() ?: return null
    return if (text.contains('/')) {
        text.split('/').takeIf { it.size == 3 }?.let { (d, m, y) -> listOf(y, m, d) }
    } else {
        text.split('-').takeIf { it.size == 3 }
    }
}
