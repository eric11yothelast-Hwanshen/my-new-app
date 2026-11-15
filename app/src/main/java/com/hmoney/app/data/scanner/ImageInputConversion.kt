package com.hmoney.app.data.scanner

/**
 * CameraX ImageProxy → ML Kit InputImage 的尺寸與旋轉換算（S40 純函式，S41 使用）：
 * CameraX 的 rotationDegrees 為順時針旋轉量，InputImage.fromMediaImage 需要同一數值；
 * 旋轉 90／270 度時顯示尺寸寬高對調。
 */
data class ImageInputSpec(
    val rotationDegrees: Int,
    val width: Int,
    val height: Int,
)

/** 正規化旋轉角至 0..359（負值與超過 360 的輸入皆容錯） */
fun resolveImageRotation(rotationDegrees: Int): Int =
    ((rotationDegrees % 360) + 360) % 360

/** 依旋轉角換算顯示尺寸（90／270 度時寬高對調） */
fun resolveDisplayedSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> {
    val rotation = resolveImageRotation(rotationDegrees)
    return if (rotation == 90 || rotation == 270) height to width else width to height
}

/** 組出 InputImage 需要的規格（rotation 正規化＋顯示尺寸換算） */
fun toImageInputSpec(imageWidth: Int, imageHeight: Int, rotationDegrees: Int): ImageInputSpec {
    val (w, h) = resolveDisplayedSize(imageWidth, imageHeight, rotationDegrees)
    return ImageInputSpec(
        rotationDegrees = resolveImageRotation(rotationDegrees),
        width = w,
        height = h,
    )
}
