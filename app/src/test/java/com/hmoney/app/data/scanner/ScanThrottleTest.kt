package com.hmoney.app.data.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 條碼掃描節流與去重純函式測試（S41）：
 * 450ms 間隔內不重複辨識；同一條碼碼值去重；首次掃描允許。
 */
class ScanThrottleTest {

    @Test
    fun `節流間隔內不允許新幀超過間隔才允許`() {
        assertFalse(shouldScanAt(lastScanEpochMillis = 1000L, nowEpochMillis = 1200L))
        assertTrue(shouldScanAt(lastScanEpochMillis = 1000L, nowEpochMillis = 1451L))
    }

    @Test
    fun `首次掃描一律允許`() {
        assertTrue(shouldScanAt(lastScanEpochMillis = 0L, nowEpochMillis = 10L))
    }

    @Test
    fun `同一條碼去重不同條碼允許`() {
        assertTrue(isSameBarcode("4711234567890", "4711234567890"))
        assertFalse(isSameBarcode("4711234567890", "4719876543210"))
        assertFalse(isSameBarcode(null, "4711234567890"))
    }
}
