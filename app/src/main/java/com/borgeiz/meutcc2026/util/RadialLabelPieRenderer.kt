package com.borgeiz.meutcc2026.util

import android.graphics.Canvas
import android.graphics.Paint
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.renderer.PieChartRenderer
import kotlin.math.cos
import kotlin.math.sin

class RadialLabelPieRenderer(
    chart: PieChart,
    private val labelColor: Int,
    private val density: Float
) : PieChartRenderer(chart, chart.animator, chart.viewPortHandler) {

    override fun drawValues(c: Canvas) {
        val data = mChart.data ?: return
        val dataSet = data.dataSet ?: return
        val total = data.yValueSum
        if (total <= 0f) return

        val center = mChart.centerCircleBox
        val radius = mChart.radius
        val rotation = mChart.rotationAngle
        val drawAngles = mChart.drawAngles
        val absoluteAngles = mChart.absoluteAngles
        val phaseY = mAnimator.phaseY

        val lineStart = radius + 2 * density
        val lineEnd = radius + 8 * density
        val textRadius = radius + 11 * density

        val textPaint = mValuePaint.apply {
            color = labelColor
            textSize = dataSet.valueTextSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1.2f * density
            style = Paint.Style.STROKE
        }
        val fm = textPaint.fontMetrics
        val textHeight = -fm.ascent

        for (i in 0 until dataSet.entryCount) {
            val entry = dataSet.getEntryForIndex(i)
            if (entry.y <= 0f) continue

            val sliceStart = if (i == 0) 0f else absoluteAngles[i - 1]
            val midDeg = rotation + (sliceStart + drawAngles[i] / 2f) * phaseY
            val rad = Math.toRadians(midDeg.toDouble())
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()

            linePaint.color = dataSet.getColor(i)
            c.drawLine(
                center.x + lineStart * dx, center.y + lineStart * dy,
                center.x + lineEnd * dx, center.y + lineEnd * dy,
                linePaint
            )

            val x = center.x + textRadius * dx
            val y = center.y + textRadius * dy

            textPaint.textAlign = when {
                dx > 0.35f -> Paint.Align.LEFT
                dx < -0.35f -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val baseline = when {
                dy > 0.35f -> y + textHeight
                dy < -0.35f -> y
                else -> y + textHeight / 2f
            }

            val percent = entry.y / total * 100f
            val text = dataSet.valueFormatter.getFormattedValue(percent)
            c.drawText(text, x, baseline, textPaint)
        }
    }
}
