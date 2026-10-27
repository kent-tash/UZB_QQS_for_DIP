package com.example.uzb_qqs_for_dip.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.settings.Quarter
import com.example.uzb_qqs_for_dip.ui.AppViewModel
import com.example.uzb_qqs_for_dip.ui.AuditorViewModel
import com.example.uzb_qqs_for_dip.ui.components.AboutAppBottomSheet
import com.example.uzb_qqs_for_dip.ui.components.BackupBottomSheet
import com.example.uzb_qqs_for_dip.ui.components.HelpBottomSheet
import com.example.uzb_qqs_for_dip.ui.components.MainMenuBottomSheet
import com.example.uzb_qqs_for_dip.ui.components.UserManagementBottomSheet
import com.example.uzb_qqs_for_dip.ui.screens.AuditorReceiptSearchScreen
import com.example.uzb_qqs_for_dip.ui.screens.AuditorScreen
import com.example.uzb_qqs_for_dip.ui.screens.AuditorVerifyScreen
import com.example.uzb_qqs_for_dip.ui.screens.AuthScreen
import com.example.uzb_qqs_for_dip.ui.screens.RegisterScreen
import com.example.uzb_qqs_for_dip.ui.screens.ReportScreen
import com.example.uzb_qqs_for_dip.ui.screens.ScanScreen

sealed class TopRoute(val route: String) {
    data object Auth : TopRoute("auth")
    data object Register : TopRoute("register")
    data object Main : TopRoute("main")
}

sealed class MainTab(val route: String, val title: String, val icon: ImageVector) {
    data object Scan : MainTab("main/scan", "Добавить", Icons.Outlined.QrCodeScanner)
    data object Report : MainTab("main/report", "Чеки и отчёт", Icons.Outlined.Description)
    data object Audit : MainTab("main/audit", "Аудит", Icons.AutoMirrored.Outlined.FactCheck)
    companion object {
        val employee: List<MainTab> get() = listOf(Scan, Report)
        val auditor: List<MainTab> get() = listOf(Audit, Report)
    }
}

private const val ROUTE_AUDIT_VERIFY = "main/audit/verify"
private const val ROUTE_AUDIT_SEARCH = "main/audit/search"
private const val ARG_USER_ID = "userId"
private const val ARG_QUARTER = "quarter"
private const val ARG_YEAR = "year"
private const val ARG_MANUAL = "manual"

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
                onCreated = { }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    appViewModel: AppViewModel,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val tabsNav = rememberNavController()
    val backStackEntry by tabsNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()
    val isAuditor = currentUser?.role == UserRole.AUDITOR
    val tabs = if (isAuditor) MainTab.auditor else MainTab.employee
    val startTab = if (isAuditor) MainTab.Audit.route else MainTab.Scan.route
    val isAuditVerify = currentRoute?.startsWith(ROUTE_AUDIT_VERIFY) == true
    val isAuditSearch = currentRoute?.startsWith(ROUTE_AUDIT_SEARCH) == true
    val isSubScreen = isAuditVerify || isAuditSearch

    val currentTitle = when {
        isAuditVerify -> "Проверка чеков"
        isAuditSearch -> "Поиск чека"
        else -> tabs.find { it.route == currentRoute }?.title ?: "UZB QQS"
    }

    val quickScanRequested by appViewModel.quickScanRequested.collectAsStateWithLifecycle()
    LaunchedEffect(quickScanRequested) {
        if (quickScanRequested && !isAuditor && currentRoute != MainTab.Scan.route) {
            tabsNav.navigate(MainTab.Scan.route) {
                popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val pendingReceiptUrl by appViewModel.pendingReceiptUrl.collectAsStateWithLifecycle()
    LaunchedEffect(pendingReceiptUrl) {
        if (pendingReceiptUrl != null) {
            if (isAuditor) {
                appViewModel.consumePendingReceiptUrl()
                Toast.makeText(context, "Добавление чеков недоступно в режиме аудитора", Toast.LENGTH_SHORT).show()
            } else if (currentRoute != MainTab.Scan.route) {
                tabsNav.navigate(MainTab.Scan.route) {
                    popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    LaunchedEffect(isAuditor) {
        val current = tabsNav.currentDestination?.route
        val validRoutes = tabs.map { it.route } + listOf(ROUTE_AUDIT_SEARCH, ROUTE_AUDIT_VERIFY)
        if (current != null && current !in validRoutes) {
            tabsNav.navigate(startTab) {
                popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    var showMainMenu by remember { mutableStateOf(false) }
    var showUserManagement by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAboutApp by remember { mutableStateOf(false) }

    if (showMainMenu) {
        MainMenuBottomSheet(
            user = currentUser,
            onOpenUserManagement = { showUserManagement = true },
            onSwitchRole = { appViewModel.switchCurrentUserRole() },
            onOpenBackup = { showBackup = true },
            onOpenHelp = { showHelp = true },
            onOpenAboutApp = { showAboutApp = true },
            onDismissRequest = { showMainMenu = false }
        )
    }

    if (showUserManagement) {
        UserManagementBottomSheet(
            appViewModel = appViewModel,
            onDismissRequest = { showUserManagement = false },
            onLoggedOut = onLoggedOut
        )
    }

    if (showBackup) {
        BackupBottomSheet(
            appViewModel = appViewModel,
            onDismissRequest = { showBackup = false },
            onNavigateToAuth = onLoggedOut
        )
    }

    if (showHelp) {
        HelpBottomSheet(onDismissRequest = { showHelp = false })
    }

    if (showAboutApp) {
        AboutAppBottomSheet(onDismissRequest = { showAboutApp = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentTitle,
                        modifier = Modifier.clickable {
                            currentRoute?.let { appViewModel.requestScrollToTop(it) }
                        }
                    )
                },
                navigationIcon = {
                    if (isSubScreen) {
                        IconButton(onClick = { tabsNav.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { showMainMenu = true }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Главное меню профиля: ${currentUser?.fullName ?: "Гость"}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser?.fullName?.take(1)?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
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
            startDestination = startTab,
            modifier = Modifier.padding(padding)
        ) {
            composable(MainTab.Scan.route) {
                ScanScreen(appViewModel = appViewModel)
            }
            composable(MainTab.Report.route) {
                ReportScreen(appViewModel = appViewModel)
            }
            composable(MainTab.Audit.route) {
                val auditorVm: AuditorViewModel = viewModel()
                val quarter by auditorVm.quarter.collectAsStateWithLifecycle()
                val year by auditorVm.year.collectAsStateWithLifecycle()
                AuditorScreen(
                    appViewModel = appViewModel,
                    onVerifyEmployee = { userId ->
                        tabsNav.navigate(
                            "$ROUTE_AUDIT_VERIFY?$ARG_USER_ID=$userId&$ARG_QUARTER=${quarter.name}&$ARG_YEAR=$year"
                        )
                    },
                    onSearchReceipts = {
                        tabsNav.navigate(ROUTE_AUDIT_SEARCH)
                    }
                )
            }
            composable(ROUTE_AUDIT_SEARCH) {
                AuditorReceiptSearchScreen(
                    onBack = { tabsNav.popBackStack() },
                    onOpenEmployee = { userId ->
                        tabsNav.navigate(
                            "$ROUTE_AUDIT_VERIFY?$ARG_USER_ID=$userId&$ARG_QUARTER=Q1&$ARG_YEAR=2026"
                        )
                    }
                )
            }
            composable(
                "$ROUTE_AUDIT_VERIFY?$ARG_USER_ID={$ARG_USER_ID}&$ARG_QUARTER={$ARG_QUARTER}&$ARG_YEAR={$ARG_YEAR}"
            ) { backStack ->
                val userId = backStack.arguments?.getString(ARG_USER_ID)?.toLongOrNull()
                val quarterName = backStack.arguments?.getString(ARG_QUARTER)
                val year = backStack.arguments?.getString(ARG_YEAR)?.toIntOrNull()
                val quarter = quarterName?.let { runCatching { Quarter.valueOf(it) }.getOrNull() }
                AuditorVerifyScreen(
                    appViewModel = appViewModel,
                    preselectedUserId = userId,
                    initialQuarter = quarter,
                    initialYear = year,
                    onBack = { tabsNav.popBackStack() }
                )
            }
        }
    }
}
