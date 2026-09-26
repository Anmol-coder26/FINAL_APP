package com.guardian.app.protect.advanced

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardian.app.callprotect.GuardianDatabase
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.components.GxChip
import com.guardian.app.ui.components.GxChipVariant
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProfilesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles = remember { mutableStateListOf<ContactProfile>() }

    fun refreshProfiles() {
        scope.launch(Dispatchers.IO) {
            val list = GuardianDatabase.getInstance(context).contactProfileDao().getAll()
            withContext(Dispatchers.Main) {
                profiles.clear()
                profiles.addAll(list)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshProfiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GxTextHi)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Contact Identity Profiles", style = GxType.title, color = GxTextHi)
                Text("Baseline behavior models for trusted contacts", style = GxType.caption, color = GxTextLo)
            }
        }

        if (profiles.isEmpty()) {
            GxCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GxSurface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No Contact Profiles Yet", style = GxType.title, color = GxTextHi)
                    Text(
                        "Add trusted contacts to model their normal calling hours and detect AI voice cloning or spoofed behavior.",
                        style = GxType.body,
                        color = GxTextMid
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(profiles) { profile ->
                    GxCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GxSurface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(profile.displayName, style = GxType.title, color = GxTextHi)
                                    GxChip(text = "${profile.callCount} calls", variant = GxChipVariant.Neutral)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(profile.number, style = GxType.mono, color = GxPrimary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Usual hours: ${profile.usualCallHoursStart}:00 - ${profile.usualCallHoursEnd}:00 • Never asks for OTP/money",
                                    style = GxType.caption,
                                    color = GxTextLo
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        GuardianDatabase.getInstance(context).contactProfileDao().delete(profile.number)
                                        refreshProfiles()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = GxDanger)
                            }
                        }
                    }
                }
            }
        }

        GxButton.Primary(
            text = "Seed Demo Mom Profile",
            icon = Icons.Default.Add,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    DemoSeed.seedDemoData(context)
                    refreshProfiles()
                    Toast.makeText(context, "Seeded Mom demo profile", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
