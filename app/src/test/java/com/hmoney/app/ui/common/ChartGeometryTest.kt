package com.hmoney.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S48 迷你折線圖座標純函式（折線點座標抽純函式可測試） */
class ChartGeometryTest {

    @Test
    fun `空序列回空清單`() {
        assertTrue(lineChartPoints(emptyList(), 100f, 50f).isEmpty())
    }

    @Test
    fun `單點置中`() {
        val points = lineChartPoints(listOf(10L), 100f, 50f)
        assertEquals(1, points.size)
        assertEquals(50f, points[0].x, 0.001f)
        assertEquals(25f, points[0].y, 0.001f)
    }

    @Test
    fun `x 依索引均分且 y 正規化反轉`() {
        val points = lineChartPoints(listOf(0L, 50L, 100L), 100f, 50f)
        assertEquals(3, points.size)
        assertEquals(0f, points[0].x, 0.001f)
        assertEquals(50f, points[0].y, 0.001f)
        assertEquals(50f, points[1].x, 0.001f)
        assertEquals(25f, points[1].y, 0.001f)
        assertEquals(100f, points[2].x, 0.001f)
        assertEquals(0f, points[2].y, 0.001f)
    }

    @Test
    fun `padding 內縮且全同值 y 置中`() {
        val points = lineChartPoints(listOf(7L, 7L, 7L, 7L), 100f, 50f, padding = 10f)
        assertEquals(4, points.size)
        assertEquals(10f, points[0].x, 0.001f)
        assertEquals(90f, points[3].x, 0.001f)
        points.forEach {
            assertEquals(25f, it.y, 0.001f)
        }
    }

    @Test
    fun `非正尺寸回空清單`() {
        assertTrue(lineChartPoints(listOf(1L, 2L), 0f, 50f).isEmpty())
        assertTrue(lineChartPoints(listOf(1L, 2L), 100f, -1f).isEmpty())
    }
}
