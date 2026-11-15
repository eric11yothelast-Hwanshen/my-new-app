package com.hmoney.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 憑證圖片存取介面（S41：拍照記帳解碼／縮圖／存檔／base64） */
interface ReceiptImageStore {
    /** @return base64 與憑證檔案路徑；解碼失敗回 null */
    fun decodeAndStore(uri: Uri): Pair<String, String>?

    /** S44：刪除憑證暫存檔（聊天草稿取消／丟棄時，嚴禁孤兒檔案）；路徑不存在時靜默 */
    fun delete(path: String) {
        runCatching { java.io.File(path).delete() }
    }
}

/** 憑證圖片實作：最長邊縮至 1280、JPEG 85 存 filesDir/receipts/ */
@Singleton
class ReceiptImageStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReceiptImageStore {

    override fun decodeAndStore(uri: Uri): Pair<String, String>? = runCatching {
        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        val scale = (MAX_EDGE.toFloat() / maxOf(source.width, source.height)).coerceAtMost(1f)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt(),
                (source.height * scale).toInt(),
                true,
            )
        } else {
            source
        }
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP) to file.absolutePath
    }.getOrNull()

    private companion object {
        const val MAX_EDGE = 1280
    }
}
