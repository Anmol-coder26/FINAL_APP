package com.guardian.app.callprotect

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.components.GxChip
import com.guardian.app.ui.components.GxChipVariant
import com.guardian.app.ui.components.GxHeader
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimarySoft
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSafeSoft
import com.guardian.app.ui.theme.GxShapeLg
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxShapePill
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val historyEntries = remember { mutableStateListOf<CallHistoryEntry>() }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var activeModalEntry by remember { mutableStateOf<CallHistoryEntry?>(null) }

    fun refreshList() {
        scope.launch {
            val repo = NumberReputationRepository(context)
            val list = repo.getAllHistory()
            historyEntries.clear()
            historyEntries.addAll(list)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    val filteredEntries = historyEntries.filter { entry ->
        val matchesSearch = entry.number.contains(searchQuery, ignoreCase = true) ||
            entry.topSignals.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "High Risk" -> entry.riskScore >= 70
            "Safe" -> entry.riskScore < 40
            "Blocked" -> entry.actionTaken.contains("block", ignoreCase = true)
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GxHeader(
                title = "Call Audit Log",
                onBack = onBack,
                actionContent = {
                    Text(
                        "${historyEntries.size} calls",
                        color = GxPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            // Search Bar & Filter Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search caller number or threat...", color = GxTextLo, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = GxTextLo, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GxSurfaceAlt,
                        unfocusedContainerColor = GxSurface,
                        focusedBorderColor = GxPrimary,
                        unfocusedBorderColor = GxBorder,
                        focusedTextColor = GxTextHi,
                        unfocusedTextColor = GxTextHi
                    ),
                    shape = GxShapeMd,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "High Risk", "Safe", "Blocked").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        GxChip(
                            text = filter,
                            variant = if (isSelected) GxChipVariant.Brand else GxChipVariant.Neutral,
                            onClick = { selectedFilter = filter },
                            height = 30.dp
                        )
                    }
                }
            }

            // Call List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GxPrimary, strokeWidth = 2.dp)
                }
            } else if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = GxSurfaceAlt,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.History, contentDescription = null, tint = GxTextLo, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("No calls analyzed yet", color = GxTextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Calls received while Guardian is active will be audited here with full transcripts.",
                            color = GxTextLo,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEntries) { entry ->
                        CallHistoryRow(
                            entry = entry,
                            onClick = { activeModalEntry = entry }
                        )
                    }
                }
            }
        }
    }

    // Modal Details Sheet
    if (activeModalEntry != null) {
        CallDetailModal(
            entry = activeModalEntry!!,
            onDismiss = { activeModalEntry = null },
            onBlock = {
                val ok = CallActionHelper.blockNumber(context, activeModalEntry!!.number)
                if (ok) {
                    Toast.makeText(context, "Number blocked & reputation updated", Toast.LENGTH_SHORT).show()
                    refreshList()
                }
            },
            onReport = {
                ReportToCybercrime.report(context, activeModalEntry!!)
            }
        )
    }
}

@Composable
private fun CallHistoryRow(
    entry: CallHistoryEntry,
    onClick: () -> Unit
) {
    val isHighRisk = entry.riskScore >= 70
    val isWarning = entry.riskScore in 40..69
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(entry.timestamp))

    GxCard(
        backgroundColor = GxSurface,
        borderColor = GxBorder,
        contentPadding = 14.dp,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Avatar Circle in risk color
            Surface(
                color = when {
                    isHighRisk -> GxDangerSoft
                    isWarning -> GxWarningSoft
                    else -> GxSafeSoft
                },
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    when {
                        isHighRisk -> GxDanger.copy(alpha = 0.4f)
                        isWarning -> GxWarning.copy(alpha = 0.4f)
                        else -> GxSafe.copy(alpha = 0.4f)
                    }
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isHighRisk) Icons.Default.ReportProblem else Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = when {
                            isHighRisk -> GxDanger
                            isWarning -> GxWarning
                            else -> GxSafe
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.number.ifBlank { "Unknown Caller" },
                    color = GxTextHi,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (entry.topSignals.isNotBlank()) entry.topSignals else "Speech audited clean",
                    color = GxTextMid,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                Text(
                    text = formattedDate,
                    color = GxTextLo,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.width(8.dp))

            GxChip(
                text = "${entry.riskScore}%",
                variant = when {
                    isHighRisk -> GxChipVariant.Danger
                    isWarning -> GxChipVariant.Warning
                    else -> GxChipVariant.Safe
                },
                height = 26.dp
            )
        }
    }
}

@Composable
private fun CallDetailModal(
    entry: CallHistoryEntry,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(entry.timestamp))
    val isHighRisk = entry.riskScore >= 70

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Call Audit: ${entry.number}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = GxTextHi
                )
                Spacer(Modifier.weight(1f))
                GxChip(
                    text = "${entry.riskScore}% RISK",
                    variant = if (isHighRisk) GxChipVariant.Danger else GxChipVariant.Safe
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(formattedDate, color = GxTextLo, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                if (entry.topSignals.isNotBlank()) {
                    item {
                        GxCard(
                            backgroundColor = GxSurfaceAlt,
                            contentPadding = 10.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Primary Vector / Signals:", color = GxTextLo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(entry.topSignals, color = if (isHighRisk) GxDanger else GxSafe, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                item {
                    Text("Call Transcript Snippet", color = GxTextMid, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    GxCard(
                        backgroundColor = GxSurfaceAlt,
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            entry.transcriptSummary.ifBlank { "No speech recorded." },
                            color = GxTextHi,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GxButton.Danger(
                            text = "Report 1930",
                            onClick = onReport,
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            modifier = Modifier.weight(1f),
                            height = 42.dp
                        )
                        GxButton.Ghost(
                            text = "Block",
                            onClick = onBlock,
                            icon = Icons.Default.Block,
                            modifier = Modifier.weight(1f),
                            height = 42.dp
                        )
                    }
                }

                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    GxButton.Primary(
                        text = "Generate Report",
                        onClick = {
                            val intent = android.content.Intent(context, com.guardian.app.evidence.EvidenceCaptureActivity::class.java).apply {
                                putExtra("riskScore", entry.riskScore)
                                putExtra("callerId", entry.number)
                                putExtra("packageName", "Guardian Call Audit")
                                putExtra("transcript", entry.transcriptSummary)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        height = 42.dp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = GxTextMid) }
        }
    )
}
