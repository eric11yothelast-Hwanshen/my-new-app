package com.hmoney.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import com.hmoney.app.data.work.AI_ASSISTANT_ROUTE
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.ui.components.glassSurface
import com.hmoney.app.ui.navigation.TopLevelDestination
import com.hmoney.app.ui.screens.ai.AiScreen
import com.hmoney.app.ui.screens.StatsScreen
import com.hmoney.app.ui.screens.accounts.AccountsScreen
import com.hmoney.app.ui.screens.overview.OverviewScreen
import com.hmoney.app.ui.screens.settings.SettingsScreen
import com.hmoney.app.ui.screens.transactions.TransactionsScreen
import com.hmoney.app.ui.screens.wizard.BatteryWizardScreen
import dev.chrisbanes.haze.rememberHazeState

private const val WIZARD_ROUTE = "battery_wizard"

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

    val isTopLevel = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    // 通知深連結：直接開啟對應頁面（例：AI 助手）
    LaunchedEffect(initialRoute) {
        if (initialRoute == AI_ASSISTANT_ROUTE) {
            navController.navigate(AI_ASSISTANT_ROUTE)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isTopLevel) {
                // 玻璃態底部導覽列：API 33+ 背景模糊，低版本半透明壓色
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.glassSurface(
                        state = glassState,
                        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        cornerRadius = 0.dp,
                    ),
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
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
                navController.navigate(TopLevelDestination.SETTINGS.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            composable(TopLevelDestination.OVERVIEW.route) {
                OverviewScreen(onGoSettings = goSettings)
            }
            composable(TopLevelDestination.TRANSACTIONS.route) {
                TransactionsScreen(onGoSettings = goSettings)
            }
            composable(TopLevelDestination.STATS.route) {
                StatsScreen(onGoSettings = goSettings)
            }
            composable(TopLevelDestination.ACCOUNTS.route) {
                AccountsScreen(onGoSettings = goSettings)
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenWizard = { navController.navigate(WIZARD_ROUTE) },
                    onOpenAi = { navController.navigate(AI_ASSISTANT_ROUTE) },
                )
            }
            composable(WIZARD_ROUTE) {
                BatteryWizardScreen(onBack = { navController.popBackStack() })
            }
            composable(AI_ASSISTANT_ROUTE) {
                AiScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
