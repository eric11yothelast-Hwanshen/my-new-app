package com.hmoney.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Markdown 行內區段：純資料，供測試與渲染共用 */
sealed interface MdSegment {
    val text: String

    data class Plain(override val text: String) : MdSegment
    data class Bold(override val text: String) : MdSegment
    data class Italic(override val text: String) : MdSegment
    data class Code(override val text: String) : MdSegment
}

/** 一行：heading 代表「### 標題」、bullet 代表「- 」或「* 」開頭的清單項目 */
data class MdLine(
    val segments: List<MdSegment>,
    val heading: Boolean = false,
    val bullet: Boolean = false,
)

/**
 * 輕量 Markdown 解析（純 Kotlin、保守掃描、天生不拋例外）：
 * - **粗體**、*斜體*、`行內程式碼`
 * - 行首「- 」「* 」視為清單項目（渲染為「• 」）
 * - 行首「### 」（1–3 個 #）視為標題（渲染為粗體大字）
 * - 不支援的符號原樣保留，不會拋例外
 * 請勿誤改：解析只做字串掃描，不得引入正則或第三方依賴；顏色一律 colorScheme 取用。
 */
fun parseMarkdown(text: String): List<MdLine> {
    if (text.isEmpty()) return emptyList()
    return text.split('\n').map { raw ->
        var line = raw
        var heading = false
        var bullet = false
        for (hashes in 3 downTo 1) {
            val prefix = "#".repeat(hashes) + " "
            if (line.startsWith(prefix)) {
                line = line.removePrefix(prefix)
                heading = true
                break
            }
        }
        if (!heading) {
            if (line.startsWith("- ") || line.startsWith("* ")) {
                line = line.substring(2)
                bullet = true
            }
        }
        MdLine(segments = parseInline(line), heading = heading, bullet = bullet)
    }
}

/** 行內解析：**粗體**、*斜體*、`程式碼`；未閉合的記號原樣保留 */
private fun parseInline(text: String): List<MdSegment> {
    val segments = mutableListOf<MdSegment>()
    val plain = StringBuilder()
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    if (plain.isNotEmpty()) {
                        segments.add(MdSegment.Plain(plain.toString()))
                        plain.clear()
                    }
                    segments.add(MdSegment.Bold(text.substring(i + 2, end)))
                    i = end + 2
                } else {
                    plain.append("**")
                    i += 2
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    if (plain.isNotEmpty()) {
                        segments.add(MdSegment.Plain(plain.toString()))
                        plain.clear()
                    }
                    segments.add(MdSegment.Code(text.substring(i + 1, end)))
                    i = end + 1
                } else {
                    plain.append('`')
                    i += 1
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && end > i + 1) {
                    if (plain.isNotEmpty()) {
                        segments.add(MdSegment.Plain(plain.toString()))
                        plain.clear()
                    }
                    segments.add(MdSegment.Italic(text.substring(i + 1, end)))
                    i = end + 1
                } else {
                    plain.append('*')
                    i += 1
                }
            }
            else -> {
                plain.append(text[i])
                i += 1
            }
        }
    }
    if (plain.isNotEmpty()) segments.add(MdSegment.Plain(plain.toString()))
    return segments
}

/**
 * AI 回覆文字渲染：支援 **粗體**、*斜體*、`行內程式碼`、清單與標題。
 * 請勿誤改：顏色一律從 colorScheme 取用（Code 底色 surfaceVariant、字色 onSurfaceVariant），
 * 不得 hardcode 色碼；呼叫端可透過 style / color 覆蓋基本樣式。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    val lines = remember(text) { parseMarkdown(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeForeground = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(lines, codeBackground, codeForeground) {
        buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                if (index > 0) append('\n')
                if (line.bullet) append("•  ")
                if (line.heading) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp))
                }
                line.segments.forEach { segment ->
                    when (segment) {
                        is MdSegment.Plain -> append(segment.text)
                        is MdSegment.Bold -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        is MdSegment.Italic -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        is MdSegment.Code -> pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                                color = codeForeground,
                            ),
                        )
                    }
                    append(segment.text)
                    if (segment !is MdSegment.Plain) pop()
                }
                if (line.heading) pop()
            }
        }
    }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}
