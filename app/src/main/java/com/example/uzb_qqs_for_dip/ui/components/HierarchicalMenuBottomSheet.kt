package com.example.uzb_qqs_for_dip.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uzb_qqs_for_dip.data.model.User
import com.example.uzb_qqs_for_dip.data.model.UserRole
import com.example.uzb_qqs_for_dip.data.settings.AppThemeMode
import com.example.uzb_qqs_for_dip.ui.AppViewModel

enum class MenuScreen(val title: String) {
    MAIN("Меню"),
    USERS("Управление профилями"),
    BACKUP("Резервная копия"),
    HELP("Справка и руководство"),
    ABOUT("О приложении")
}

@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад в главное меню",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HierarchicalMenuBottomSheet(
    appViewModel: AppViewModel,
    initialScreen: MenuScreen = MenuScreen.MAIN,
    onDismissRequest: () -> Unit,
    onLoggedOut: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentScreen by remember(initialScreen) { mutableStateOf(initialScreen) }

    val currentUser by appViewModel.currentUser.collectAsStateWithLifecycle()
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()

    BackHandler(enabled = currentScreen != MenuScreen.MAIN) {
        currentScreen = MenuScreen.MAIN
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState != MenuScreen.MAIN) {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 3 },
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180))
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180))
                    )
                }
            },
            label = "MenuScreenTransition"
        ) { screen ->
            when (screen) {
                MenuScreen.MAIN -> {
                    MainMenuContent(
                        user = currentUser,
                        themeMode = themeMode,
                        onSetThemeMode = { appViewModel.setThemeMode(it) },
                        onNavigateToScreen = { currentScreen = it },
                        onSwitchRole = {
                            currentUser?.let { u ->
                                val newRole = if (u.role == UserRole.AUDITOR) UserRole.EMPLOYEE else UserRole.AUDITOR
                                appViewModel.updateProfile(
                                    userId = u.id,
                                    fullName = u.fullName,
                                    position = u.position,
                                    organization = u.organization,
                                    role = newRole
                                )
                            }
                        }
                    )
                }
                MenuScreen.USERS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        SubScreenHeader(
                            title = "Управление профилями",
                            onBack = { currentScreen = MenuScreen.MAIN }
                        )
                        Spacer(Modifier.height(10.dp))
                        UserManagementContent(
                            appViewModel = appViewModel,
                            onUserSelected = {
                                onDismissRequest()
                            },
                            onLoggedOut = onLoggedOut,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                MenuScreen.BACKUP -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        SubScreenHeader(
                            title = "Резервная копия",
                            onBack = { currentScreen = MenuScreen.MAIN }
                        )
                        Spacer(Modifier.height(10.dp))
                        BackupContent(
                            appViewModel = appViewModel,
                            onNavigateToAuth = {
                                onDismissRequest()
                                onLoggedOut()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                MenuScreen.HELP -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        SubScreenHeader(
                            title = "Справка и руководство",
                            onBack = { currentScreen = MenuScreen.MAIN },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        HelpContent(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                MenuScreen.ABOUT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        SubScreenHeader(
                            title = "О приложении",
                            onBack = { currentScreen = MenuScreen.MAIN }
                        )
                        Spacer(Modifier.height(10.dp))
                        AboutAppContent(
                            onDismissRequest = onDismissRequest,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainMenuContent(
    user: User?,
    themeMode: AppThemeMode,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onNavigateToScreen: (MenuScreen) -> Unit,
    onSwitchRole: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val isAuditor = user?.role == UserRole.AUDITOR
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Карточка текущего профиля
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onNavigateToScreen(MenuScreen.USERS) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user?.fullName?.take(1)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.fullName ?: "Гость",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = listOfNotNull(
                        user?.position?.takeIf { it.isNotBlank() },
                        user?.organization?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Сменить профиль",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Сегментированный переключатель тем
        ThemeSelectorRow(
            currentMode = themeMode,
            onModeSelected = { mode ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSetThemeMode(mode)
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        // 1. Строка «Режим работы» с цветовой индикацией
        val roleIcon = if (isAuditor) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Person
        val roleIconColor = if (isAuditor) {
            if (isDark) Color(0xFFEF5350) else Color(0xFFD32F2F)
        } else {
            if (isDark) Color(0xFF90CAF9) else Color(0xFF1976D2)
        }
        val roleContainerColor = if (isAuditor) {
            if (isDark) Color(0xFF3B1212) else Color(0xFFFFEBEE)
        } else {
            if (isDark) Color(0xFF0D253F) else Color(0xFFE3F2FD)
        }
        val roleBadgeBg = if (isAuditor) {
            if (isDark) Color(0xFFE53935) else Color(0xFFD32F2F)
        } else {
            if (isDark) Color(0xFF1E88E5) else Color(0xFF1976D2)
        }

        HierarchicalMenuActionRow(
            icon = roleIcon,
            iconTint = roleIconColor,
            iconContainerColor = roleContainerColor,
            title = "Режим работы",
            subtitle = if (isAuditor) "Переключить на «Сотрудник»" else "Переключить на «Аудитор»",
            badge = {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = roleBadgeBg
                ) {
                    Text(
                        text = if (isAuditor) "Аудитор" else "Сотрудник",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            },
            onClick = onSwitchRole
        )

        // 2. Резервная копия
        HierarchicalMenuActionRow(
            icon = Icons.Outlined.Backup,
            iconTint = MaterialTheme.colorScheme.secondary,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            title = "Резервная копия",
            subtitle = "Экспорт и импорт базы данных",
            onClick = { onNavigateToScreen(MenuScreen.BACKUP) }
        )

        // 3. Справка
        HierarchicalMenuActionRow(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            iconTint = MaterialTheme.colorScheme.primary,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            title = "Справка и руководство",
            subtitle = "Инструкции, 5 способов сканирования",
            onClick = { onNavigateToScreen(MenuScreen.HELP) }
        )

        // 4. О приложении
        HierarchicalMenuActionRow(
            icon = Icons.Outlined.Info,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = "О приложении",
            subtitle = "Версия, контакты и политика",
            onClick = { onNavigateToScreen(MenuScreen.ABOUT) }
        )
    }
}

@Composable
private fun ThemeSelectorRow(
    currentMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Тема оформления",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Сегментированный переключатель [Авто] [Светлая] [Тёмная]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            AppThemeMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                val segmentIcon = when (mode) {
                    AppThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                    AppThemeMode.LIGHT -> Icons.Outlined.LightMode
                    AppThemeMode.DARK -> Icons.Outlined.DarkMode
                }

                val activeBg = if (isDark) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val activeContentColor = if (isDark) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) activeBg else Color.Transparent)
                        .clickable { onModeSelected(mode) }
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = segmentIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) activeContentColor else inactiveContentColor
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = mode.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) activeContentColor else inactiveContentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HierarchicalMenuActionRow(
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color,
    title: String,
    subtitle: String,
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = iconTint
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            badge()
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
