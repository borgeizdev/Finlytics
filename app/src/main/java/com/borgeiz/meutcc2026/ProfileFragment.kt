package com.borgeiz.meutcc2026

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.borgeiz.meutcc2026.data.RecurringRepository
import com.borgeiz.meutcc2026.model.RecurringConfig
import com.borgeiz.meutcc2026.model.RecurringItem
import com.borgeiz.meutcc2026.util.parseAmountPtBr
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var ref: DatabaseReference

    private val primaryBlue get() = 0xFF2563EB.toInt()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        tvName  = view.findViewById(R.id.tvProfileName)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        tvAvatar = view.findViewById(R.id.tvProfileAvatar)

        val btnLogout   = view.findViewById<MaterialButton>(R.id.btnLogout)
        val rowSettings = view.findViewById<LinearLayout>(R.id.rowSettings)

        val uid = auth.currentUser?.uid ?: return view
        ref = FirebaseDatabase.getInstance().reference.child("users").child(uid).child("profile")

        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("preferences").child("nightMode")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val saved = snapshot.getValue(Int::class.java) ?: return
                    val ctx = context ?: return
                    ctx.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putInt("night_mode", saved).apply()
                    if (AppCompatDelegate.getDefaultNightMode() != saved) {
                        AppCompatDelegate.setDefaultNightMode(saved)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name  = snapshot.child("name").value?.toString() ?: ""
                val email = snapshot.child("email").value?.toString()
                    ?: auth.currentUser?.email ?: ""
                tvName.text   = name.ifBlank { "Usuario" }
                tvEmail.text  = email
                tvAvatar.text = name.trim().firstOrNull()?.uppercase() ?: "U"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        rowSettings.setOnClickListener { showSettingsMenu(uid) }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        return view
    }

    private fun showSettingsMenu(uid: String) {
        val ctx = requireContext()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(12))
        }

        data class MenuItem(
            val iconRes: Int, val iconTintRes: Int, val iconBgRes: Int,
            val title: String, val subtitle: String
        )
        val menuItems = listOf(
            MenuItem(R.drawable.ic_person,    R.color.primary, R.color.settings_badge_profile, "Configurações do perfil",   "Editar nome de usuário"),
            MenuItem(R.drawable.ic_palette,   R.color.primary, R.color.settings_badge_display, "Configurações de exibição", "Tema claro, escuro ou sistema"),
            MenuItem(R.drawable.ic_wallet,    R.color.income,  R.color.settings_badge_salary,  "Recorrências",               "Salário, assinaturas e contas fixas")
        )

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Configurações")
            .setView(root)
            .create()

        menuItems.forEachIndexed { index, item ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                isClickable = true
                isFocusable = true
                background = ctx.obtainStyledAttributes(
                    intArrayOf(android.R.attr.selectableItemBackground)
                ).let { ta -> ta.getDrawable(0).also { ta.recycle() } }
                setOnClickListener {
                    dialog.dismiss()
                    when (index) {
                        0 -> showEditProfileDialog()
                        1 -> showDisplaySettingsDialog()
                        2 -> showRecurringItemsDialog(uid)
                    }
                }
            }

            val iconBadge = ImageView(ctx).apply {
                setImageResource(item.iconRes)
                imageTintList = ColorStateList.valueOf(ctx.getColor(item.iconTintRes))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
                layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)).also {
                    it.marginEnd = dpToPx(14)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ctx.getColor(item.iconBgRes))
                }
            }

            val textBlock = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvTitle = TextView(ctx).apply {
                text = item.title
                textSize = 15f
                setTextColor(ctx.getColor(R.color.text_heading))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val tvSub = TextView(ctx).apply {
                text = item.subtitle
                textSize = 12f
                setTextColor(ctx.getColor(R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpToPx(2) }
            }
            textBlock.addView(tvTitle)
            textBlock.addView(tvSub)

            val chevron = TextView(ctx).apply {
                text = "›"
                textSize = 22f
                setTextColor(ctx.getColor(R.color.text_hint))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = dpToPx(8) }
            }

            row.addView(iconBadge)
            row.addView(textBlock)
            row.addView(chevron)
            root.addView(row)

            if (index < menuItems.lastIndex) {
                root.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
                    ).also { it.marginStart = dpToPx(70) }
                    setBackgroundColor(ctx.getColor(R.color.divider))
                })
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.GradientDrawable().apply {
                setColor(ctx.getColor(R.color.bg_card))
                cornerRadius = dpToPx(20).toFloat()
            }
        )
    }

    private fun showEditProfileDialog() {
        val ctx = requireContext()
        val dialogView = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 16)
        }
        val tilName = TextInputLayout(
            ctx, null,
            com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox
        ).apply {
            hint = "Nome de usuario"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBoxCornerRadii(12f, 12f, 12f, 12f)
        }
        val etName = TextInputEditText(ctx).apply { setText(tvName.text) }
        tilName.addView(etName)
        dialogView.addView(tilName)

        AlertDialog.Builder(ctx)
            .setTitle("Configurações do perfil")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName.text?.toString()?.trim() ?: ""
                if (newName.isBlank()) {
                    Toast.makeText(ctx, "Informe um nome.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val email = auth.currentUser?.email ?: tvEmail.text.toString()
                ref.setValue(mapOf("name" to newName, "email" to email))
                    .addOnSuccessListener {
                        tvName.text   = newName
                        tvAvatar.text = newName.firstOrNull()?.uppercase() ?: "U"
                        Toast.makeText(ctx, "Nome atualizado!", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
            .also { d ->
                d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(primaryBlue)
                d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(primaryBlue)
                d.window?.setBackgroundDrawable(
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(ctx.getColor(R.color.bg_card))
                        cornerRadius = dpToPx(20).toFloat()
                    }
                )
            }
    }

    private fun showDisplaySettingsDialog() {
        val ctx = requireContext()
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val options = arrayOf("Claro", "Escuro", "Seguir sistema")
        val checkedItem = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO  -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            else                              -> 2
        }
        AlertDialog.Builder(ctx)
            .setTitle("Configurações de exibição")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val newMode = when (which) {
                    0    -> AppCompatDelegate.MODE_NIGHT_NO
                    1    -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(newMode)
                ctx.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putInt("night_mode", newMode).apply()
                val uid = auth.currentUser?.uid ?: return@setSingleChoiceItems
                FirebaseDatabase.getInstance().reference
                    .child("users").child(uid).child("preferences").child("nightMode")
                    .setValue(newMode)
                dialog.dismiss()
            }
            .setNegativeButton("Fechar", null)
            .show()
            .also { d ->
                d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(primaryBlue)
                d.window?.setBackgroundDrawable(
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(ctx.getColor(R.color.bg_card))
                        cornerRadius = dpToPx(20).toFloat()
                    }
                )
            }
    }

    private fun showRecurringItemsDialog(uid: String) {
        val ctx = requireContext()
        val recurringRepo = RecurringRepository(uid)

        val dialog = android.app.Dialog(ctx)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(24))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ctx.getColor(R.color.bg_card))
                cornerRadius = dpToPx(20).toFloat()
            }
        }

        val tvTitle = TextView(ctx).apply {
            text = "Recorrências"
            textSize = 18f
            setTextColor(ctx.getColor(R.color.text_heading))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(4) }
        }
        root.addView(tvTitle)

        val tvInfo = TextView(ctx).apply {
            text = "Receitas e despesas que se repetem todo mês (salário, assinaturas, aluguel etc.)."
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(20) }
        }
        root.addView(tvInfo)

        val containerEntries = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(containerEntries)

        data class RowRefs(
            val id: String,
            val etTitle: EditText,
            val etCategory: EditText,
            val etAmount: EditText,
            val etDay: EditText,
            val typeGetter: () -> String
        )

        fun addRow(item: RecurringItem? = null) {
            var currentType = item?.type?.takeIf { it == "receita" || it == "despesa" } ?: "receita"
            val rowId = item?.id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(16) }
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(8) }
            }

            val etTitle = EditText(ctx).apply {
                hint = "Título (ex: Salário, Netflix)"
                setText(item?.title ?: "")
                isSingleLine = true
                setHintTextColor(ctx.getColor(R.color.text_hint))
                setTextColor(ctx.getColor(R.color.text_heading))
                background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_outlined_input)
                setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginEnd = dpToPx(8) }
            }

            val btnRemove = ImageButton(ctx).apply {
                setImageDrawable(androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.ic_remove))
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44))
                contentDescription = "Remover"
                setOnClickListener { containerEntries.removeView(row) }
            }

            topRow.addView(etTitle)
            topRow.addView(btnRemove)

            val btnTypeReceita = MaterialButton(ctx).apply {
                text = "Receita"
                textSize = 12f
                cornerRadius = dpToPx(10)
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1f).also { it.marginEnd = dpToPx(6) }
            }
            val btnTypeDespesa = MaterialButton(ctx).apply {
                text = "Despesa"
                textSize = 12f
                cornerRadius = dpToPx(10)
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1f)
            }

            fun paintType() {
                val activeColor  = ctx.getColor(R.color.primary)
                val activeText   = ctx.getColor(R.color.on_primary)
                val inactiveBg   = ctx.getColor(R.color.bg_card_subtle)
                val inactiveText = ctx.getColor(R.color.text_secondary)
                if (currentType == "receita") {
                    btnTypeReceita.backgroundTintList = ColorStateList.valueOf(activeColor)
                    btnTypeReceita.setTextColor(activeText)
                    btnTypeDespesa.backgroundTintList = ColorStateList.valueOf(inactiveBg)
                    btnTypeDespesa.setTextColor(inactiveText)
                } else {
                    btnTypeDespesa.backgroundTintList = ColorStateList.valueOf(activeColor)
                    btnTypeDespesa.setTextColor(activeText)
                    btnTypeReceita.backgroundTintList = ColorStateList.valueOf(inactiveBg)
                    btnTypeReceita.setTextColor(inactiveText)
                }
            }
            paintType()
            btnTypeReceita.setOnClickListener { currentType = "receita"; paintType() }
            btnTypeDespesa.setOnClickListener { currentType = "despesa"; paintType() }

            val typeRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(8) }
            }
            typeRow.addView(btnTypeReceita)
            typeRow.addView(btnTypeDespesa)

            val bottomRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val etCategory = EditText(ctx).apply {
                hint = "Categoria"
                setText(item?.category ?: "")
                isSingleLine = true
                setHintTextColor(ctx.getColor(R.color.text_hint))
                setTextColor(ctx.getColor(R.color.text_heading))
                background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_outlined_input)
                setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                    .also { it.marginEnd = dpToPx(8) }
            }
            val etAmount = EditText(ctx).apply {
                hint = "Valor (R$)"
                setText(if ((item?.amount ?: 0.0) > 0) "%.2f".format(item!!.amount) else "")
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                isSingleLine = true
                setHintTextColor(ctx.getColor(R.color.text_hint))
                setTextColor(ctx.getColor(R.color.text_heading))
                background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_outlined_input)
                setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                    .also { it.marginEnd = dpToPx(8) }
            }
            val etDay = EditText(ctx).apply {
                hint = "Dia"
                setText(if ((item?.dayOfMonth ?: 0) > 0) item!!.dayOfMonth.toString() else "")
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                isSingleLine = true
                gravity = Gravity.CENTER
                setHintTextColor(ctx.getColor(R.color.text_hint))
                setTextColor(ctx.getColor(R.color.text_heading))
                background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_outlined_input)
                setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            bottomRow.addView(etCategory)
            bottomRow.addView(etAmount)
            bottomRow.addView(etDay)

            row.addView(topRow)
            row.addView(typeRow)
            row.addView(bottomRow)
            row.tag = RowRefs(rowId, etTitle, etCategory, etAmount, etDay) { currentType }
            containerEntries.addView(row)
        }

        fun collectEntries(): List<RecurringItem>? {
            val result = mutableListOf<RecurringItem>()
            for (i in 0 until containerEntries.childCount) {
                val refs = containerEntries.getChildAt(i).tag as? RowRefs ?: continue

                val title = refs.etTitle.text.toString().trim()
                if (title.isEmpty()) { refs.etTitle.error = "Informe um título"; return null }
                refs.etTitle.error = null

                val category = refs.etCategory.text.toString().trim()
                if (category.isEmpty()) { refs.etCategory.error = "Informe uma categoria"; return null }
                refs.etCategory.error = null

                val amount = parseAmountPtBr(refs.etAmount.text.toString())
                if (amount == null || amount <= 0) { refs.etAmount.error = "Valor inválido"; return null }
                refs.etAmount.error = null

                val day = refs.etDay.text.toString().trim().toIntOrNull()
                if (day == null || day !in 1..31) { refs.etDay.error = "1 a 31"; return null }
                refs.etDay.error = null

                result.add(
                    RecurringItem(
                        id = refs.id,
                        title = title,
                        type = refs.typeGetter(),
                        amount = amount,
                        category = category,
                        dayOfMonth = day
                    )
                )
            }
            return result
        }

        val btnAddEntry = MaterialButton(ctx).apply {
            text = "+ Adicionar item"
            textSize = 13f
            setBackgroundColor(primaryBlue)
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpToPx(4); it.bottomMargin = dpToPx(20) }
        }
        root.addView(btnAddEntry)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnCancel = MaterialButton(
            ctx, null,
            com.google.android.material.R.attr.borderlessButtonStyle
        ).apply {
            text = "Cancelar"
            setTextColor(0xFF64748B.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnSave = MaterialButton(ctx).apply {
            text = "Salvar"
            setBackgroundColor(primaryBlue)
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = dpToPx(8) }
        }

        btnRow.addView(btnCancel)
        btnRow.addView(btnSave)
        root.addView(btnRow)

        recurringRepo.loadConfig { config ->
            val items = config?.items ?: emptyList()
            containerEntries.removeAllViews()
            items.forEach { addRow(it) }
        }

        btnAddEntry.setOnClickListener { addRow() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val items  = collectEntries() ?: return@setOnClickListener
            val config = RecurringConfig(items = items)
            recurringRepo.saveConfig(config)
                .addOnSuccessListener {
                    Toast.makeText(ctx, "Recorrências salvas!", Toast.LENGTH_SHORT).show()
                    if (items.isNotEmpty()) recurringRepo.checkAndPostIfNeeded(config) { tx ->
                        if (isAdded) Toast.makeText(
                            ctx,
                            "R$ ${"%.2f".format(tx.amount)} lançado para ${tx.date}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(ctx, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        // Frame full-screen transparente — toque fora fecha o dialog. O conteúdo
        // rola dentro de um ScrollView porque a lista de itens recorrentes pode
        // crescer além da altura da tela (ao contrário da antiga entrada única de salário).
        val frame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { dialog.dismiss() }
        }
        val scrollWrapper = ScrollView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val h = dpToPx(24)
            val w = dpToPx(20)
            setPadding(w, h, w, h)
            clipToPadding = false
            setOnClickListener { /* consome o toque para não fechar */ }
        }
        root.layoutParams = ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        )
        scrollWrapper.addView(root)
        frame.addView(scrollWrapper)

        dialog.setContentView(frame)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
        }
        dialog.show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
