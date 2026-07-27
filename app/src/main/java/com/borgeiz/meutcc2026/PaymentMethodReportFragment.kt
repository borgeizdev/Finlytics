package com.borgeiz.meutcc2026

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.borgeiz.meutcc2026.data.TransactionsRepository
import com.borgeiz.meutcc2026.model.Transaction
import com.borgeiz.meutcc2026.util.buildBreakdownRows
import com.borgeiz.meutcc2026.util.isDarkMode
import com.borgeiz.meutcc2026.util.setupBreakdownPieChart
import com.github.mikephil.charting.charts.PieChart
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class PaymentMethodReportFragment : Fragment() {

    private val typeFilterLabels = listOf("Todos", "Receitas", "Despesas")

    private var txRepoRef: TransactionsRepository? = null
    private var txListenerRef: ValueEventListener? = null

    override fun onDestroyView() {
        super.onDestroyView()
        txRepoRef?.let { repo -> txListenerRef?.let { repo.removeObserver(it) } }
        txRepoRef = null
        txListenerRef = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_payment_method_report, container, false)

        val etStartDate  = view.findViewById<TextInputEditText>(R.id.etStartDate)
        val etEndDate    = view.findViewById<TextInputEditText>(R.id.etEndDate)
        val spTypeFilter = view.findViewById<Spinner>(R.id.spTypeFilter)
        val tvTotal      = view.findViewById<TextView>(R.id.tvPeriodTotal)
        val pieChart     = view.findViewById<PieChart>(R.id.pieChartPaymentMethod)
        val llBreakdown  = view.findViewById<LinearLayout>(R.id.llPaymentBreakdown)

        pieChart.setNoDataText("")

        spTypeFilter.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            typeFilterLabels
        )

        val today = Calendar.getInstance()
        etEndDate.setText(
            "%d-%02d-%02d".format(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))
        )
        today.set(Calendar.DAY_OF_MONTH, 1)
        etStartDate.setText(
            "%d-%02d-%02d".format(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))
        )

        var allTransactions = listOf<Transaction>()

        fun refresh() {
            val start = etStartDate.text?.toString()?.trim().orEmpty()
            val end   = etEndDate.text?.toString()?.trim().orEmpty()
            val typeFilter = when (spTypeFilter.selectedItemPosition) {
                1 -> "receita"
                2 -> "despesa"
                else -> null
            }

            val filtered = allTransactions.filter { t ->
                (typeFilter == null || t.type == typeFilter) &&
                    (start.isBlank() || t.date >= start) &&
                    (end.isBlank() || t.date <= end)
            }

            val totals = mutableMapOf<String, Double>()
            var total = 0.0
            for (t in filtered) {
                total += t.amount
                val method = t.paymentMethod.ifBlank { "Não informado" }
                totals[method] = (totals[method] ?: 0.0) + t.amount
            }

            tvTotal.text = "R$ %.2f".format(total)

            if (totals.isEmpty()) {
                pieChart.visibility = View.GONE
                llBreakdown.removeAllViews()
                llBreakdown.addView(TextView(requireContext()).apply {
                    text = "Nenhuma transação neste período."
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                    textSize = 14f
                })
            } else {
                pieChart.visibility = View.VISIBLE
                val isDark = isDarkMode(requireContext())
                setupBreakdownPieChart(requireContext(), isDark, pieChart, totals)
                buildBreakdownRows(requireContext(), isDark, llBreakdown, totals, total)
            }
        }

        fun pickDate(field: TextInputEditText) {
            val parts = field.text?.toString()?.split("-")
            val cal = Calendar.getInstance()
            if (parts != null && parts.size == 3) {
                parts[0].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
                parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
                parts[2].toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
            }
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    field.setText("%d-%02d-%02d".format(y, m + 1, d))
                    refresh()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        etStartDate.setOnClickListener { pickDate(etStartDate) }
        etEndDate.setOnClickListener { pickDate(etEndDate) }

        spTypeFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return view
        val txRepo = TransactionsRepository(uid)
        txRepoRef = txRepo
        txListenerRef = txRepo.observe { transactions ->
            if (!isAdded) return@observe
            allTransactions = transactions
            refresh()
        }

        return view
    }
}
