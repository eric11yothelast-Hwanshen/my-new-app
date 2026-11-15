package com.hmoney.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import kotlinx.coroutines.launch

/**
 * S50 新手引導（六頁）：歡迎→記帳三入口（手動表單／AI 對話／掃條碼各自一小段）→
 * AI 查詢與刪改交易→物品管理（含「之後有獨立分頁」）→統計與帳戶→完成頁（提示接著有實機點擊引導）。
 * 「跳過」→ onSkip（寫完成旗標，永不再現）；最後一頁「開始使用」→ onStart（進入實機點擊引導 step 1）。
 * 內容為既有功能的靜態說明＋示意，嚴禁真的觸發記帳流程；有限高度（單一捲動容器鐵律不涉入）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1

    ModalBottomSheet(
        onDismissRequest = onSkip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 380.dp),
            ) { page ->
                OnboardingPage(page = page)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.onboarding_skip))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / $ONBOARDING_PAGE_COUNT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (isLastPage) {
                                onStart()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (isLastPage) R.string.onboarding_start else R.string.onboarding_next,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    val (icon, titleRes, bodyRes) = when (page) {
        0 -> Triple<ImageVector, Int, Int>(
            Icons.Outlined.Savings,
            R.string.onboarding_page1_title,
            R.string.onboarding_page1_body,
        )
        1 -> Triple<ImageVector, Int, Int>(
            Icons.Outlined.Edit,
            R.string.onboarding_page2_title,
            R.string.onboarding_page2_body,
        )
        2 -> Triple<ImageVector, Int, Int>(
            Icons.AutoMirrored.Outlined.ReceiptLong,
            R.string.onboarding_page3_title,
            R.string.onboarding_page3_body,
        )
        3 -> Triple<ImageVector, Int, Int>(
            Icons.Outlined.QrCodeScanner,
            R.string.onboarding_page4_title,
            R.string.onboarding_page4_body,
        )
        4 -> Triple<ImageVector, Int, Int>(
            Icons.Outlined.AutoAwesome,
            R.string.onboarding_page5_title,
            R.string.onboarding_page5_body,
        )
        else -> Triple<ImageVector, Int, Int>(
            Icons.Outlined.PieChart,
            R.string.onboarding_page6_title,
            R.string.onboarding_page6_body,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val ONBOARDING_PAGE_COUNT = 6
