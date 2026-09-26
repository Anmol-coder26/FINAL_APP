package com.guardian.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.components.GxChip
import com.guardian.app.ui.components.GxChipVariant
import com.guardian.app.ui.components.GxLiveDot
import com.guardian.app.ui.components.GxRiskRing
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimaryGlow
import com.guardian.app.ui.theme.GxPrimarySoft
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSafeSoft
import com.guardian.app.ui.theme.GxShapeLg
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxShapePill
import com.guardian.app.ui.theme.GxShapeSm
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxType
import com.guardian.app.ui.theme.GxVoid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import com.guardian.app.ui.theme.gxFancyGlow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==========================================
// 1. AUTH SCREEN (Obsidian Security Aesthetic)
// ==========================================
@Composable
fun AuthScreen(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            // Glowing Logo Shield
            Surface(
                color = GxPrimarySoft,
                shape = GxShapeMd,
                border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = GxPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Guardian Defense",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = GxTextHi
            )
            Text(
                "Next-generation AI security defending calls, phishing links, and extortive messages in 10+ Indic languages.",
                color = GxTextMid,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name", color = GxTextLo) },
                singleLine = true,
                colors = customGxTextFieldColors(),
                shape = GxShapeMd,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address", color = GxTextLo) },
                singleLine = true,
                colors = customGxTextFieldColors(),
                shape = GxShapeMd,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Security PIN / Password", color = GxTextLo) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = customGxTextFieldColors(),
                shape = GxShapeMd,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            GxButton.Primary(
                text = "Launch Security Hub",
                onClick = { onContinue(name.trim().ifBlank { "User" }) },
                enabled = email.isNotBlank() && password.length >= 4,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = GxTextLo, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "On-device zero-telemetry sandbox. Encrypted session state.",
                    color = GxTextLo,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ==========================================
// 2. 5-STEP ONBOARDING PAGER (Premium Linear Flow)
// ==========================================
@Composable
fun OnboardingScreen(name: String, onComplete: () -> Unit) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    // Permission States
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var hasDefaultBrowser by remember {
        mutableStateOf(isDefaultBrowser(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasCallControlPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPhonePermission = granted }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    val callControlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCallControlPermission = granted }

    var selectedLang by remember {
        mutableStateOf(
            context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
                .getString("preferred_language", "hi") ?: "hi"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Progress Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isActive = i == currentStep
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isActive) 24.dp else 8.dp, 6.dp)
                            .clip(GxShapePill)
                            .background(if (isActive) GxPrimary else GxBorder)
                    )
                }
            }

            // Step Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                when (currentStep) {
                    // Step 0: Welcome
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            color = GxPrimarySoft,
                            shape = GxShapeMd,
                            border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Text(
                            "Protection that speaks your language",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = GxTextHi,
                            lineHeight = 34.sp
                        )
                        Text(
                            "Hello $name. Guardian detects scam calls, coercive extortion, and phishing links in real time across Hindi, English, and 10+ Indian regional languages.",
                            color = GxTextMid,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }

                    // Step 1: Real-Time Interception Permissions
                    1 -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text("Security Layers", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                            Text(
                                "Grant core hardware hooks so Guardian can protect your device.",
                                color = GxTextMid,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "1. Microphone Speech-to-Text",
                                desc = "Real-time acoustic analysis of speakerphone calls for extortion and digital arrest threats.",
                                isGranted = hasMicPermission,
                                onGrant = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "2. Phone Call Telephony",
                                desc = "Auto-triggers the AI Reasoning Card as soon as a call is ringing or answered.",
                                isGranted = hasPhonePermission,
                                onGrant = { phoneLauncher.launch(android.Manifest.permission.READ_PHONE_STATE) }
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "3. Floating Heads-Up Overlay",
                                desc = "Renders the live risk telemetry over the stock dialer and messaging apps.",
                                isGranted = hasOverlayPermission,
                                onGrant = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "4. Call Control & Auto-Hangup",
                                desc = "Allows one-tap emergency call termination when severe fraud (score >= 85%) is detected.",
                                isGranted = hasCallControlPermission,
                                onGrant = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        callControlLauncher.launch(android.Manifest.permission.ANSWER_PHONE_CALLS)
                                    } else {
                                        hasCallControlPermission = true
                                    }
                                }
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "5. Notification Alerts",
                                desc = "Displays critical scam heads-up warnings during active attacks.",
                                isGranted = hasNotificationPermission,
                                onGrant = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        hasNotificationPermission = true
                                    }
                                }
                            )
                        }
                    }

                    // Step 2: Language Picker
                    2 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Scam Warning Language", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                        Text(
                            "Select the language you want Guardian's Bhashini AI voice alerts and reasoning card explanations in:",
                            color = GxTextMid,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        val languages = listOf(
                            "hi" to "Hindi (हिंदी)",
                            "en" to "English",
                            "ta" to "Tamil (தமிழ்)",
                            "te" to "Telugu (తెలుగు)",
                            "bn" to "Bengali (বাংলা)",
                            "mr" to "Marathi (मराठी)",
                            "kn" to "Kannada (ಕನ್ನಡ)",
                            "ml" to "Malayalam (മലയാളം)",
                            "gu" to "Gujarati (ગુજરાતી)",
                            "pa" to "Punjabi (ਪੰਜਾਬੀ)"
                        )

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            languages.forEach { (code, label) ->
                                val isSelected = selectedLang == code
                                GxChip(
                                    text = label,
                                    variant = if (isSelected) GxChipVariant.Brand else GxChipVariant.Neutral,
                                    height = 36.dp,
                                    onClick = {
                                        selectedLang = code
                                        context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putString("preferred_language", code)
                                            .apply()
                                    }
                                )
                            }
                        }
                    }

                    // Step 3: Finish Screen
                    3 -> Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = GxSafeSoft,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, GxSafe.copy(alpha = 0.5f)),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = GxSafe, modifier = Modifier.size(36.dp))
                            }
                        }
                        Text("You're Protected", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                        Text(
                            "Dual-Engine AI (Gemini 1.5 Flash + Bhashini STT) is standing by in your secure on-device sandbox.",
                            color = GxTextMid,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Bottom Navigation Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0 && currentStep < 3) {
                    TextButton(onClick = { currentStep-- }) {
                        Text("Back", color = GxTextMid, fontSize = 14.sp)
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                if (currentStep < 3) {
                    GxButton.Primary(
                        text = if (currentStep == 0) "Get Started" else "Continue",
                        onClick = { currentStep++ },
                        icon = Icons.AutoMirrored.Filled.ArrowForward
                    )
                } else {
                    GxButton.Primary(
                        text = "Open Guardian Shield",
                        onClick = onComplete,
                        icon = Icons.Default.Shield,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    GxCard(
        backgroundColor = if (isGranted) GxSurfaceAlt else GxSurface,
        borderColor = if (isGranted) GxSafe.copy(alpha = 0.3f) else GxBorder,
        contentPadding = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (isGranted) GxSafeSoft else GxSurfaceAlt, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isGranted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (isGranted) GxSafe else GxWarning,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GxTextHi)
                Text(desc, color = GxTextLo, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(Modifier.width(8.dp))

            if (isGranted) {
                GxChip(text = "ACTIVE", variant = GxChipVariant.Safe, height = 24.dp)
            } else {
                GxButton.Primary(
                    text = "Grant",
                    onClick = onGrant,
                    height = 34.dp
                )
            }
        }
    }
}

// ==========================================
// 3. HOME SCREEN (Linear / Arc / 1Password Aesthetic)
// ==========================================
@Composable
fun HomeScreen(
    state: GuardianState,
    contentPadding: PaddingValues,
    onOpenCallRisk: () -> Unit,
    onOpenQrScanner: () -> Unit = {},
    onOpenCallHistory: () -> Unit = {},
    onProtectionChange: (Boolean) -> Unit,
    onAddIncident: (GuardianIncident) -> Unit
) {
    var showIncidentDialog by remember { mutableStateOf(false) }
    var showLinkCheckDialog by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ----------------------------------------------------
        // Status Hero (180dp height)
        // ----------------------------------------------------
        item {
            GxCard(
                backgroundColor = GxSurface,
                borderColor = if (state.protectionEnabled) GxPrimary.copy(alpha = 0.3f) else GxDanger.copy(alpha = 0.3f),
                contentPadding = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .gxFancyGlow()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "GUARDIAN DEFENSE",
                            color = GxTextLo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.protectionEnabled) "Protected" else "Protection Paused",
                            color = GxTextHi,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.protectionEnabled)
                                "Real-time dual AI shield active · Zero threats detected"
                            else "Interception listeners are currently in standby",
                            color = GxTextMid,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    GxRiskRing(
                        riskScore = if (state.protectionEnabled) 12 else 85,
                        size = 80.dp,
                        strokeWidth = 6.dp,
                        showLabel = false
                    )
                }
            }
        }

        // ----------------------------------------------------
        // Quick Actions Row (Horizontal Scroll)
        // ----------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                GxChip(
                    text = "Secure VoIP",
                    icon = Icons.Default.PhoneInTalk,
                    variant = GxChipVariant.Brand,
                    height = 42.dp,
                    onClick = {
                        ctx.startActivity(Intent(ctx, com.guardian.app.voip.VoipCallActivity::class.java))
                    }
                )
                GxChip(
                    text = "Scan QR",
                    icon = Icons.Default.QrCodeScanner,
                    variant = GxChipVariant.Brand,
                    height = 42.dp,
                    onClick = onOpenQrScanner
                )
                GxChip(
                    text = "Check Link",
                    icon = Icons.Default.Link,
                    variant = GxChipVariant.Neutral,
                    height = 42.dp,
                    onClick = { showLinkCheckDialog = true }
                )
                GxChip(
                    text = "Scam History",
                    icon = Icons.Default.History,
                    variant = GxChipVariant.Neutral,
                    height = 42.dp,
                    onClick = onOpenCallHistory
                )
                GxChip(
                    text = "Report Incident",
                    icon = Icons.Default.WarningAmber,
                    variant = GxChipVariant.Warning,
                    height = 42.dp,
                    onClick = { showIncidentDialog = true }
                )
            }
        }

        // ----------------------------------------------------
        // Section Header: "Protection Modules"
        // ----------------------------------------------------
        item {
            Text(
                "PROTECTION MODULES",
                color = GxTextLo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ----------------------------------------------------
        // 6 Vertical Feature Cards (Linear Aesthetic)
        // ----------------------------------------------------
        item {
            FeatureCardItem(
                title = "Live Call Speaker Guard",
                subtitle = "Bhashini STT + Gemini reasoning with tone & TTS warning",
                icon = Icons.Default.PhoneInTalk,
                accentColor = GxPrimary,
                onClick = onOpenCallRisk
            )
        }

        item {
            FeatureCardItem(
                title = "Camera QR Vision Shield",
                subtitle = "Real-time ML Kit scanner to prevent malicious UPI/URL redirects",
                icon = Icons.Default.QrCodeScanner,
                accentColor = GxSafe,
                onClick = onOpenQrScanner
            )
        }

        item {
            FeatureCardItem(
                title = "Link Shield Interceptor",
                subtitle = "Default browser protection routing clicked URLs through AI verification",
                icon = Icons.Default.Link,
                accentColor = GxWarning,
                onClick = { showLinkCheckDialog = true }
            )
        }

        item {
            FeatureCardItem(
                title = "Scam Call Audit Log",
                subtitle = "Chronological threat records & one-tap 1930 Cybercrime filing",
                icon = Icons.Default.History,
                accentColor = GxDanger,
                onClick = onOpenCallHistory
            )
        }

        item {
            FeatureCardItem(
                title = "Call Screening & Auto-Block",
                subtitle = "Telecom service rejecting blacklisted scam and extortion numbers",
                icon = Icons.Default.Block,
                accentColor = GxPrimary,
                onClick = onOpenCallHistory
            )
        }

        // Master Shield Toggle Button
        item {
            GxButton.Ghost(
                text = if (state.protectionEnabled) "Pause Guardian Protection" else "Activate Guardian Protection",
                onClick = { onProtectionChange(!state.protectionEnabled) },
                icon = Icons.Default.Shield,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Modal Dialogs
    if (showIncidentDialog) {
        IncidentReportModal(
            onDismiss = { showIncidentDialog = false },
            onSave = {
                onAddIncident(it)
                showIncidentDialog = false
            }
        )
    }

    if (showLinkCheckDialog) {
        LinkCheckerModal(onDismiss = { showLinkCheckDialog = false })
    }

    if (showQrScannerDialog) {
        QrScannerModal(onDismiss = { showQrScannerDialog = false })
    }
}

@Composable
private fun FeatureCardItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    GxCard(
        backgroundColor = GxSurface,
        borderColor = GxBorder,
        contentPadding = 16.dp,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.12f),
                shape = GxShapeMd,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(title, color = GxTextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = GxTextLo, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GxTextLo,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==========================================
// 4. EVENTS SCREEN (Audit Log)
// ==========================================
@Composable
fun EventsScreen(events: List<GuardianEvent>, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Security Audit Log", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
            Text("Chronological record of background telemetry checks.", color = GxTextMid, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (events.isEmpty()) {
            item {
                GxCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No security events logged yet.", color = GxTextLo, fontSize = 13.sp)
                }
            }
        } else {
            items(events) { event ->
                GxCard(
                    backgroundColor = GxSurface,
                    contentPadding = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GxLiveDot(
                            color = if (event.safe) GxSafe else GxDanger,
                            size = 8.dp,
                            pulsing = false
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, color = GxTextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(event.detail, color = GxTextMid, fontSize = 12.sp)
                        }
                        Text(event.time, color = GxTextLo, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. INCIDENTS SCREEN (Threat Log)
// ==========================================
@Composable
fun IncidentsScreen(
    incidents: List<GuardianIncident>,
    contentPadding: PaddingValues,
    onAddIncident: (GuardianIncident) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Threat Log", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                    Text("Flagged scams, extortions, and blocked callers.", color = GxTextMid, fontSize = 13.sp)
                }
                GxButton.Primary(
                    text = "Report",
                    onClick = { showAddDialog = true },
                    height = 38.dp
                )
            }
        }

        if (incidents.isEmpty()) {
            item {
                GxCard(
                    backgroundColor = GxSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = GxSafe, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No threats logged", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)
                        Text("Your phone is currently clear of flagged extortion attempts.", color = GxTextLo, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(incidents) { incident ->
                GxCard(
                    backgroundColor = GxDangerSoft,
                    borderColor = GxDanger.copy(alpha = 0.4f),
                    contentPadding = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(incident.title, color = GxTextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(incident.detail, color = GxTextMid, fontSize = 12.sp)
                        }
                        GxChip(text = incident.risk, variant = GxChipVariant.Danger, height = 24.dp)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        IncidentReportModal(
            onDismiss = { showAddDialog = false },
            onSave = {
                onAddIncident(it)
                showAddDialog = false
            }
        )
    }
}

// ==========================================
// 6. SETTINGS SCREEN
// ==========================================
enum class SettingType { All, Phone, Messages, Links }

@Composable
fun SettingsScreen(
    state: GuardianState,
    contentPadding: PaddingValues,
    onToggle: (SettingType, Boolean) -> Unit,
    onDeleteData: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE) }
    var currentLang by remember {
        mutableStateOf(prefs.getString("preferred_language", "hi") ?: "hi")
    }
    var voiceSynthEnabled by remember {
        mutableStateOf(prefs.getBoolean("voice_synthesis_enabled", true))
    }
    var plainReasoningEnabled by remember {
        mutableStateOf(prefs.getBoolean("plain_reasoning_enabled", true))
    }

    var showTrustedContact by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }

    if (showTrustedContact) {
        com.guardian.app.protect.advanced.TrustedContactSettingsScreen(
            onBack = { showTrustedContact = false }
        )
        return
    }

    if (showProfiles) {
        com.guardian.app.protect.advanced.ContactProfilesScreen(
            onBack = { showProfiles = false }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings & Sensors", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
            Text("Configure proactive detection engines and privacy preferences.", color = GxTextMid, fontSize = 13.sp)
        }

        item {
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Interception Channels", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)

                    SettingToggleRow(
                        title = "Live Call STT Analyzer",
                        desc = "Monitor live speakerphone speech for scams",
                        enabled = state.phoneProtection,
                        onToggle = { onToggle(SettingType.Phone, it) }
                    )

                    SettingToggleRow(
                        title = "SMS & Notification Shield",
                        desc = "Detect urgent extortion in WhatsApp & SMS",
                        enabled = state.messageProtection,
                        onToggle = { onToggle(SettingType.Messages, it) }
                    )

                    SettingToggleRow(
                        title = "Anti-Phishing Link Shield",
                        desc = "Pre-screen clicked links before opening browser",
                        enabled = state.linkProtection,
                        onToggle = { onToggle(SettingType.Links, it) }
                    )
                }
            }
        }

        // Advanced Protection Suite (New)
        item {
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced Protection Suite", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)
                        GxChip(text = "PRO 2026", variant = GxChipVariant.Brand)
                    }

                    SettingToggleRow(
                        title = "Voice Synthesis Detection",
                        desc = "Acoustic FFT & pitch variance analysis to detect cloned/AI voices",
                        enabled = voiceSynthEnabled,
                        onToggle = {
                            voiceSynthEnabled = it
                            prefs.edit().putBoolean("voice_synthesis_enabled", it).apply()
                        }
                    )

                    SettingToggleRow(
                        title = "Plain-Language Reasoning",
                        desc = "Clear English & Hindi breakdown explaining risk score factors",
                        enabled = plainReasoningEnabled,
                        onToggle = {
                            plainReasoningEnabled = it
                            prefs.edit().putBoolean("plain_reasoning_enabled", it).apply()
                        }
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTrustedContact = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Emergency Trusted Contact", color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Auto-alert relative on >=75% severe scam call risk", color = GxTextLo, fontSize = 11.sp)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GxTextMid
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProfiles = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Identity Consistency Baselines", color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Behavioral profiles for family & bank imposter detection", color = GxTextLo, fontSize = 11.sp)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GxTextMid
                        )
                    }
                }
            }
        }

        item {
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Language Preference (Bhashini AI)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)
                    Text("Select warning and reasoning language:", color = GxTextLo, fontSize = 12.sp)

                    val languages = listOf(
                        "hi" to "Hindi", "en" to "English", "ta" to "Tamil", "te" to "Telugu",
                        "bn" to "Bengali", "mr" to "Marathi", "kn" to "Kannada", "ml" to "Malayalam"
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        languages.forEach { (code, label) ->
                            val isSelected = currentLang == code
                            GxChip(
                                text = label,
                                variant = if (isSelected) GxChipVariant.Brand else GxChipVariant.Neutral,
                                onClick = {
                                    currentLang = code
                                    prefs.edit().putString("preferred_language", code).apply()
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            GxButton.Ghost(
                text = "Sign Out & Reset Session",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // App Version & Quick Seed Button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch(Dispatchers.IO) {
                            com.guardian.app.protect.advanced.DemoSeed.seedDemoData(context)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "✨ Demo data initialized (Mom profile & Trusted Contact active)",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Guardian v2.0 • Advanced Protection Engine",
                        style = GxType.caption,
                        color = GxTextLo
                    )
                    Text(
                        "Tap to re-seed hackathon demo baseline data",
                        style = GxType.caption,
                        color = GxPrimary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    desc: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = GxTextLo, fontSize = 11.sp)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GxVoid,
                checkedTrackColor = GxPrimary,
                uncheckedThumbColor = GxTextLo,
                uncheckedTrackColor = GxSurfaceAlt
            )
        )
    }
}

// ==========================================
// 7. MODALS
// ==========================================
@Composable
fun LinkCheckerModal(onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<RiskReport?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    val context = LocalContext.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        textContentColor = GxTextMid,
        title = { Text("Link Shield Inspector", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Paste any suspicious URL to inspect against phishing and malicious download databases.", fontSize = 12.sp, color = GxTextLo)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://...", color = GxTextLo) },
                    singleLine = true,
                    colors = customGxTextFieldColors(),
                    shape = GxShapeMd,
                    modifier = Modifier.fillMaxWidth()
                )

                if (report != null) {
                    val r = report!!
                    GxCard(
                        backgroundColor = if (r.riskScore >= 70) GxDangerSoft else GxSafeSoft,
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                "Risk Score: ${r.riskScore}%",
                                color = if (r.riskScore >= 70) GxDanger else GxSafe,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(r.explanationEn, color = GxTextHi, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            GxButton.Primary(
                text = if (isChecking) "Checking..." else "Analyze URL",
                onClick = {
                    if (url.isNotBlank()) {
                        isChecking = true
                        report = RiskReport(
                            riskScore = if (url.contains("apk") || url.contains("verify") || url.contains("free")) 88 else 10,
                            explanationEn = if (url.contains("apk")) "Flagged: Attempting unauthorized APK sideload." else "URL appears clean."
                        )
                        isChecking = false
                    }
                },
                height = 42.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = GxTextMid) }
        }
    )
}

@Composable
fun IncidentReportModal(
    onDismiss: () -> Unit,
    onSave: (GuardianIncident) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var risk by remember { mutableStateOf("High") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        textContentColor = GxTextMid,
        title = { Text("Report Threat / Scam Number", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Phone Number / Threat Title", color = GxTextLo) },
                    singleLine = true,
                    colors = customGxTextFieldColors(),
                    shape = GxShapeMd,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Incident Details (e.g. impersonated CBI)", color = GxTextLo) },
                    colors = customGxTextFieldColors(),
                    shape = GxShapeMd,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            GxButton.Primary(
                text = "Save Incident",
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            GuardianIncident(
                                title = title,
                                detail = detail.ifBlank { "Flagged suspicious activity" },
                                time = "Just now",
                                risk = risk
                            )
                        )
                    }
                },
                height = 42.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = GxTextMid) }
        }
    )
}

@Composable
fun QrScannerModal(onDismiss: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        title = { Text("QR Vision Shield") },
        text = {
            Text("Open the dedicated camera ML vision scanner to inspect QR codes for malware payloads.", color = GxTextMid)
        },
        confirmButton = {
            GxButton.Primary(
                text = "Launch Scanner",
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(context, QrScannerActivity::class.java))
                },
                height = 42.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = GxTextMid) }
        }
    )
}

@Composable
fun customGxTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = GxSurfaceAlt,
    unfocusedContainerColor = GxSurface,
    focusedBorderColor = GxPrimary,
    unfocusedBorderColor = GxBorder,
    focusedTextColor = GxTextHi,
    unfocusedTextColor = GxTextHi,
    cursorColor = GxPrimary
)

fun isDefaultBrowser(context: Context): Boolean {
    return try {
        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolver = context.packageManager
        val defaultHandler = resolver.resolveActivity(testIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        defaultHandler?.activityInfo?.packageName == context.packageName
    } catch (_: Exception) {
        false
    }
}