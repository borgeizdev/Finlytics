package com.borgeiz.meutcc2026.util

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.widget.EditText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Locale usado em toda a formatação numérica do app. */
private val LOCALE_PT_BR = Locale("pt", "BR")

/**
 * Símbolos fixos do padrão brasileiro: ponto no milhar e vírgula nos centavos.
 * Definidos à mão (em vez de confiar nos dados de locale do aparelho) para que
 * o separador de milhar apareça sempre, em qualquer versão do Android.
 */
private val SYMBOLS_PT_BR = DecimalFormatSymbols(LOCALE_PT_BR).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}

/** Número com milhar e 2 casas decimais (ex: 1234.5 -> "1.234,50"). */
private fun moneyFormat() = DecimalFormat("#,##0.00", SYMBOLS_PT_BR)

/** Número inteiro com milhar (ex: 1234 -> "1.234"). */
private fun integerFormat() = DecimalFormat("#,##0", SYMBOLS_PT_BR)

/** "1.234", "12.345.678"... — só dígitos agrupados de 3 em 3 por pontos. */
private val THOUSANDS_ONLY = Regex("""^\d{1,3}(\.\d{3})+$""")

/**
 * Converte texto digitado em formato monetário pt-BR para Double.
 * Se houver vírgula, ela é tratada como separador decimal e os pontos
 * (se houver) como separador de milhar (ex: "1.234,56" -> 1234.56).
 * Sem vírgula, o texto é interpretado como já está (ex: "150.00" -> 150.0).
 */
// ==========================================================================
// parseAmountPtBr() — linha a linha
// ==========================================================================
// Parâmetro "raw: String?" — o "?" depois de String significa que esse
// parâmetro PODE ser null (o Kotlin distingue tipos que aceitam null dos que
// não aceitam; isso é chamado de "null safety"). O retorno "Double?" também
// pode ser null — é o jeito da função dizer "não consegui converter" sem
// precisar lançar um erro (exception).
fun parseAmountPtBr(raw: String?): Double? {
    // "raw?.trim()" — o "?." é "safe call": só chama .trim() se raw não for
    // null; se for null, a expressão inteira já vira null. ".trim()" remove
    // espaços do início/fim do texto. "?: return null" (operador Elvis): se o
    // resultado até aqui for null, sai da função devolvendo null.
    val trimmed = raw?.trim() ?: return null
    // Texto vazio (só espaços, ou realmente vazio) também não é um valor válido.
    if (trimmed.isEmpty()) return null

    // .contains(',') verifica se existe uma vírgula em algum lugar do texto —
    // é o sinal de que o usuário digitou no formato brasileiro.
    val normalized = if (trimmed.contains(',')) {
        // .replace(".", "") apaga TODOS os pontos do texto (eles são separador
        // de milhar em pt-BR, não têm valor numérico) — ex: "1.234,56" vira
        // "1234,56". Encadeado com .replace(",", "."), que troca a vírgula
        // (decimal em pt-BR) pelo ponto (decimal que o Kotlin entende) — vira
        // "1234.56". As duas chamadas de .replace ficam uma em cima da outra
        // porque cada .replace devolve uma String NOVA (Strings no Kotlin são
        // imutáveis), então a segunda chamada já opera em cima do resultado
        // da primeira.
        trimmed.replace(".", "").replace(",", ".")
    } else if (THOUSANDS_ONLY.matches(trimmed)) {
        // Sem vírgula, mas com pontos no formato de milhar (ex: "1.500", que o
        // próprio campo de valor gera ao digitar): os pontos são só separadores.
        trimmed.replace(".", "")
    } else {
        // Sem vírgula nenhuma: assume que já está em formato "de máquina"
        // (ex: "150.00" digitado direto com ponto) e não mexe.
        trimmed
    }
    // toDoubleOrNull() é a conversão final de String pra Double. Existe também
    // um .toDouble() sem o "OrNull", mas esse lança uma EXCEPTION (erro que
    // derruba o app se ninguém tratar) se o texto não for um número válido —
    // por isso a escolha por toDoubleOrNull(), que devolve null nesse caso, e
    // deixa quem chamou essa função decidir o que fazer (ver validação em
    // AddTransactionFragment: "if (amount == null || amount <= 0.0) { ... }").
    return normalized.toDoubleOrNull()
}

/**
 * Formata um valor monetário no padrão pt-BR, com vírgula nos centavos e
 * ponto no milhar (ex: 1234.5 -> "1.234,50"). Sem prefixo de moeda.
 */
fun formatAmountPtBr(value: Double): String =
    moneyFormat().format(value)

/**
 * Formata um valor inteiro com ponto no milhar (ex: 1234f -> "1.234").
 * Usado nos rótulos curtos dos gráficos.
 */
fun formatIntegerPtBr(value: Double): String =
    integerFormat().format(value)

/**
 * Formata um valor monetário com o prefixo "R$ " (ex: 1234.5 -> "R$ 1.234,50").
 */
fun formatMoneyPtBr(value: Double): String = "R$ " + formatAmountPtBr(value)

/**
 * Formata um valor para edição em campo de texto, no mesmo padrão exibido
 * (ex: 1234.5 -> "1.234,50"). [parseAmountPtBr] entende esse formato de volta.
 */
fun formatAmountInputPtBr(value: Double): String =
    formatAmountPtBr(value)

/**
 * Formata uma porcentagem com uma casa decimal no padrão pt-BR (ex: 0.5 -> "0,5").
 */
fun formatPercentPtBr(value: Double): String =
    String.format(LOCALE_PT_BR, "%.1f", value)

/**
 * Configura um [EditText] para entrada de valores monetários em pt-BR:
 * teclado numérico decimal, apenas dígitos e vírgula vindos do usuário, e o
 * ponto de milhar é inserido automaticamente enquanto se digita
 * (ex: "1500" vira "1.500", "1234567,8" vira "1.234.567,8").
 */
// Extension function sobre EditText: qualquer campo de texto do app vira "campo de
// valor monetário" chamando .setupMoneyInputPtBr() nele. Restringe fisicamente quais
// caracteres o teclado aceita — só dígitos e vírgula, nunca ponto — evitando de raiz
// a ambiguidade entre "ponto de milhar" e "ponto decimal" que parseAmountPtBr
// resolveria depois de qualquer forma.
fun EditText.setupMoneyInputPtBr() {
    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    // O ponto entra na lista só para não ser barrado quando o próprio
    // TextWatcher abaixo o insere; qualquer ponto digitado é refeito por ele.
    keyListener = DigitsKeyListener.getInstance("0123456789,.")
    addTextChangedListener(ThousandsMaskWatcher(this))
}

/**
 * Reescreve o texto do campo a cada alteração, recolocando os pontos de
 * milhar na parte inteira e limitando os centavos a 2 dígitos. A posição do
 * cursor é preservada contando quantos caracteres "reais" (dígitos e vírgula)
 * havia antes dele.
 */
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
            // Um "0" sozinho continua visível (ex: o usuário vai digitar "0,50").
            return if (grouped.isEmpty() && intRaw.any { it == '0' }) "0" else grouped
        }
        val decimals = text.substring(commaIdx + 1).filter { it.isDigit() }.take(2)
        return (grouped.ifEmpty { "0" }) + "," + decimals
    }
}
