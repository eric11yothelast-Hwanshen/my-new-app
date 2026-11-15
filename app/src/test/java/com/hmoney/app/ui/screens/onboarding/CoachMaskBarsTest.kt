package com.hmoney.app.ui.screens.onboarding

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S51 引導遮罩四條遮罩條 bounds：洞內區域必須零覆蓋（洞內有任何 pointerInput 節點，
 * Compose 獨占命中測試會擋掉底層 FAB／分頁項的點擊），洞外由四條矩形完整攔截。
 */
class CoachMaskBarsTest {

    private val screenW = 1080f
    private val screenH = 2280f

    @Test
    fun `四條遮罩條恰好包住洞且洞內零覆蓋`() {
        val hole = Rect(400f, 1500f, 680f, 1660f)
        val bars = coachMaskBarRects(screenW, screenH, hole)

        assertEquals(4, bars.size)
        val (top, bottom, left, right) = bars

        assertEquals(Rect(0f, 0f, screenW, 1500f), top)
        assertEquals(Rect(0f, 1660f, screenW, screenH), bottom)
        assertEquals(Rect(0f, 1500f, 400f, 1660f), left)
        assertEquals(Rect(680f, 1500f, screenW, 1660f), right)

        val holeArea = hole.width * hole.height
        val covered = bars.sumOf { (it.width * it.height).toDouble() }
        assertEquals((screenW * screenH - holeArea).toDouble(), covered, 1e-3)
    }

    @Test
    fun `洞貼螢幕邊緣時不出現負或零尺寸條`() {
        val hole = Rect(0f, 2000f, screenW, screenH)
        val bars = coachMaskBarRects(screenW, screenH, hole)

        assertEquals(1, bars.size)
        assertEquals(Rect(0f, 0f, screenW, 2000f), bars[0])
        bars.forEach { bar ->
            assertTrue(bar.width > 0f && bar.height > 0f)
            assertTrue(bar.left >= 0f && bar.top >= 0f)
            assertTrue(bar.right <= screenW && bar.bottom <= screenH)
        }
    }

    @Test
    fun `洞超出螢幕時夾回螢幕內`() {
        val hole = Rect(-50f, 1000f, screenW + 50f, 1200f)
        val bars = coachMaskBarRects(screenW, screenH, hole)

        assertEquals(2, bars.size)
        assertEquals(Rect(0f, 0f, screenW, 1000f), bars[0])
        assertEquals(Rect(0f, 1200f, screenW, screenH), bars[1])
    }
}
