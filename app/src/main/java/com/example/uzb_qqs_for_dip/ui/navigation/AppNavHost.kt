package com.example.uzb_qqs_for_dip.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.screens.AuthScreen
import com.example.uzb_qqs_for_dip.ui.screens.ProfileScreen
import com.example.uzb_qqs_for_dip.ui.screens.ReceiptsScreen
import com.example.uzb_qqs_for_dip.ui.screens.RegisterScreen
import com.example.uzb_qqs_for_dip.ui.screens.ReportScreen
import com.example.uzb_qqs_for_dip.ui.screens.ScanScreen

private sealed class TopRoute(val route: String) {
    data object Auth : TopRoute("auth")
    data object Register : TopRoute("register")
    data object Main : TopRoute("main")
}

private sealed class MainTab(val route: String, val title: String, val icon: ImageVector) {
    data object Scan : MainTab("main/scan", "Добавить", Icons.Outlined.QrCodeScanner)
    data object Receipts : MainTab("main/receipts", "Чеки", Icons.Outlined.TableChart)
    data object Report : MainTab("main/report", "Отчёт", Icons.Outlined.Description)
    data object Profile : MainTab("main/profile", "Профиль", Icons.Outlined.Person)

    companion object {
        val all = listOf(Scan, Receipts, Report, Profile)
    }
}

@Composable
fun AppNavHost(appViewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()

    val startDestination = if (currentUser == null) TopRoute.Auth.route else TopRoute.Main.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(TopRoute.Auth.route) {
            AuthScreen(
                appViewModel = appViewModel,
                onAuthenticated = {
                    navController.navigate(TopRoute.Main.route) {
                        popUpTo(TopRoute.Auth.route) { inclusive = true }
                    }
                },
                onCreateProfile = { navController.navigate(TopRoute.Register.route) }
            )
        }
        composable(TopRoute.Register.route) {
            RegisterScreen(
                appViewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onCreated = {
                    navController.navigate(TopRoute.Main.route) {
                        popUpTo(TopRoute.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(TopRoute.Main.route) {
            MainScaffold(
                appViewModel = appViewModel,
                onLoggedOut = {
                    navController.navigate(TopRoute.Auth.route) {
                        popUpTo(TopRoute.Main.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun MainScaffold(
    appViewModel: AppViewModel,
    onLoggedOut: () -> Unit
) {
    val tabsNav = rememberNavController()
    val backStackEntry by tabsNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.all.forEach { tab ->
                    val selected = currentRoute?.let { route ->
                        backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true ||
                            route == tab.route
                    } ?: false
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tabsNav.navigate(tab.route) {
                                popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = tabsNav,
            startDestination = MainTab.Scan.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(MainTab.Scan.route) {
                ScanScreen(appViewModel = appViewModel)
            }
            composable(MainTab.Receipts.route) {
                ReceiptsScreen(appViewModel = appViewModel)
            }
            composable(MainTab.Report.route) {
                ReportScreen(appViewModel = appViewModel)
            }
            composable(MainTab.Profile.route) {
                ProfileScreen(
                    appViewModel = appViewModel,
                    onLoggedOut = onLoggedOut
                )
            }
        }
    }
}
