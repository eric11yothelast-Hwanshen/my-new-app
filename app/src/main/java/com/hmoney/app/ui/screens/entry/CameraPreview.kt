package com.hmoney.app.ui.screens.entry

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.hardware.display.DisplayManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * CameraX 相機預覽共用元件（S43 自 ScanOverlayScreen 抽出，行為零變更）：
 * 1280×720 分析串流（條碼偵測依賴幀寬度，勿降回預設）＋HARDWARE bitmap 軟體複製防禦＋
 * targetRotation 對齊 display；綁定一律用 LocalLifecycleOwner，嚴禁 context cast LifecycleOwner。
 * 兩個掃碼 Dialog（ScanOverlayScreen／SimpleScanDialog）共用此元件，不得各自複製相機程式碼。
 */
@Composable
fun CameraPreview(onFrame: (android.graphics.Bitmap, Int) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    // S50：analysis 參照——display 旋轉時更新 targetRotation（掃碼中轉橫仍可偵測）
    val analysisRef = remember { java.util.concurrent.atomic.AtomicReference<ImageAnalysis?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            analysisRef.set(null)
        }
    }
    // S50：觀察 display rotation 變化，即時同步 analyzer 的 targetRotation（既有黑屏防護零變動）
    DisposableEffect(context) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.displays?.firstOrNull()
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                val analysis = analysisRef.get() ?: return
                val rotation = display?.rotation ?: return
                analysis.targetRotation = rotation
                android.util.Log.i(
                    "CameraPreview",
                    "display rotation changed, targetRotation updated to $rotation",
                )
            }

            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        display?.let { displayManager?.registerDisplayListener(listener, null) }
        onDispose { displayManager?.unregisterDisplayListener(listener) }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext)
            val future = ProcessCameraProvider.getInstance(viewContext)
            future.addListener(
                {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(
                            androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    androidx.camera.core.resolutionselector.ResolutionStrategy(
                                        android.util.Size(1280, 720),
                                        androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    ),
                                )
                                .build(),
                        )
                        .build()
                    analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                        try {
                            val bitmap = proxy.toBitmap()
                            val rotation = proxy.imageInfo.rotationDegrees
                            // HARDWARE bitmap ML Kit 無法讀取，先複製成軟體 ARGB_8888
                            val frame = if (bitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                                runCatching {
                                    bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                }.getOrDefault(bitmap)
                            } else {
                                bitmap
                            }
                            onFrame(frame, rotation)
                        } finally {
                            // CameraX 鐵律：分析結束前必須 close，否則畫面卡死
                            proxy.close()
                        }
                    }
                    analysisRef.set(analysis)
                    val display = previewView.display
                    if (display != null) {
                        analysis.targetRotation = display.rotation
                    } else {
                        android.util.Log.w(
                            "CameraPreview",
                            "display unavailable, analysis targetRotation fallback to ROTATION_0",
                        )
                        analysis.targetRotation = android.view.Surface.ROTATION_0
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }.onFailure { e ->
                        android.util.Log.e("CameraPreview", "CameraX bindToLifecycle failed", e)
                    }
                },
                ContextCompat.getMainExecutor(viewContext),
            )
            previewView
        },
    )
}
