package com.borgeiz.meutcc2026.util

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.widget.EditText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val LOCALE_PT_BR = Locale("pt", "BR")

private val SYMBOLS_PT_BR = DecimalFormatSymbols(LOCALE_PT_BR).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}

private fun moneyFormat() = DecimalFormat("#,##0.00", SYMBOLS_PT_BR)

private fun integerFormat() = DecimalFormat("#,##0", SYMBOLS_PT_BR)

private val THOUSANDS_ONLY = Regex("""^\d{1,3}(\.\d{3})+$""")

fun parseAmountPtBr(raw: String?): Double? {
    val trimmed = raw?.trim() ?: return null
    if (trimmed.isEmpty()) return null

    val normalized = if (trimmed.contains(',')) {
        trimmed.replace(".", "").replace(",", ".")
    } else if (THOUSANDS_ONLY.matches(trimmed)) {
        trimmed.replace(".", "")
    } else {
        trimmed
    }
    return normalized.toDoubleOrNull()
}

fun formatAmountPtBr(value: Double): String =
    moneyFormat().format(value)

fun formatIntegerPtBr(value: Double): String =
    integerFormat().format(value)

fun formatMoneyPtBr(value: Double): String = "R$ " + formatAmountPtBr(value)

fun formatAmountInputPtBr(value: Double): String =
    formatAmountPtBr(value)

fun formatPercentPtBr(value: Double): String =
    String.format(LOCALE_PT_BR, "%.1f", value)

fun EditText.setupMoneyInputPtBr() {
    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    keyListener = DigitsKeyListener.getInstance("0123456789,.")
    addTextChangedListener(ThousandsMaskWatcher(this))
}

private class ThousandsMaskWatcher(private val field: EditText) : TextWatcher {
    private var editing = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (editing || s == null) return
        val original = s.toString()
        val formatted = maskThousands(original)
        if (formatted == original) return

        val cursor = field.selectionEnd.coerceIn(0, original.length)
        val realBeforeCursor = original.take(cursor).count { it.isDigit() || it == ',' }

        editing = true
        s.replace(0, s.length, formatted)
        editing = false

        var seen = 0
        var newCursor = formatted.length
        if (realBeforeCursor == 0) newCursor = 0
        else for (i in formatted.indices) {
            val c = formatted[i]
            if (c.isDigit() || c == ',') seen++
            if (seen == realBeforeCursor) { newCursor = i + 1; break }
        }
        field.setSelection(newCursor.coerceIn(0, formatted.length))
    }

    private fun maskThousands(text: String): String {
        val commaIdx = text.indexOf(',')
        val intRaw = if (commaIdx >= 0) text.substring(0, commaIdx) else text
        val intDigits = intRaw.filter { it.isDigit() }.trimStart('0')
        val grouped = intDigits.reversed().chunked(3).joinToString(".").reversed()
        if (commaIdx < 0) {
            return if (grouped.isEmpty() && intRaw.any { it == '0' }) "0" else grouped
        }
        val decimals = text.substring(commaIdx + 1).filter { it.isDigit() }.take(2)
        return (grouped.ifEmpty { "0" }) + "," + decimals
    }
}
