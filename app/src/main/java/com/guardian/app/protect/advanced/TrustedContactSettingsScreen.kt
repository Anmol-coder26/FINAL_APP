package com.guardian.app.protect.advanced

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedContactSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val saved = remember { TrustedContactManager.load(context) }

    var name by remember { mutableStateOf(saved?.name ?: "") }
    var phone by remember { mutableStateOf(saved?.phone ?: "") }
    var enabled by remember { mutableStateOf(saved?.enabled ?: true) }

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
            Column {
                Text("Trusted Contact Auto-Alert", style = GxType.title, color = GxTextHi)
                Text("Emergency SMS dispatch on high scam risk", style = GxType.caption, color = GxTextLo)
            }
        }

        GxCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = GxSurface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Auto-Alert", style = GxType.title, color = GxTextHi)
                        Text("Send automated SMS alert when scam risk exceeds 75%", style = GxType.caption, color = GxTextMid)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GxBase,
                            checkedTrackColor = GxSafe,
                            uncheckedThumbColor = GxTextLo,
                            uncheckedTrackColor = GxSurfaceAlt
                        )
                    )
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name (e.g. Son, Daughter, Caregiver)") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GxSurfaceAlt,
                        unfocusedContainerColor = GxSurfaceAlt,
                        focusedTextColor = GxTextHi,
                        unfocusedTextColor = GxTextHi,
                        focusedIndicatorColor = GxPrimary,
                        unfocusedIndicatorColor = GxBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number (+91...)") },
                    placeholder = { Text("+91 98765 43210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = GxPrimary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GxSurfaceAlt,
                        unfocusedContainerColor = GxSurfaceAlt,
                        focusedTextColor = GxTextHi,
                        unfocusedTextColor = GxTextHi,
                        focusedIndicatorColor = GxPrimary,
                        unfocusedIndicatorColor = GxBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "🔒 Alerts are dispatched directly to your emergency contact. We never sell or share contact numbers.",
                    style = GxType.caption,
                    color = GxTextLo
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        GxButton.Primary(
            text = "Save Trusted Contact",
            icon = Icons.Default.Check,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (phone.isNotBlank()) {
                    TrustedContactManager.save(
                        context,
                        TrustedContactManager.TrustedContact(
                            name = name.ifBlank { "Trusted Contact" },
                            phone = phone.trim(),
                            enabled = enabled
                        )
                    )
                    Toast.makeText(context, "Trusted contact configured successfully", Toast.LENGTH_SHORT).show()
                    onBack()
                } else {
                    Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
