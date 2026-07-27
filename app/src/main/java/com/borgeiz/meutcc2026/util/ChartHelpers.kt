package com.borgeiz.meutcc2026.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.borgeiz.meutcc2026.R
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

val chartPaletteHex = listOf(
    "#2563EB", "#16A34A", "#D97706", "#DC2626",
    "#7C3AED", "#0891B2", "#059669", "#B45309",
    "#4F46E5", "#BE185D"
)

fun isDarkMode(context: Context): Boolean {
    return (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
}

fun buildBreakdownRows(
    context: Context,
    isDark: Boolean,
    container: LinearLayout,
    totals: Map<String, Double>,
    total: Double
) {
    container.removeAllViews()
    val ctx = context
    val dp  = ctx.resources.displayMetrics.density

    val trackColor   = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
    val dividerColor = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9")
    val nameColor    = ContextCompat.getColor(ctx, R.color.text_heading)
    val amtColor     = ContextCompat.getColor(ctx, R.color.text_secondary)

    val sorted = totals.entries.sortedByDescending { it.value }

    sorted.forEachIndexed { idx, (label, value) ->
        val pct    = if (total > 0) (value / total * 100.0) else 0.0
        val color  = Color.parseColor(chartPaletteHex[idx % chartPaletteHex.size])
        val pctStr = if (pct < 1.0 && pct > 0.0) "${"%.1f".format(pct)}%" else "${pct.toInt()}%"
        val isLast = idx == sorted.size - 1

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (!isLast) bottomMargin = (4 * dp).toInt()
            }
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dot = View(ctx).apply {
            val size = (10 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (10 * dp).toInt()
                topMargin = (2 * dp).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }

        val tvName = TextView(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(nameColor)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val tvAmt = TextView(ctx).apply {
            text = "R$ ${"%.2f".format(value)}"
            textSize = 13f
            setTextColor(amtColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (10 * dp).toInt() }
        }

        val tvPct = TextView(ctx).apply {
            text = pctStr
            textSize = 12f
            setTextColor(color)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                (40 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (6 * dp).toInt() }
        }

        topRow.addView(dot)
        topRow.addView(tvName)
        topRow.addView(tvAmt)
        topRow.addView(tvPct)

        val track = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (6 * dp).toInt()
            ).apply { topMargin = (8 * dp).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 3 * dp
                setColor(trackColor)
            }
        }

        val fill = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT,
                pct.toFloat().coerceAtLeast(1f)
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 3 * dp
                setColor(color)
            }
        }
        val rest = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT,
                (100.0 - pct).toFloat().coerceAtLeast(0f)
            )
        }
        track.addView(fill)
        track.addView(rest)

        row.addView(topRow)
        row.addView(track)

        if (!isLast) {
            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (1 * dp).toInt()
                ).apply {
                    topMargin    = (14 * dp).toInt()
                    bottomMargin = (14 * dp).toInt()
                }
                setBackgroundColor(dividerColor)
            }
            row.addView(divider)
        }

        container.addView(row)
    }
}

fun setupBreakdownPieChart(
    context: Context,
    isDark: Boolean,
    pieChart: PieChart,
    totals: Map<String, Double>
) {
    val colors = chartPaletteHex.map { Color.parseColor(it) }

    val entries = totals.entries
        .sortedByDescending { it.value }
        .map { (label, value) -> PieEntry(value.toFloat(), label) }

    val dataSet = PieDataSet(entries, "").apply {
        this.colors = colors.take(entries.size).toMutableList()
        valueTextSize = 11f
        valueTextColor = Color.WHITE
        valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value < 1f && value > 0f) "${"%.1f".format(value)}%" else "${value.toInt()}%"
            }
        }
        sliceSpace = 2f
    }

    val holeColor = if (isDark) Color.parseColor("#1E293B") else Color.WHITE

    pieChart.apply {
        data = PieData(dataSet)
        description.isEnabled = false
        isDrawHoleEnabled = true
        holeRadius = 44f
        transparentCircleRadius = 0f
        setHoleColor(holeColor)
        setUsePercentValues(true)
        setEntryLabelColor(Color.TRANSPARENT)
        setDrawEntryLabels(false)
        legend.isEnabled = false
        animateY(800)
        invalidate()
    }
}
