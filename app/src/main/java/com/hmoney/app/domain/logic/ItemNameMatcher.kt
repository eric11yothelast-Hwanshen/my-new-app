package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Item

/**
 * S47 物品名稱匹配（純函式，JVM 單元測試直接驗證）：
 * UPDATE／DELETE 前先對 ItemRepository.observeAll 清單匹配名稱——
 * ①完全相等（ignoreCase）→ ②名稱包含（ignoreCase）；
 * 唯一命中 → Unique；多筆符合 → Ambiguous（列出候選請使用者講明）；無 → NotFound。
 */
sealed interface ItemNameMatch {
    data class Unique(val item: Item) : ItemNameMatch
    data class Ambiguous(val items: List<Item>) : ItemNameMatch
    data object NotFound : ItemNameMatch
}

fun matchItemByName(items: List<Item>, name: String): ItemNameMatch {
    val target = name.trim()
    if (target.isEmpty()) return ItemNameMatch.NotFound
    val exact = items.filter { it.name.equals(target, ignoreCase = true) }
    if (exact.size == 1) return ItemNameMatch.Unique(exact.single())
    if (exact.size > 1) return ItemNameMatch.Ambiguous(exact)
    val contains = items.filter {
        it.name.contains(target, ignoreCase = true) || target.contains(it.name, ignoreCase = true)
    }
    return when (contains.size) {
        0 -> ItemNameMatch.NotFound
        1 -> ItemNameMatch.Unique(contains.single())
        else -> ItemNameMatch.Ambiguous(contains)
    }
}
