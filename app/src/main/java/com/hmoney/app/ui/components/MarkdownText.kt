/**
 * 【勿改警示】Markdown 渲染管線（parseMarkdown → buildMdAnnotatedString → MarkdownText）：
 * 渲染時每個 segment 恰好 append 一次——when 分支只負責 pushStyle，
 * 不得在分支內 append（Session 34 修正的重複渲染根因，請勿誤改回）。
 */
package com.hmoney.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Markdown 行內區段：純資料，供測試與渲染共用 */
sealed interface MdSegment {
    val text: String

    data class Plain(override val text: String) : MdSegment
    data class Bold(override val text: String) : MdSegment
    data class Italic(override val text: String) : MdSegment
    data class Code(override val text: String) : MdSegment
}

/**
 * 一行：
 * - headingLevel：1–3 級標題（0 表示非標題）
 * - bullet：「- 」「* 」開頭的無序清單項目
 * - orderedNumber：「1. 」開頭的有序清單編號（內容不含編號）
 * - divider：整行只有 3 個以上連續「-」「*」「—」的分隔線
 */
data class MdLine(
    val segments: List<MdSegment>,
    val headingLevel: Int = 0,
    val bullet: Boolean = false,
    val orderedNumber: String? = null,
    val divider: Boolean = false,
)

/**
 * 輕量 Markdown 解析（純 Kotlin、保守掃描、天生不拋例外）：
 * - **粗體**、*斜體*、`行內程式碼`
 * - 行首「- 」「* 」視為無序清單項目（渲染為「• 」）
 * - 行首「數字. 」視為有序清單（保留編號渲染，不疊圓點）
 * - 行首「#」「##」「### 」視為標題（分級渲染），行尾「 ##」等裝飾性收尾 hash 一併清除
 * - 整行僅 3 個以上連續「-」「*」「—」（允許前後空白）視為分隔線
 * - 不支援的符號原樣保留，不會拋例外
 * 請勿誤改：解析只做字串掃描，不得引入正則或第三方依賴；顏色一律 colorScheme 取用。
 */
fun parseMarkdown(text: String): List<MdLine> {
    if (text.isEmpty()) return emptyList()
    return text.split('\n').map { raw ->
        var line = raw
        var headingLevel = 0
        var bullet = false
        var orderedNumber: String? = null

        val trimmed = line.trim()
        if (trimmed.length >= 3 &&
            (trimmed.all { it == '-' } || trimmed.all { it == '*' } || trimmed.all { it == '—' })
        ) {
            return@map MdLine(segments = emptyList(), divider = true)
        }

        for (level in 3 downTo 1) {
            val prefix = "#".repeat(level) + " "
            if (line.startsWith(prefix)) {
                line = line.removePrefix(prefix)
                headingLevel = level
                break
            }
        }
        if (headingLevel > 0) {
            line = stripTrailingHashes(line)
        } else {
            var digitEnd = 0
            while (digitEnd < line.length && line[digitEnd] in '0'..'9') digitEnd += 1
            if (digitEnd > 0 &&
                digitEnd + 1 < line.length &&
                line[digitEnd] == '.' &&
                line[digitEnd + 1] == ' '
            ) {
                orderedNumber = line.substring(0, digitEnd)
                line = line.substring(digitEnd + 2)
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                line = line.substring(2)
                bullet = true
            }
        }
        MdLine(
            segments = parseInline(line),
            headingLevel = headingLevel,
            bullet = bullet,
            orderedNumber = orderedNumber,
        )
    }
}

/** 標題收尾清理：剝除行尾「 ##」等以空白分隔的裝飾性 hash（保守：無空白分隔不動） */
private fun stripTrailingHashes(line: String): String {
    var end = line.length
    while (end > 0 && line[end - 1] == ' ') end -= 1
    var hashStart = end
    while (hashStart > 0 && line[hashStart - 1] == '#') hashStart -= 1
    return if (hashStart < end && hashStart > 0 && line[hashStart - 1] == ' ') {
        line.substring(0, hashStart).trimEnd()
    } else {
        line
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
 * 單行→ AnnotatedString 的組字純函式（不碰 Compose 狀態，可單元測試）。
 * 【勿改警示】每個 segment 恰好 append 一次——when 分支只負責 pushStyle，
 * 不得在分支內 append（Session 34 修正的重複渲染根因，請勿誤改回）。
 */
internal fun buildMdAnnotatedString(
    line: MdLine,
    codeBackground: Color,
    codeForeground: Color,
): AnnotatedString = buildAnnotatedString {
    if (line.bullet) append("•  ")
    if (line.orderedNumber != null) {
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append("${line.orderedNumber}. ")
        pop()
    }
    if (line.headingLevel > 0) {
        pushStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                fontSize = when (line.headingLevel) {
                    1 -> 20.sp
                    2 -> 18.sp
                    else -> 16.sp
                },
            ),
        )
    }
    line.segments.forEach { segment ->
        when (segment) {
            is MdSegment.Plain -> Unit
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
    if (line.headingLevel > 0) pop()
}

/**
 * AI 回覆文字渲染：支援 **粗體**、*斜體*、`行內程式碼`、無序／有序清單、分隔線與分級標題。
 * 請勿誤改：顏色一律從 colorScheme 取用（Code 底色 surfaceVariant、字色 onSurfaceVariant），
 * 不得 hardcode 色碼；呼叫端可透過 style / color 覆蓋基本樣式。
 * 組字邏輯在 buildMdAnnotatedString（見檔頭勿改警示）。
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
    Column(modifier = modifier) {
        lines.forEach { line ->
            if (line.divider) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            } else {
                Text(
                    text = remember(line, codeBackground, codeForeground) {
                        buildMdAnnotatedString(line, codeBackground, codeForeground)
                    }
                    },
                    style = style,
                    color = color,
                )
            }
        }
    }
}
