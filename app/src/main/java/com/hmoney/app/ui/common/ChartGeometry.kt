package com.hmoney.app.ui.common

/**
 * S48 迷你折線圖座標純函式（JVM 單元測試直接驗證）：
 * 把數值序列映射到 [padding, width - padding] × [padding, height - padding] 的繪圖座標——
 * x 依索引均分、y 依最小／最大值正規化後反轉（值越大 y 越小）。
 * 空序列回空清單；單點置中；min == max（含全同值）時 y 置中，避免除以零。
 */
data class LineChartPoint(val x: Float, val y: Float)

fun lineChartPoints(
    values: List<Long>,
    width: Float,
    height: Float,
    padding: Float = 0f,
): List<LineChartPoint> {
    if (values.isEmpty()) return emptyList()
    if (width <= 0f || height <= 0f) return emptyList()
    val min = padding
    val maxX = width - padding
    val maxY = height - padding
    if (values.size == 1) {
        return listOf(LineChartPoint((min + maxX) / 2f, (min + maxY) / 2f))
    }
    val lo = values.min()
    val hi = values.max()
    val spanX = maxX - min
    val step = spanX / (values.size - 1).toFloat()
    return values.mapIndexed { index, value ->
        val x = min + index * step
        val y = if (hi == lo) {
            (min + maxY) / 2f
        } else {
            maxY - (value - lo).toFloat() / (hi - lo).toFloat() * (maxY - min)
        }
        LineChartPoint(x, y)
    }
}
