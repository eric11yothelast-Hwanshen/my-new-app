package com.hmoney.app.domain.scanner

import android.graphics.Bitmap

/**
 * 條碼掃描器介面（S40 定義、S41 使用——介面簽名改動需於 PROGRESS.md 註明理由）：
 * 輸入圖像 → 條碼字串或明確錯誤。
 */
interface BarcodeScannerSource {

    /** 掃描單張圖像；偵測不到或 ML Kit 失敗時回 Failure，不拋例外 */
    suspend fun scan(bitmap: Bitmap): BarcodeScanResult = scan(bitmap, 0)

    /**
     * S41 附加：帶旋轉角的掃描（CameraX ImageProxy 幀的 rotationDegrees）。
     * 介面變更理由：相機分析幀為感應器方向，ML Kit 需旋轉資訊才能穩定辨識條碼；
     * 原 scan(bitmap) 保留不動（預設 0 度，相簿圖片用）。
     */
    suspend fun scan(bitmap: Bitmap, rotationDegrees: Int): BarcodeScanResult
}

/** 掃描結果：成功（條碼字串）或失敗（明確錯誤類型） */
sealed class BarcodeScanResult {
    data class Success(val barcode: String) : BarcodeScanResult()
    data class Failure(val error: BarcodeScanError) : BarcodeScanResult()
}

/** 掃描錯誤（S40 定義；相機授權與偵測逾時由 S41 的掃描流程層使用） */
sealed class BarcodeScanError {
    /** 相機權限未授權（S41 掃描流程層判定） */
    data object CameraPermissionDenied : BarcodeScanError()

    /** 偵測逾時（S41 掃描流程層判定） */
    data object DetectionTimeout : BarcodeScanError()

    /** 圖像中沒有可辨識的條碼（或 ML Kit 辨識失敗） */
    data object NoBarcodeFound : BarcodeScanError()
}
