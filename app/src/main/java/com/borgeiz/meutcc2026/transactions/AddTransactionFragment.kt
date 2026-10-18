package com.borgeiz.meutcc2026.transactions

import com.borgeiz.meutcc2026.R
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.borgeiz.meutcc2026.data.CategoryRepository
import com.borgeiz.meutcc2026.model.CategoryConfig
import com.borgeiz.meutcc2026.model.PaymentMethods
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.util.parseAmountPtBr
import com.borgeiz.meutcc2026.util.setupMoneyInputPtBr
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class AddTransactionFragment : Fragment() {

    private var categoryConfig = CategoryConfig()

    private var selectedType = "receita"

    private lateinit var btnTypeReceita: MaterialButton
    private lateinit var btnTypeDespesa: MaterialButton
    private lateinit var actvCategory: AutoCompleteTextView
    private lateinit var actvPaymentMethod: AutoCompleteTextView
    private lateinit var tilPaymentMethod: com.google.android.material.textfield.TextInputLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_add_transaction, container, false)

        btnTypeReceita = view.findViewById(R.id.btnTypeReceita)
        btnTypeDespesa = view.findViewById(R.id.btnTypeDespesa)
        actvCategory   = view.findViewById(R.id.actvCategory)
        actvPaymentMethod = view.findViewById(R.id.actvPaymentMethod)
        tilPaymentMethod = view.findViewById(R.id.tilPaymentMethod)

        val paymentAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_dropdown_item_1line, PaymentMethods.ALL
        )
        actvPaymentMethod.setAdapter(paymentAdapter)
        actvPaymentMethod.setText(PaymentMethods.ALL[0], false)

        val etTitle       = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etAmount      = view.findViewById<TextInputEditText>(R.id.etAmount)
        val etDate        = view.findViewById<TextInputEditText>(R.id.etDate)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnSaveTransaction)

        etAmount.setupMoneyInputPtBr()

        setType("receita")

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            CategoryRepository(uid).loadConfig { config ->
                if (!isAdded) return@loadConfig
                categoryConfig = config
                setType(selectedType)
            }
        }

        btnTypeReceita.setOnClickListener { setType("receita") }
        btnTypeDespesa.setOnClickListener { setType("despesa") }

        etDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, y, m, d -> etDate.setText("%d-%02d-%02d".format(y, m + 1, d)) },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // ======================================================================
        // CADASTRO DE UMA NOVA TRANSAÇÃO — explicação linha a linha
        // ======================================================================
        // btnSave.setOnClickListener { ... } registra uma LAMBDA (um bloco de
        // código sem nome, entre chaves) que só roda quando o usuário toca no
        // botão "Salvar". É um "listener de clique": o Android guarda essa lambda
        // e a chama sozinho no momento certo — o app não fica checando se foi
        // clicado, é avisado (padrão chamado de "callback").
        btnSave.setOnClickListener {

            // FirebaseAuth.getInstance() pega a instância única (singleton) do
            // serviço de autenticação do Firebase. .currentUser é o usuário
            // logado agora (ou null se ninguém logou). .uid é o ID único dele.
            // O "?." (safe call / chamada segura) faz a expressão inteira virar
            // null automaticamente se currentUser for null, em vez de travar o
            // app com um erro de "null pointer".
            val uid = FirebaseAuth.getInstance().currentUser?.uid

            // Se não tem usuário logado (não deveria acontecer, mas é uma
            // proteção), mostra um aviso (Toast = mensagem curta que some
            // sozinha) e sai da função com "return@setOnClickListener" — esse
            // rótulo (@setOnClickListener) existe porque estamos dentro de uma
            // lambda, não de uma função normal; um "return" sozinho não bastaria
            // pra sair só desse bloco de clique.
            if (uid == null) {
                Toast.makeText(requireContext(), "Usuário não autenticado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // etTitle.text pega o conteúdo atual do campo de texto (pode ser
            // null se o campo nunca recebeu nada). .toString() converte pra
            // String de verdade. .trim() remove espaços em branco do início e
            // do fim (ex: " Mercado " vira "Mercado"). O "?:" no final é o
            // "operador Elvis": se tudo antes dele der null, usa "" (string
            // vazia) como valor de reserva.
            val titleStr  = etTitle.text?.toString()?.trim() ?: ""
            val dateStr   = etDate.text?.toString()?.trim() ?: ""
            val descStr   = etDescription.text?.toString()?.trim() ?: ""

            // Validação do título: se ficou vazio depois do trim, mostra erro
            // no próprio campo (etTitle.error = "...") e sai da função.
            if (titleStr.isEmpty()) { etTitle.error = "Informe o título"; return@setOnClickListener }

            // parseAmountPtBr é uma função nossa (util/MoneyParser.kt) que pega o
            // texto digitado no campo de valor — que pode estar em formato
            // brasileiro, tipo "1.234,56" — e devolve um Double (número com casa
            // decimal) ou null se o texto não for um número válido.
            val amount = parseAmountPtBr(etAmount.text?.toString())
            // Valor precisa existir (não nulo) E ser maior que zero — não faz
            // sentido cadastrar uma transação de R$ 0,00 ou negativa.
            if (amount == null || amount <= 0.0) { etAmount.error = "Informe um valor válido"; return@setOnClickListener }

            // selectedType é uma variável da classe (var, ou seja, mutável) que
            // guarda "receita" ou "despesa" conforme o botão que o usuário tocou
            // (ver setType() mais abaixo). Aqui ela é copiada pra uma "val"
            // (variável que não muda mais) local chamada typeSnapshot — um
            // "retrato" do valor nesse instante exato, pra garantir que o objeto
            // Transaction monte com o tipo certo mesmo que selectedType mude por
            // algum motivo entre o clique e a gravação no banco.
            val typeSnapshot  = selectedType

            // FirebaseDatabase.getInstance() pega a instância do Realtime
            // Database (o banco de dados em si, diferente do Auth). .reference
            // é a "raiz" da árvore de dados. Cada .child("x") desce um nível:
            // users -> {uid} -> transactions. "ref" agora aponta pra pasta de
            // transações DESSE usuário específico — nenhum outro usuário tem
            // acesso a esse caminho (isso é reforçado pelas regras de segurança
            // configuradas no console do Firebase, fora deste código).
            val ref = FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("transactions")

            // ref.push() pede ao Firebase pra gerar uma CHAVE ÚNICA nova (baseada
            // em timestamp + aleatoriedade) — é assim que cada transação ganha um
            // "ID" sem o app precisar inventar um contador. .key extrai o texto
            // dessa chave; se por algum motivo vier null (não deveria), a função
            // para aqui.
            val key = ref.push().key ?: return@setOnClickListener

            // ref.child(key) desce mais um nível na árvore, até o lugar exato
            // onde ESTA transação vai morar: users/{uid}/transactions/{key}.
            // .setValue(...) é a gravação em si: manda o objeto Transaction
            // inteiro pro Firebase, que serializa ele em JSON.
            //
            // Transaction(...) é a "data class" (classe de dados, ver
            // model/Transaction.kt) que representa uma transação. Aqui estamos
            // construindo uma instância nova dela, preenchendo cada campo com o
            // que a gente coletou dos campos de texto acima:
            ref.child(key).setValue(
                Transaction(
                    type        = typeSnapshot,
                    title       = titleStr,
                    amount      = amount,
                    // actvCategory é o campo de categoria (um AutoCompleteTextView,
                    // um campo de texto com sugestões). .text.toString() pega o
                    // que está escrito nele agora.
                    category    = actvCategory.text.toString(),
                    date        = dateStr,
                    description = descStr,
                    // Receita nunca tem forma de pagamento (não faz sentido "pagar"
                    // um salário com Pix) — por isso salva "" nesse caso. Só
                    // despesa de fato lê o campo actvPaymentMethod.
                    paymentMethod = if (typeSnapshot == "receita") "" else actvPaymentMethod.text.toString()
                )
            )
                // setValue() devolve um "Task<Void>" — um objeto que representa
                // uma operação ASSÍNCRONA (que roda em paralelo, sem travar a
                // tela) que "no futuro" vai terminar com sucesso ou com erro.
                // .addOnSuccessListener registra o que fazer QUANDO/SE der certo:
                .addOnSuccessListener {
                    // "context" pode ser null se o usuário já saiu da tela antes
                    // da resposta do Firebase voltar — por segurança, se for
                    // null, a função para aqui (return@addOnSuccessListener).
                    val ctx = context ?: return@addOnSuccessListener
                    Toast.makeText(ctx, "Transação salva!", Toast.LENGTH_SHORT).show()
                    // Limpa os campos do formulário pra o usuário poder cadastrar
                    // a próxima transação sem precisar apagar tudo na mão.
                    etTitle.text?.clear()
                    etAmount.text?.clear()
                    etDate.text?.clear()
                    etDescription.text?.clear()
                }
                // .addOnFailureListener registra o que fazer SE der erro (sem
                // internet, regra de segurança do Firebase bloqueando, etc.):
                .addOnFailureListener {
                    val ctx = context ?: return@addOnFailureListener
                    // "it" aqui é a Exception do erro (nome implícito do único
                    // parâmetro da lambda, quando não damos um nome pra ele).
                    // it.message é o texto descrevendo o que deu errado.
                    Toast.makeText(ctx, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
                }

            // Note que em NENHUM momento este código "avisa" o Dashboard ou os
            // Relatórios que uma transação nova chegou. Isso é trabalho do
            // Firebase: qualquer tela com um listener ativo em
            // users/{uid}/transactions é notificada automaticamente assim que
            // o setValue() acima é confirmado no servidor (ver
            // TransactionsRepository.observe, no arquivo data/TransactionsRepository.kt).
        }

        return view
    }

    private fun setType(type: String) {
        if (!isAdded) return
        selectedType = type
        val ctx           = requireContext()
        val activeColor   = ctx.getColor(R.color.primary)
        val activeText    = ctx.getColor(R.color.on_primary)
        val inactiveBg    = ctx.getColor(R.color.bg_card_subtle)
        val inactiveText  = ctx.getColor(R.color.text_secondary)
        val borderColor   = ctx.getColor(R.color.border)

        if (type == "receita") {
            btnTypeReceita.backgroundTintList = ColorStateList.valueOf(activeColor)
            btnTypeReceita.setTextColor(activeText)
            btnTypeReceita.setStrokeColor(ColorStateList.valueOf(activeColor))
            btnTypeDespesa.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            btnTypeDespesa.setTextColor(inactiveText)
            btnTypeDespesa.setStrokeColor(ColorStateList.valueOf(borderColor))
        } else {
            btnTypeDespesa.backgroundTintList = ColorStateList.valueOf(activeColor)
            btnTypeDespesa.setTextColor(activeText)
            btnTypeDespesa.setStrokeColor(ColorStateList.valueOf(activeColor))
            btnTypeReceita.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            btnTypeReceita.setTextColor(inactiveText)
            btnTypeReceita.setStrokeColor(ColorStateList.valueOf(borderColor))
        }

        val cats = if (type == "receita") categoryConfig.income else categoryConfig.expense
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, cats)
        actvCategory.setAdapter(adapter)
        actvCategory.setText(cats.firstOrNull() ?: "", false)

        // Receita não tem "forma de pagamento" no sentido de Pix/cartão/dinheiro.
        tilPaymentMethod.visibility = if (type == "receita") View.GONE else View.VISIBLE
    }
}
