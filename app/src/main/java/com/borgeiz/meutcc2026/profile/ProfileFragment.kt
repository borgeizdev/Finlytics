package com.borgeiz.meutcc2026.profile

import com.borgeiz.meutcc2026.R
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
import com.borgeiz.meutcc2026.auth.LoginActivity
import com.borgeiz.meutcc2026.data.CategoryRepository
import com.borgeiz.meutcc2026.data.GoalRepository
import com.borgeiz.meutcc2026.data.RecurringRepository
import com.borgeiz.meutcc2026.model.CategoryConfig
import com.borgeiz.meutcc2026.model.Goal
import com.borgeiz.meutcc2026.model.GoalConfig
import com.borgeiz.meutcc2026.model.RecurringConfig
import com.borgeiz.meutcc2026.model.RecurringItem
import com.borgeiz.meutcc2026.util.parseAmountPtBr
import com.borgeiz.meutcc2026.util.formatAmountInputPtBr
import com.borgeiz.meutcc2026.util.formatMoneyPtBr
import com.borgeiz.meutcc2026.util.setupMoneyInputPtBr
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
            MenuItem(R.drawable.ic_wallet,    R.color.income,  R.color.settings_badge_salary,  "Recorrências",               "Salário, assinaturas e contas fixas"),
            MenuItem(R.drawable.ic_tag,       R.color.primary, R.color.settings_badge_category, "Categorias",                "Categorias de receita e despesa"),
            MenuItem(R.drawable.ic_bar_chart, R.color.primary, R.color.settings_badge_goal,     "Metas",                     "Limites de gasto mensais")
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
                        3 -> showCategoriesDialog(uid)
                        4 -> showGoalsDialog(uid)
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

        val dialog = android.app.Dialog(ctx)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ctx.getColor(R.color.bg_card))
                cornerRadius = dpToPx(20).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER }
            setOnClickListener { /* consome o toque para não fechar */ }
        }

        // Card pequeno e de altura fixa (duas opções) — sem lista rolável aqui,
        // então basta centralizar na tela, sem o problema de esticar visto no
        // dialog de edição (showRecurringTypeDialog), que usa ScrollView.
        val frame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val h = dpToPx(24)
            val w = dpToPx(20)
            setPadding(w, h, w, h)
            clipToPadding = false
            setOnClickListener { dialog.dismiss() }
        }
        frame.addView(root)

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
            text = "O que você quer configurar?"
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(16) }
        }
        root.addView(tvInfo)

        data class TypeOption(
            val type: String, val iconRes: Int, val iconTintRes: Int, val iconBgRes: Int,
            val title: String, val subtitle: String
        )
        val options = listOf(
            TypeOption(
                "receita", R.drawable.ic_trending_up, R.color.income, R.color.settings_badge_salary,
                "Receitas", "Salário, bônus e outras entradas fixas"
            ),
            TypeOption(
                "despesa", R.drawable.ic_trending_down, R.color.expense, R.color.settings_badge_category,
                "Despesas", "Assinaturas, aluguel e contas fixas"
            )
        )

        options.forEachIndexed { index, option ->
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
                    showRecurringTypeDialog(uid, option.type)
                }
            }

            val iconBadge = ImageView(ctx).apply {
                setImageResource(option.iconRes)
                imageTintList = ColorStateList.valueOf(ctx.getColor(option.iconTintRes))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
                layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)).also {
                    it.marginEnd = dpToPx(14)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ctx.getColor(option.iconBgRes))
                }
            }

            val textBlock = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textBlock.addView(TextView(ctx).apply {
                text = option.title
                textSize = 15f
                setTextColor(ctx.getColor(R.color.text_heading))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            textBlock.addView(TextView(ctx).apply {
                text = option.subtitle
                textSize = 12f
                setTextColor(ctx.getColor(R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dpToPx(2) }
            })

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

            if (index < options.lastIndex) {
                root.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
                    ).also { it.marginStart = dpToPx(70) }
                    setBackgroundColor(ctx.getColor(R.color.divider))
                })
            }
        }

        dialog.setContentView(frame)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
        }
        dialog.show()
    }

    private fun showRecurringTypeDialog(uid: String, type: String) {
        val ctx = requireContext()
        val recurringRepo = RecurringRepository(uid)
        val categoryRepo = CategoryRepository(uid)
        val typeLabel = if (type == "receita") "Receitas" else "Despesas"

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

        // Frame full-screen transparente — toque fora fecha o dialog. "root" só é
        // anexado a ele depois de sabermos (após carregar os dados) se cabe
        // centralizado ou se precisa rolar do topo — dialog.show() já roda na hora
        // (sem esperar a rede) só com o frame vazio; o card aparece já no lugar
        // certo, sem "pulo" nem sensação de lentidão. Antes isso usava um truque
        // com ScrollView.isFillViewport que não centralizava de forma confiável.
        val frame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { dialog.dismiss() }
        }

        val hPad = dpToPx(24)
        val wPad = dpToPx(20)

        // Decide pelo tamanho REAL do conteúdo já montado (não por "tem item ou não"):
        // mede "root" com a largura disponível e compara com a altura da tela. Se
        // couber, centraliza sem ScrollView; se não couber, ancora no topo e rola.
        fun attachRoot() {
            val maxWidth = resources.displayMetrics.widthPixels - 2 * wPad
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(maxWidth, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            val availableHeight = resources.displayMetrics.heightPixels - 2 * hPad
            if (root.measuredHeight <= availableHeight) {
                root.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.gravity = Gravity.CENTER
                    it.leftMargin = wPad
                    it.rightMargin = wPad
                }
                frame.addView(root)
            } else {
                val scrollWrapper = ScrollView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setPadding(wPad, hPad, wPad, hPad)
                    clipToPadding = false
                    setOnClickListener { /* consome o toque para não fechar */ }
                }
                root.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                scrollWrapper.addView(root)
                frame.addView(scrollWrapper)
            }
        }

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

        val tvTitle = TextView(ctx).apply {
            text = typeLabel
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
            text = if (type == "receita")
                "Receitas que se repetem todo mês (salário, bônus, extras)."
            else
                "Despesas que se repetem todo mês (assinaturas, aluguel, contas fixas)."
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(20) }
        }
        root.addView(tvInfo)

        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(container)

        data class RowRefs(
            val id: String,
            val etTitle: EditText,
            val etCategory: AutoCompleteTextView,
            val etAmount: EditText,
            val etDay: EditText
        )

        // Categorias salvas em Perfil > Configurações > Categorias, carregadas de forma
        // assíncrona logo abaixo; cada linha usa esta lista para popular seu seletor.
        var categoryOptions = listOf<String>()

        fun categoryAdapter() = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, categoryOptions)

        fun refreshCategoryAdapters() {
            for (i in 0 until container.childCount) {
                val refs = container.getChildAt(i).tag as? RowRefs ?: continue
                refs.etCategory.setAdapter(categoryAdapter())
            }
        }

        fun addRow(item: RecurringItem? = null) {
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
                setOnClickListener { container.removeView(row) }
            }

            topRow.addView(etTitle)
            topRow.addView(btnRemove)

            val bottomRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val etCategory = AutoCompleteTextView(ctx).apply {
                hint = "Categoria"
                setText(item?.category ?: "", false)
                inputType = android.text.InputType.TYPE_NULL
                isSingleLine = true
                setHintTextColor(ctx.getColor(R.color.text_hint))
                setTextColor(ctx.getColor(R.color.text_heading))
                background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_outlined_input)
                setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                    .also { it.marginEnd = dpToPx(8) }
                setAdapter(categoryAdapter())
                setOnClickListener { showDropDown() }
                // No primeiro toque o campo só ganha foco (o clique em si não abre o
                // menu); sem isto o usuário precisa tocar duas vezes na primeira vez.
                setOnFocusChangeListener { v, hasFocus -> if (hasFocus) (v as AutoCompleteTextView).showDropDown() }
            }
            val etAmount = EditText(ctx).apply {
                hint = "Valor (R$)"
                setupMoneyInputPtBr()
                setText(if ((item?.amount ?: 0.0) > 0) formatAmountInputPtBr(item!!.amount) else "")
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
            row.addView(bottomRow)
            row.tag = RowRefs(rowId, etTitle, etCategory, etAmount, etDay)
            container.addView(row)
        }

        fun collectEntries(): List<RecurringItem>? {
            val result = mutableListOf<RecurringItem>()
            for (i in 0 until container.childCount) {
                val refs = container.getChildAt(i).tag as? RowRefs ?: continue

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
                        type = type,
                        amount = amount,
                        category = category,
                        dayOfMonth = day
                    )
                )
            }
            return result
        }

        val btnAddEntry = MaterialButton(ctx).apply {
            text = if (type == "receita") "+ Adicionar receita" else "+ Adicionar despesa"
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

        // Guarda os itens do outro tipo (não editados nesta tela) para não perdê-los ao salvar.
        var otherTypeItems = listOf<RecurringItem>()

        categoryRepo.loadConfig { catConfig ->
            if (!isAdded) return@loadConfig
            categoryOptions = if (type == "receita") catConfig.income else catConfig.expense
            refreshCategoryAdapters()
        }

        recurringRepo.loadConfig { config ->
            if (!isAdded) return@loadConfig
            val items = config?.items ?: emptyList()
            otherTypeItems = items.filter { it.type != type }
            val typeItems = items.filter { it.type == type }
            container.removeAllViews()
            typeItems.forEach { addRow(it) }
            attachRoot()
        }

        btnAddEntry.setOnClickListener { addRow() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val items  = collectEntries() ?: return@setOnClickListener
            val config = RecurringConfig(items = otherTypeItems + items)
            recurringRepo.saveConfig(config)
                .addOnSuccessListener {
                    Toast.makeText(ctx, "Recorrências salvas!", Toast.LENGTH_SHORT).show()
                    if (config.items.isNotEmpty()) recurringRepo.checkAndPostIfNeeded(config) { tx ->
                        if (isAdded) Toast.makeText(
                            ctx,
                            "${formatMoneyPtBr(tx.amount)} lançado para ${tx.date}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(ctx, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showCategoriesDialog(uid: String) {
        val ctx = requireContext()
        val categoryRepo = CategoryRepository(uid)

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

        // Frame full-screen transparente — toque fora fecha o dialog. "root" só é
        // anexado a ele depois de sabermos (após carregar os dados) se cabe
        // centralizado ou se precisa rolar do topo — dialog.show() já roda na hora
        // (sem esperar a rede) só com o frame vazio; o card aparece já no lugar
        // certo, sem "pulo" nem sensação de lentidão. Antes isso usava um truque
        // com ScrollView.isFillViewport que não centralizava de forma confiável.
        val frame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { dialog.dismiss() }
        }

        val hPad = dpToPx(24)
        val wPad = dpToPx(20)

        // Decide pelo tamanho REAL do conteúdo já montado (não por "tem item ou não"):
        // mede "root" com a largura disponível e compara com a altura da tela. Se
        // couber, centraliza sem ScrollView; se não couber, ancora no topo e rola.
        fun attachRoot() {
            val maxWidth = resources.displayMetrics.widthPixels - 2 * wPad
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(maxWidth, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            val availableHeight = resources.displayMetrics.heightPixels - 2 * hPad
            if (root.measuredHeight <= availableHeight) {
                root.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.gravity = Gravity.CENTER
                    it.leftMargin = wPad
                    it.rightMargin = wPad
                }
                frame.addView(root)
            } else {
                val scrollWrapper = ScrollView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setPadding(wPad, hPad, wPad, hPad)
                    clipToPadding = false
                    setOnClickListener { /* consome o toque para não fechar */ }
                }
                root.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                scrollWrapper.addView(root)
                frame.addView(scrollWrapper)
            }
        }

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

        val tvTitle = TextView(ctx).apply {
            text = "Categorias"
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
            text = "Categorias usadas nos formulários de receita e despesa."
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(20) }
        }
        root.addView(tvInfo)

        // Abas Receita / Despesa: a aba ativa decide em qual lista (income/expense)
        // as categorias adicionadas entram.
        var currentTab = "receita"

        val btnTabReceita = MaterialButton(ctx).apply {
            text = "Receita"
            textSize = 13f
            cornerRadius = dpToPx(10)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(42), 1f).also { it.marginEnd = dpToPx(6) }
        }
        val btnTabDespesa = MaterialButton(ctx).apply {
            text = "Despesa"
            textSize = 13f
            cornerRadius = dpToPx(10)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(42), 1f)
        }
        val tabRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(16) }
        }
        tabRow.addView(btnTabReceita)
        tabRow.addView(btnTabDespesa)
        root.addView(tabRow)

        val containerIncome = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val containerExpense = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(containerIncome)
        root.addView(containerExpense)

        fun paintTabs() {
            val activeColor  = ctx.getColor(R.color.primary)
            val activeText   = ctx.getColor(R.color.on_primary)
            val inactiveBg   = ctx.getColor(R.color.bg_card_subtle)
            val inactiveText = ctx.getColor(R.color.text_secondary)
            if (currentTab == "receita") {
                btnTabReceita.backgroundTintList = ColorStateList.valueOf(activeColor)
                btnTabReceita.setTextColor(activeText)
                btnTabDespesa.backgroundTintList = ColorStateList.valueOf(inactiveBg)
                btnTabDespesa.setTextColor(inactiveText)
                containerIncome.visibility = View.VISIBLE
                containerExpense.visibility = View.GONE
            } else {
                btnTabDespesa.backgroundTintList = ColorStateList.valueOf(activeColor)
                btnTabDespesa.setTextColor(activeText)
                btnTabReceita.backgroundTintList = ColorStateList.valueOf(inactiveBg)
                btnTabReceita.setTextColor(inactiveText)
                containerIncome.visibility = View.GONE
                containerExpense.visibility = View.VISIBLE
            }
        }
        paintTabs()
        btnTabReceita.setOnClickListener { currentTab = "receita"; paintTabs() }
        btnTabDespesa.setOnClickListener { currentTab = "despesa"; paintTabs() }

        data class CategoryRowRefs(val etName: EditText)

        fun addRow(type: String, name: String = "") {
            val container = if (type == "receita") containerIncome else containerExpense

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(12) }
            }

            val etName = EditText(ctx).apply {
                hint = "Nome da categoria"
                setText(name)
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
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
                contentDescription = "Remover"
                setOnClickListener { container.removeView(row) }
            }

            row.addView(etName)
            row.addView(btnRemove)
            row.tag = CategoryRowRefs(etName)
            container.addView(row)
        }

        fun collectFrom(container: LinearLayout, result: MutableList<String>): Boolean {
            for (i in 0 until container.childCount) {
                val refs = container.getChildAt(i).tag as? CategoryRowRefs ?: continue
                val name = refs.etName.text.toString().trim()
                if (name.isEmpty()) { refs.etName.error = "Informe um nome"; return false }
                refs.etName.error = null
                result.add(name)
            }
            return true
        }

        fun collectCategories(): CategoryConfig? {
            val income = mutableListOf<String>()
            val expense = mutableListOf<String>()
            if (!collectFrom(containerIncome, income)) return null
            if (!collectFrom(containerExpense, expense)) return null
            return CategoryConfig(
                income = income.distinctBy { it.lowercase() },
                expense = expense.distinctBy { it.lowercase() }
            )
        }

        val btnAddEntry = MaterialButton(ctx).apply {
            text = "+ Adicionar categoria"
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

        categoryRepo.loadConfig { config ->
            if (!isAdded) return@loadConfig
            containerIncome.removeAllViews()
            containerExpense.removeAllViews()
            config.income.forEach { addRow("receita", it) }
            config.expense.forEach { addRow("despesa", it) }
            attachRoot()
        }

        btnAddEntry.setOnClickListener { addRow(currentTab) }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val config = collectCategories() ?: return@setOnClickListener
            categoryRepo.saveConfig(config)
                .addOnSuccessListener {
                    Toast.makeText(ctx, "Categorias salvas!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(ctx, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showGoalsDialog(uid: String) {
        val ctx = requireContext()
        val goalRepo = GoalRepository(uid)
        val categoryRepo = CategoryRepository(uid)

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

        // Frame full-screen transparente — toque fora fecha o dialog. "root" só é
        // anexado a ele depois de sabermos (após carregar os dados) se cabe
        // centralizado ou se precisa rolar do topo — dialog.show() já roda na hora
        // (sem esperar a rede) só com o frame vazio; o card aparece já no lugar
        // certo, sem "pulo" nem sensação de lentidão. Antes isso usava um truque
        // com ScrollView.isFillViewport que não centralizava de forma confiável.
        val frame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { dialog.dismiss() }
        }

        val hPad = dpToPx(24)
        val wPad = dpToPx(20)

        // Decide pelo tamanho REAL do conteúdo já montado (não por "tem item ou não"):
        // mede "root" com a largura disponível e compara com a altura da tela. Se
        // couber, centraliza sem ScrollView; se não couber, ancora no topo e rola.
        fun attachRoot() {
            val maxWidth = resources.displayMetrics.widthPixels - 2 * wPad
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(maxWidth, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            val availableHeight = resources.displayMetrics.heightPixels - 2 * hPad
            if (root.measuredHeight <= availableHeight) {
                root.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.gravity = Gravity.CENTER
                    it.leftMargin = wPad
                    it.rightMargin = wPad
                }
                frame.addView(root)
            } else {
                val scrollWrapper = ScrollView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setPadding(wPad, hPad, wPad, hPad)
                    clipToPadding = false
                    setOnClickListener { /* consome o toque para não fechar */ }
                }
                root.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                scrollWrapper.addView(root)
                frame.addView(scrollWrapper)
            }
        }

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

        val tvTitle = TextView(ctx).apply {
            text = "Metas"
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
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(16) }
        }
        root.addView(tvInfo)

        // Abas: "Limite de gasto" (meta tradicional, geral ou por categoria) e
        // "Lucro mensal" (receita menos despesa do mês >= valor alvo). Cada aba
        // guarda sua própria lista de metas em memória até salvar.
        var currentTab = "gasto"

        val btnTabGasto = MaterialButton(ctx).apply {
            text = "Limite de gasto"
            textSize = 12f
            isAllCaps = false
            cornerRadius = dpToPx(10)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(42), 1f).also { it.marginEnd = dpToPx(6) }
        }
        val btnTabLucro = MaterialButton(ctx).apply {
            text = "Lucro mensal"
            textSize = 12f
            isAllCaps = false
            cornerRadius = dpToPx(10)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(42), 1f)
        }
        val tabRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(16) }
        }
        tabRow.addView(btnTabGasto)
        tabRow.addView(btnTabLucro)
        root.addView(tabRow)

        val containerGasto = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val containerLucro = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(containerGasto)
        root.addView(containerLucro)

        fun paintTabs() {
            val activeColor  = ctx.getColor(R.color.primary)
            val activeText   = ctx.getColor(R.color.on_primary)
            val inactiveBg   = ctx.getColor(R.color.bg_card_subtle)
            val inactiveText = ctx.getColor(R.color.text_secondary)
            if (currentTab == "gasto") {
                btnTabGasto.backgroundTintList = ColorStateList.valueOf(activeColor)
                btnTabGasto.setTextColor(activeText)
                btnTabLucro.backgroundTintList = ColorStateList.valueOf(inactiveBg)
                btnTabLucro.setTextColor(inactiveText)
                containerGasto.visibility = View.VISIBLE
                containerLucro.visibility = View.GONE
                tvInfo.text = "Defina um limite de gasto mensal, geral ou por categoria. O progresso mostra o quanto já foi gasto em relação a esse limite — passar de 100% significa que a meta estourou."
            } else {
                btnTabLucro.backgroundTintList = ColorStateList.valueOf(activeColor)
                btnTabLucro.setTextColor(activeText)
                btnTabGasto.backgroundTintList = ColorStateList.valueOf(inactiveBg)
                btnTabGasto.setTextColor(inactiveText)
                containerGasto.visibility = View.GONE
                containerLucro.visibility = View.VISIBLE
                tvInfo.text = "Defina quanto você quer que sobre no mês (receitas menos despesas). O progresso mostra o quanto desse lucro-alvo já foi alcançado — chegar a 100% significa que a meta foi batida."
            }
        }
        paintTabs()
        btnTabGasto.setOnClickListener { currentTab = "gasto"; paintTabs() }
        btnTabLucro.setOnClickListener { currentTab = "lucro"; paintTabs() }

        data class GoalRowRefs(
            val id: String,
            val spCategory: Spinner?,
            val etAmount: EditText,
            val categoryValues: List<String>
        )

        var expenseCategories = listOf<String>()

        fun addGastoRow(goal: Goal? = null) {
            val rowId = goal?.id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(12) }
            }

            val categoryLabels = listOf("Geral (todas as despesas)") + expenseCategories
            val categoryValues = listOf("") + expenseCategories

            val spCategory = Spinner(ctx).apply {
                adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, categoryLabels)
                background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_spinner)
                setPadding(dpToPx(10), 0, dpToPx(10), 0)
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(44), 1f).also { it.marginEnd = dpToPx(8) }
            }
            val selIdx = categoryValues.indexOf(goal?.category ?: "").let { if (it >= 0) it else 0 }
            spCategory.setSelection(selIdx)

            val etAmount = EditText(ctx).apply {
                hint = "Valor alvo (R$)"
                setupMoneyInputPtBr()
                setText(if ((goal?.targetAmount ?: 0.0) > 0) formatAmountInputPtBr(goal!!.targetAmount) else "")
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
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
                contentDescription = "Remover"
                setOnClickListener { containerGasto.removeView(row) }
            }

            row.addView(spCategory)
            row.addView(etAmount)
            row.addView(btnRemove)
            row.tag = GoalRowRefs(rowId, spCategory, etAmount, categoryValues)
            containerGasto.addView(row)
        }

        fun addLucroRow(goal: Goal? = null) {
            val rowId = goal?.id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(12) }
            }

            val etAmount = EditText(ctx).apply {
                hint = "Lucro alvo no mês (R$)"
                setupMoneyInputPtBr()
                setText(if ((goal?.targetAmount ?: 0.0) > 0) formatAmountInputPtBr(goal!!.targetAmount) else "")
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
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
                contentDescription = "Remover"
                setOnClickListener { containerLucro.removeView(row) }
            }

            row.addView(etAmount)
            row.addView(btnRemove)
            row.tag = GoalRowRefs(rowId, null, etAmount, emptyList())
            containerLucro.addView(row)
        }

        fun collectFrom(container: LinearLayout, type: String, result: MutableList<Goal>): Boolean {
            for (i in 0 until container.childCount) {
                val refs = container.getChildAt(i).tag as? GoalRowRefs ?: continue
                val amount = parseAmountPtBr(refs.etAmount.text.toString())
                if (amount == null || amount <= 0.0) { refs.etAmount.error = "Valor inválido"; return false }
                refs.etAmount.error = null
                val category = refs.spCategory?.let { sp -> refs.categoryValues.getOrElse(sp.selectedItemPosition) { "" } } ?: ""
                result.add(Goal(id = refs.id, category = category, targetAmount = amount, period = "mensal", type = type))
            }
            return true
        }

        fun collectGoals(): List<Goal>? {
            val result = mutableListOf<Goal>()
            if (!collectFrom(containerGasto, "gasto", result)) return null
            if (!collectFrom(containerLucro, "lucro", result)) return null
            return result
        }

        val btnAddEntry = MaterialButton(ctx).apply {
            text = "+ Adicionar meta"
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

        categoryRepo.loadConfig { catConfig ->
            if (!isAdded) return@loadConfig
            expenseCategories = catConfig.expense
            goalRepo.loadConfig { goalConfig ->
                if (!isAdded) return@loadConfig
                containerGasto.removeAllViews()
                containerLucro.removeAllViews()
                goalConfig.items.forEach { goal ->
                    if (goal.type == "lucro") addLucroRow(goal) else addGastoRow(goal)
                }
                attachRoot()
            }
        }

        btnAddEntry.setOnClickListener { if (currentTab == "gasto") addGastoRow() else addLucroRow() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val goals = collectGoals() ?: return@setOnClickListener
            goalRepo.saveConfig(GoalConfig(items = goals))
                .addOnSuccessListener {
                    Toast.makeText(ctx, "Metas salvas!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(ctx, "Erro: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
