package com.hmoney.app.data.scanner

/**
 * 條碼掃描節流與去重（S41 純函式，可單元測試）：
 * ML Kit client 為重用欄位（MlKitBarcodeScannerSource @Singleton），
 * 分析幀以 450ms 間節流，同一條碼碼值去重避免連續重複觸發。
 */
const val SCAN_THROTTLE_MS = 450L

/** 距上次掃描超過節流間隔才允許新的一幀進入辨識 */
fun shouldScanAt(lastScanEpochMillis: Long, nowEpochMillis: Long, throttleMs: Long = SCAN_THROTTLE_MS): Boolean =
    lastScanEpochMillis <= 0L || nowEpochMillis - lastScanEpochMillis >= throttleMs

/** 與上一個成功條碼相同時去重（空值視為不同，允許重試） */
fun isSameBarcode(lastBarcode: String?, candidate: String): Boolean =
    lastBarcode != null && lastBarcode == candidate
