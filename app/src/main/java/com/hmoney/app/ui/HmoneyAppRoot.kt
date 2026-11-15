package com.hmoney.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import com.hmoney.app.data.work.AI_ASSISTANT_ROUTE
import com.hmoney.app.ui.components.LiquidGlassTabBar
import com.hmoney.app.ui.components.LiquidGlassTabBarItem
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.ui.navigation.SETTINGS_ROUTE
import com.hmoney.app.ui.navigation.ITEMS_ROUTE
import com.hmoney.app.R
import com.hmoney.app.ui.navigation.TopLevelDestination
import com.hmoney.app.ui.navigation.topLevelTabIndex
import com.hmoney.app.ui.screens.onboarding.CoachMarksOverlay
import com.hmoney.app.ui.screens.onboarding.CoachTargets
import com.hmoney.app.ui.screens.ai.AiScreen
import com.hmoney.app.ui.screens.StatsScreen
import com.hmoney.app.ui.screens.accounts.AccountsScreen
import com.hmoney.app.ui.screens.items.ItemsScreen
import com.hmoney.app.ui.screens.categories.CategoryManagementScreen
import com.hmoney.app.ui.screens.overview.OverviewScreen
import com.hmoney.app.ui.screens.settings.SettingsScreen
import com.hmoney.app.ui.screens.transactions.TransactionsScreen
import com.hmoney.app.ui.screens.wizard.BatteryWizardScreen
import dev.chrisbanes.haze.rememberHazeState

private const val WIZARD_ROUTE = "battery_wizard"
private const val CATEGORY_MANAGEMENT_ROUTE = "category_management"

/**
  * App 主框架：玻璃態底部導覽列（5 頁）＋ Navigation Compose 淡入淡出轉場動畫。
 * 附屬頁面（如續航設定精靈）不顯示底部導覽列。
 */
@Composable
fun HmoneyAppRoot(
    modifier: Modifier = Modifier,
    initialRoute: String? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val glassState = rememberHazeState()
    val rootViewModel: HmoneyAppRootViewModel = hiltViewModel()
    val flags by rootViewModel.featureFlags.collectAsState()
    val coachTargets = remember { CoachTargets() }

    // S50 頂層分頁動態過濾：inventoryEnabled 關閉→不顯示物品分頁；
    // tabCount／selectedTabIndex／onSelect 全走同一「過濾後清單」（route→index 映射函式，禁 hardcode）
    val visibleTabs = remember(flags.inventoryEnabled) {
        if (flags.inventoryEnabled) {
            TopLevelDestination.entries.toList()
        } else {
            TopLevelDestination.entries.filterNot { it == TopLevelDestination.ITEMS }
        }
    }
    val currentRoute = currentDestination?.route

    val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }

    // S50 coach 步驟②③：以導航 route 變化驅動 VM 狀態（FAB／分頁列內部邏輯零改動）
    LaunchedEffect(currentRoute) {
        rootViewModel.onCoachRouteChanged(currentRoute)
    }

    // 通知深連結：直接開啟對應頁面（例：AI 助手）
    LaunchedEffect(initialRoute) {
        if (initialRoute == AI_ASSISTANT_ROUTE) {
            navController.navigate(AI_ASSISTANT_ROUTE)
        }
    }

    // S50 分頁導航統一入口（點擊／滑動／帳戶頁物品入口行為等價；route 與狀態保存參數零變更）
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // S50 coach 步驟③目標：物品分頁項（由分頁列 bounds＋過濾後清單推導，不侵入分頁列內部）
    val itemsTabRect: Rect? = coachTargets.tabBarBounds?.let { bar ->
        val index = topLevelTabIndex(visibleTabs, TopLevelDestination.ITEMS.route)
        if (visibleTabs.getOrNull(index) == TopLevelDestination.ITEMS) {
            val slot = bar.width / visibleTabs.size
            Rect(
                left = bar.left + slot * index,
                top = bar.top,
                right = bar.left + slot * (index + 1),
                bottom = bar.bottom,
            )
        } else {
            null
        }
    }
    val coachStep = flags.onboardingCoachStep
    val coachActive = !flags.onboardingCompleted && coachStep in 1..3
    val coachHintRes = when (coachStep) {
        1 -> R.string.coach_step1_hint
        2 -> R.string.coach_step2_hint
        else -> R.string.coach_step3_hint
    }
    val coachTargetRect = when (coachStep) {
        1 -> coachTargets.fabBounds
        2 -> coachTargets.tabBarBounds
        else -> itemsTabRect
    }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isTopLevel) {
                // S47 流體水滴玻璃分頁列：僅替換視覺層（水滴選中指示器＋玻璃背景），
                // 分頁行為（route／navigate／狀態保存）零變動
                val selectedTab = topLevelTabIndex(visibleTabs, currentRoute)
                LiquidGlassTabBar(
                    tabCount = visibleTabs.size,
                    selectedTabIndex = selectedTab,
                    glassState = glassState,
                    // S50 coach 觀察掛鉤：收集分頁列 bounds（畫面旋轉／字級變化自動更新）
                    modifier = Modifier.onGloballyPositioned {
                        coachTargets.tabBarBounds = it.boundsInRoot()
                    },
                    // S49 滑動／fling 放手後切頁（與點擊同一導航參數組合，行為等價）
                    onSelect = { targetIndex ->
                        visibleTabs.getOrNull(targetIndex)?.let { destination ->
                            navigateToTab(destination.route)
                        }
                    },
                    tabs = {
                    visibleTabs.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        LiquidGlassTabBarItem(
                            selected = selected,
                            onClick = { navigateToTab(destination.route) },
                            icon = destination.icon,
                            labelRes = destination.labelRes,
                            modifier = Modifier.weight(1f),
                        )
                    }
                },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.OVERVIEW.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .glassSource(glassState),
            // 分頁切換淡入淡出轉場（純 fade，不閃白、不生硬）
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 220)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 180)) },
            popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 220)) },
            popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 180)) },
        ) {
            // AI 錯誤回饋的「前往設定」：切換到設定分頁（AI 連線設定的唯一入口）
            val goSettings = {
                navController.navigate(SETTINGS_ROUTE) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            composable(TopLevelDestination.OVERVIEW.route) {
                OverviewScreen(
                    onGoSettings = goSettings,
                    // S50 coach 觀察掛鉤：FAB 點擊通知（既有 onClick 行為零改動）＋ bounds 收集
                    onAddEntryTapped = rootViewModel::onCoachFabTapped,
                    coachTargets = coachTargets,
                )
            }
            composable(TopLevelDestination.TRANSACTIONS.route) {
                TransactionsScreen(onGoSettings = goSettings)
            }
            composable(TopLevelDestination.STATS.route) {
                StatsScreen(onGoSettings = goSettings)
            }
            composable(TopLevelDestination.ACCOUNTS.route) {
                AccountsScreen(
                    onGoSettings = goSettings,
                    // S50：帳戶頁物品入口＝切到物品分頁（與分頁點擊行為等價）
                    onGoItems = { navigateToTab(ITEMS_ROUTE) },
                )
            }
            composable(ITEMS_ROUTE) {
                ItemsScreen(
                    onBack = { navController.popBackStack() },
                    onGoSettings = goSettings,
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenWizard = { navController.navigate(WIZARD_ROUTE) },
                    onOpenAi = { navController.navigate(AI_ASSISTANT_ROUTE) },
                    onOpenCategories = { navController.navigate(CATEGORY_MANAGEMENT_ROUTE) },
                )
            }
            composable(CATEGORY_MANAGEMENT_ROUTE) {
                CategoryManagementScreen(onBack = { navController.popBackStack() })
            }
            composable(WIZARD_ROUTE) {
                BatteryWizardScreen(onBack = { navController.popBackStack() })
            }
            composable(AI_ASSISTANT_ROUTE) {
                AiScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    // S50 實機點擊引導覆蓋層：總覽頁最上層（洞內觸控直達 FAB／分頁列，可真的被點）
    if (coachActive && coachStep != 3 || (coachActive && coachStep == 3 && itemsTabRect != null)) {
        // 步驟③無物品分頁（inventoryEnabled 關閉）時 advanceCoach 已直接完成，不會進到這裡；此保護僅為防呆
        CoachMarksOverlay(
            step = coachStep,
            targetRect = coachTargetRect,
            hint = stringResource(coachHintRes),
            onSkip = rootViewModel::skipCoach,
        )
    }
    }
}
