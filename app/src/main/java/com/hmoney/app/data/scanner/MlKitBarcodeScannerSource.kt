package com.hmoney.app.data.scanner

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hmoney.app.domain.scanner.BarcodeScanError
import com.hmoney.app.domain.scanner.BarcodeScanResult
import com.hmoney.app.domain.scanner.BarcodeScannerSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit 條碼掃描實作（S40，bundled 版離線可用）：
 * 支援 EAN-13／EAN-8／UPC-A／QR Code／Code-128。
 * 偵測不到條碼或 ML Kit 內部失敗一律回 Failure(NoBarcodeFound)，不拋例外；
 * 相機授權與偵測逾時屬 S41 掃描流程層職責（介面已定義對應錯誤類型）。
 */
@Singleton
class MlKitBarcodeScannerSource @Inject constructor() : BarcodeScannerSource {

    private val directExecutor = Executor { it.run() }

    // S41-fix2：空結果低頻 log（約每 2 秒一筆），避免洗 log
    private var lastEmptyLogAt = 0L

    // S41 鐵律：scanner client 禁止逐幀新建——此類為 @Singleton，client 以 lazy 欄位重用
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13 or Barcode.FORMAT_EAN_8 or
                        Barcode.FORMAT_UPC_A or Barcode.FORMAT_QR_CODE or
                        Barcode.FORMAT_CODE_128,
                )
                .build(),
        )
    }

    override suspend fun scan(bitmap: Bitmap, rotationDegrees: Int): BarcodeScanResult {
        try {
            val barcodes = scanner.process(
                InputImage.fromBitmap(bitmap, resolveImageRotation(rotationDegrees)),
            ).awaitTask()
            val value = barcodes.firstOrNull()?.rawValue
            if (value.isNullOrBlank()) {
                logEmptyResultThrottled(bitmap, rotationDegrees, barcodes.size)
                return BarcodeScanResult.Failure(BarcodeScanError.NoBarcodeFound)
            }
            return BarcodeScanResult.Success(value)
        } catch (e: Exception) {
            // S41-fix2：例外不得靜默——一律留 log，UI 行為不變（仍回 NoBarcodeFound）
            android.util.Log.e("MlKitScan", "barcode scan failed", e)
            return BarcodeScanResult.Failure(BarcodeScanError.NoBarcodeFound)
        }
    }

    private fun logEmptyResultThrottled(bitmap: Bitmap, rotationDegrees: Int, count: Int) {
        val now = System.currentTimeMillis()
        if (now - lastEmptyLogAt < EMPTY_LOG_INTERVAL_MS) return
        lastEmptyLogAt = now
        android.util.Log.i(
            "MlKitScan",
            "no barcode: bitmap=${bitmap.width}x${bitmap.height}, rotation=$rotationDegrees, results=$count",
        )
    }

    private companion object {
        const val EMPTY_LOG_INTERVAL_MS = 2000L
    }

    /** Task → suspend（不引入額外協程相依，用 suspendCancellableCoroutine 橋接） */
    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener(directExecutor) { cont.resume(it) }
            addOnFailureListener(directExecutor) { cont.resumeWithException(it) }
            cont.invokeOnCancellation { }
        }
}
