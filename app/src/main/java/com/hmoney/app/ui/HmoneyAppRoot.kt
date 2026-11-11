package com.hmoney.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hmoney.app.ui.navigation.TopLevelDestination
import com.hmoney.app.ui.screens.AccountsScreen
import com.hmoney.app.ui.screens.OverviewScreen
import com.hmoney.app.ui.screens.SettingsScreen
import com.hmoney.app.ui.screens.StatsScreen
import com.hmoney.app.ui.screens.TransactionsScreen

/**
 * App 主框架：底部導覽列（5 頁）＋ Navigation Compose。
 * 玻璃態視覺與動效將於 UI 全面實作階段套用。
 */
@Composable
fun HmoneyAppRoot(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.OVERVIEW.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.OVERVIEW.route) { OverviewScreen() }
            composable(TopLevelDestination.TRANSACTIONS.route) { TransactionsScreen() }
            composable(TopLevelDestination.STATS.route) { StatsScreen() }
            composable(TopLevelDestination.ACCOUNTS.route) { AccountsScreen() }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
