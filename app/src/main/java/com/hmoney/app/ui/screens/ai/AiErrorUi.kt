package com.hmoney.app.ui.screens.ai

import androidx.annotation.StringRes
import com.hmoney.app.R
import com.hmoney.app.domain.model.AiError

/** AiError → 顯示字串資源（各畫面共用，錯誤一律有友善回饋，不閃退） */
@StringRes
fun AiError.toMessageRes(): Int = when (this) {
    AiError.NotConfigured -> R.string.ai_not_configured
    AiError.Network -> R.string.ai_error_network
    AiError.Unauthorized -> R.string.ai_error_unauthorized
    AiError.QuotaExceeded -> R.string.ai_error_quota
    is AiError.Http -> R.string.ai_error_http
    AiError.Parse -> R.string.ai_error_parse
    // 專屬字串之後 session 再接（S41/S42），先以通用錯誤文字回饋
    AiError.VisionUnsupported -> R.string.ai_error_http
}
