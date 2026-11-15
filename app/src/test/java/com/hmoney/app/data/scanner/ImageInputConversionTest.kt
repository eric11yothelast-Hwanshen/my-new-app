package com.hmoney.app.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CameraX ImageProxy → ML Kit InputImage 換算純函式測試（S40）：
 * 旋轉角正規化（負值／超過 360）與 90／270 度時寬高對調。
 */
class ImageInputConversionTest {

    @Test
    fun `旋轉角正規化至 0 到 359`() {
        assertEquals(0, resolveImageRotation(0))
        assertEquals(90, resolveImageRotation(90))
        assertEquals(270, resolveImageRotation(-90))
        assertEquals(45, resolveImageRotation(405))
        assertEquals(180, resolveImageRotation(-180))
    }

    @Test
    fun `旋轉 90 或 270 度時寬高對調`() {
        assertEquals(1080 to 1920, resolveDisplayedSize(1920, 1080, 90))
        assertEquals(1080 to 1920, resolveDisplayedSize(1920, 1080, 270))
    }

    @Test
    fun `旋轉 0 或 180 度時寬高不變`() {
        assertEquals(1920 to 1080, resolveDisplayedSize(1920, 1080, 0))
        assertEquals(1920 to 1080, resolveDisplayedSize(1920, 1080, 180))
    }

    @Test
    fun `InputImage 規格組合正確`() {
        val spec = toImageInputSpec(1920, 1080, -90)
        assertEquals(270, spec.rotationDegrees)
        assertEquals(1080, spec.width)
        assertEquals(1920, spec.height)
    }
}
