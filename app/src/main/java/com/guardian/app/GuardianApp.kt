package com.guardian.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppScreen(val title: String) {
    Home("Home"),
    Events("Audit Log"),
    Incidents("Threats"),
    Settings("Settings")
}

data class GuardianEvent(
    val title: String,
    val detail: String,
    val time: String,
    val safe: Boolean,
    val category: String = "SYSTEM"
)

data class GuardianIncident(
    val title: String,
    val detail: String,
    val time: String,
    val risk: String,
    val threatType: String = "Fraud Attempt"
)

data class GuardianState(
    val name: String = "",
    val signedIn: Boolean = false,
    val onboardingComplete: Boolean = false,
    val protectionEnabled: Boolean = true,
    val phoneProtection: Boolean = true,
    val messageProtection: Boolean = true,
    val linkProtection: Boolean = true,
    val events: List<GuardianEvent> = listOf(
        GuardianEvent("Real-Time Shield Online", "Speakerphone & SMS threat detectors are running", "Just now", true, "SHIELD"),
        GuardianEvent("Continuous STT Initialized", "English, Hindi & Hinglish models ready", "Just now", true, "SPEECH"),
        GuardianEvent("Background Protection Active", "Monitoring incoming calls and message payloads", "Today", true, "MONITOR")
    ),
    val incidents: List<GuardianIncident> = emptyList()
)

@Composable
fun GuardianApp(
    onOpenCallRisk: () -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    onPhoneProtectionToggle: (Boolean) -> Unit = {},
    onMessageProtectionToggle: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var state by remember {
        mutableStateOf(
            GuardianState(
                name = SessionManager.getUserName(context).ifBlank { "Guardian User" },
                signedIn = SessionManager.isLoggedIn(context),
                onboardingComplete = SessionManager.isOnboardingComplete(context)
            )
        )
    }
    var screen by remember { mutableStateOf(AppScreen.Home) }
    var showCallHistory by remember { mutableStateOf(false) }

    when {
        !state.signedIn -> AuthScreen(
            onContinue = { name ->
                val finalName = name.ifBlank { "Guardian User" }
                SessionManager.saveSession(context, finalName, "user@guardian.defense")
                state = state.copy(name = finalName, signedIn = true)
            }
        )

        !state.onboardingComplete -> OnboardingScreen(
            name = state.name,
            onComplete = {
                SessionManager.setOnboardingComplete(context, true)
                state = state.copy(onboardingComplete = true)
            }
        )

        showCallHistory -> com.guardian.app.callprotect.CallHistoryScreen(
            onBack = { showCallHistory = false }
        )

        else -> Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = com.guardian.app.ui.theme.GxBase,
                    contentColor = com.guardian.app.ui.theme.GxTextHi,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(width = 1.dp, color = com.guardian.app.ui.theme.GxBorder)
                ) {
                    NavigationBarItem(
                        selected = screen == AppScreen.Home,
                        onClick = { screen = AppScreen.Home },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(20.dp)) },
                        label = { Text("Home", style = com.guardian.app.ui.theme.GxType.caption) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.guardian.app.ui.theme.GxPrimary,
                            selectedTextColor = com.guardian.app.ui.theme.GxPrimary,
                            indicatorColor = com.guardian.app.ui.theme.GxSurfaceAlt,
                            unselectedIconColor = com.guardian.app.ui.theme.GxTextLo,
                            unselectedTextColor = com.guardian.app.ui.theme.GxTextLo
                        )
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.Events,
                        onClick = { screen = AppScreen.Events },
                        icon = { Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = "Events", modifier = Modifier.size(20.dp)) },
                        label = { Text("Audit Log", style = com.guardian.app.ui.theme.GxType.caption) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.guardian.app.ui.theme.GxPrimary,
                            selectedTextColor = com.guardian.app.ui.theme.GxPrimary,
                            indicatorColor = com.guardian.app.ui.theme.GxSurfaceAlt,
                            unselectedIconColor = com.guardian.app.ui.theme.GxTextLo,
                            unselectedTextColor = com.guardian.app.ui.theme.GxTextLo
                        )
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.Incidents,
                        onClick = { screen = AppScreen.Incidents },
                        icon = { Icon(Icons.Default.WarningAmber, contentDescription = "Incidents", modifier = Modifier.size(20.dp)) },
                        label = { Text("Threats", style = com.guardian.app.ui.theme.GxType.caption) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.guardian.app.ui.theme.GxDanger,
                            selectedTextColor = com.guardian.app.ui.theme.GxDanger,
                            indicatorColor = com.guardian.app.ui.theme.GxDangerSoft,
                            unselectedIconColor = com.guardian.app.ui.theme.GxTextLo,
                            unselectedTextColor = com.guardian.app.ui.theme.GxTextLo
                        )
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.Settings,
                        onClick = { screen = AppScreen.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp)) },
                        label = { Text("Settings", style = com.guardian.app.ui.theme.GxType.caption) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.guardian.app.ui.theme.GxPrimary,
                            selectedTextColor = com.guardian.app.ui.theme.GxPrimary,
                            indicatorColor = com.guardian.app.ui.theme.GxSurfaceAlt,
                            unselectedIconColor = com.guardian.app.ui.theme.GxTextLo,
                            unselectedTextColor = com.guardian.app.ui.theme.GxTextLo
                        )
                    )
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "guardian-screen-transition",
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.guardian.app.ui.theme.GxBase)
                    .padding(padding)
            ) { target ->
                val contentPadding = PaddingValues()
                when (target) {
                    AppScreen.Home -> HomeScreen(
                        state = state,
                        contentPadding = contentPadding,
                        onOpenCallRisk = onOpenCallRisk,
                        onOpenQrScanner = onOpenQrScanner,
                        onOpenCallHistory = { showCallHistory = true },
                        onProtectionChange = { enabled ->
                            state = state.copy(
                                protectionEnabled = enabled,
                                events = listOf(
                                    GuardianEvent(
                                        if (enabled) "Shield Protection Re-enabled" else "Shield Protection Paused",
                                        if (enabled) "Real-time scam filtering active across all channels"
                                        else "On-phone sensor listeners are in standby",
                                        "Just now",
                                        enabled,
                                        "STATE"
                                    )
                                ) + state.events
                            )
                        },
                        onAddIncident = { incident ->
                            state = state.copy(
                                incidents = listOf(incident) + state.incidents,
                                events = listOf(
                                    GuardianEvent("Threat Flagged", incident.title, "Just now", false, "ALERT")
                                ) + state.events
                            )
                            screen = AppScreen.Incidents
                        }
                    )

                    AppScreen.Events -> EventsScreen(
                        events = state.events,
                        contentPadding = contentPadding
                    )

                    AppScreen.Incidents -> IncidentsScreen(
                        incidents = state.incidents,
                        contentPadding = contentPadding,
                        onAddIncident = { incident ->
                            state = state.copy(incidents = listOf(incident) + state.incidents)
                        }
                    )

                    AppScreen.Settings -> SettingsScreen(
                        state = state,
                        contentPadding = contentPadding,
                        onToggle = { type, enabled ->
                            state = when (type) {
                                SettingType.All -> state.copy(
                                    protectionEnabled = enabled,
                                    phoneProtection = enabled,
                                    messageProtection = enabled
                                )
                                SettingType.Phone -> state.copy(phoneProtection = enabled)
                                SettingType.Messages -> state.copy(messageProtection = enabled)
                                SettingType.Links -> state.copy(linkProtection = enabled)
                            }
                            when (type) {
                                SettingType.All -> {
                                    onPhoneProtectionToggle(enabled)
                                    onMessageProtectionToggle(enabled)
                                }
                                SettingType.Phone -> onPhoneProtectionToggle(enabled)
                                SettingType.Messages -> onMessageProtectionToggle(enabled)
                                SettingType.Links -> Unit
                            }
                        },
                        onDeleteData = {
                            state = GuardianState(name = state.name, signedIn = true, onboardingComplete = true)
                            screen = AppScreen.Home
                        },
                        onLogout = {
                            SessionManager.clearSession(context)
                            state = GuardianState(signedIn = false, onboardingComplete = false)
                            screen = AppScreen.Home
                        }
                    )
                }
            }
        }
    }
}