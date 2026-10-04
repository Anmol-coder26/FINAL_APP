package com.guardian.app.voip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.bhashini.SpeechConnectionConfig
import com.guardian.app.bhashini.SpeechStreamPhase
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.components.GxLiveDot
import com.guardian.app.ui.components.GxRiskRing
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxWarning

@Composable
fun VoipCallScreen(viewModel: VoipCallViewModel, onNavigateBack: () -> Unit,
    onCreateRoomRequested: () -> Unit, onJoinRoomRequested: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val active = state.isJoining || state.isConnected
    var room by rememberSaveable { mutableStateOf("") }
    var setup by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val back = { if (active) confirmLeave = true else onNavigateBack() }
    BackHandler(enabled = active) { confirmLeave = true }
    LaunchedEffect(state.transcripts.size, state.transcripts.lastOrNull()?.text) {
        val info = listState.layoutInfo
        val nearBottom = (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 2
        if (state.transcripts.isNotEmpty() && nearBottom) listState.scrollToItem(state.transcripts.lastIndex)
    }
    Surface(color = GxBase, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Home", tint = GxTextHi) }
                Text("SuSagi room calls", color = GxTextHi, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (!active) TextButton(enabled = state.setupLoaded, onClick = { setup = true }) { Text("Call setup") }
                else Text("%02d:%02d".format(state.durationSeconds / 60, state.durationSeconds % 60), color = GxTextMid)
            }
            if (!active) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Talk, transcribe, and review", color = GxTextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Create a room and share its code, or join the code from the other person's phone.", color = GxTextMid, fontSize = 15.sp)
                    if (!state.setupLoaded) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp)); Text(" Loading call setup…", color = GxTextMid)
                    }
                    state.error?.let { Text(it, color = GxDanger) }
                    if (state.status == "Call ended") Text("Call ended. You can create or join another room.", color = GxTextMid)
                    GxCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Start a room", color = GxTextHi, fontWeight = FontWeight.Bold)
                            Text("The room code appears when you start. Share it with the other person.", color = GxTextMid)
                            GxButton.Primary("Create room", onCreateRoomRequested, enabled = state.setupLoaded, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    GxCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Join a room", color = GxTextHi, fontWeight = FontWeight.Bold)
                            OutlinedTextField(value = room, onValueChange = { room = it.filter { c -> c in '0'..'9' }.take(6) },
                                label = { Text("6-digit room code") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                            GxButton.Primary("Join call", { onJoinRoomRequested(room) },
                                enabled = state.setupLoaded && room.length == 6, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    GxCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Live transcription", color = GxTextHi, fontWeight = FontWeight.Bold)
                            val ready = state.configuration.speechError() == null
                            Text(if (ready) "BHASHINI setup saved. The call screen verifies the server and audio streams when you connect."
                                else "BHASHINI setup is needed for room-call transcription. Add your real service details in Call setup.", color = if (ready) GxTextMid else GxWarning)
                            Text("During a call, You and Caller are separate audio streams. Tell the other person that speech will be transcribed.", color = GxTextMid)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                GxCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GxLiveDot(pulsing = state.isConnected, color = if (state.isConnected) GxSafe else GxWarning)
                            Text(state.status, color = GxTextHi, modifier = Modifier.weight(1f))
                        }
                        Text("Room code: ${state.channelName.removePrefix("gx_")}", color = GxTextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(if (state.peerConnected) "Other person connected" else "Share this code. Waiting for the other person.", color = GxTextMid)
                        state.banner?.let { Text(it, color = GxWarning, fontSize = 12.sp) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("You audio: ${if (state.localPcm) "receiving" else "waiting"}", color = GxTextMid, fontSize = 12.sp)
                            Text("Caller audio: ${if (state.remotePcm) "receiving" else "waiting"}", color = GxTextMid, fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SpeechStatus("You STT", state.localSpeech)
                            SpeechStatus("Caller STT", state.remoteSpeech)
                        }
                    }
                }
                GxCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("RISK ESTIMATE", color = GxTextMid, fontSize = 11.sp)
                            Text(if (state.transcripts.isEmpty()) "Waiting for speech" else when {
                                state.report.riskScore >= 85 -> "High risk — verify identity"
                                state.report.riskScore >= 60 -> "Potential scam signals"
                                state.report.riskScore >= 25 -> "Review the conversation"
                                else -> "No risk signals detected yet"
                            }, color = if (state.report.riskScore >= 60) GxDanger else GxTextHi, fontWeight = FontWeight.Bold)
                            if (state.transcripts.isNotEmpty()) Text(state.report.explanationEn,
                                color = GxTextMid, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (state.transcripts.isNotEmpty()) GxRiskRing(state.report.riskScore, size = 64.dp, strokeWidth = 6.dp)
                        else Text("—", color = GxTextMid, fontSize = 30.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.transcripts.isEmpty()) Text("Words appear here as the speech service returns them.", color = GxTextMid, modifier = Modifier.align(Alignment.Center))
                    else LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.transcripts) { line ->
                            GxCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text((if (line.speaker == Speaker.LOCAL) "You" else "Caller") + if (line.isFinal) "" else " · speaking",
                                        color = if (line.speaker == Speaker.LOCAL) Color(0xFF77B8FA) else Color(0xFFFFBF71), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(line.text, color = GxTextHi, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(enabled = state.isConnected, onClick = viewModel::toggleMute) {
                        Icon(if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            if (state.isMuted) "Unmute microphone" else "Mute microphone", tint = GxTextHi)
                    }
                    IconButton(enabled = state.isConnected, onClick = viewModel::toggleSpeaker) {
                        Icon(if (state.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown, "Toggle speakerphone", tint = GxTextHi)
                    }
                    GxButton.Danger(if (state.isJoining && !state.isConnected) "Cancel call" else "End call", viewModel::endCall,
                        icon = Icons.Default.CallEnd, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false },
        title = { Text("Leave this call?") }, text = { Text("Leaving ends the call and stops transcription.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; viewModel.endCall(); onNavigateBack() }) { Text("Leave call") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep calling") } })
    if (setup) CallSetupDialog(state.configuration, state.error, onDismiss = { setup = false },
        onSave = { config, done -> viewModel.saveSetup(config) { ok -> done(); if (ok) setup = false } })
}

@Composable private fun SpeechStatus(label: String, phase: SpeechStreamPhase) {
    val text = when (phase) {
        SpeechStreamPhase.IDLE, SpeechStreamPhase.STOPPED -> "waiting"
        SpeechStreamPhase.CONNECTING -> "connecting"
        SpeechStreamPhase.CONFIGURING -> "awaiting ready"
        SpeechStreamPhase.READY -> "ready"
        SpeechStreamPhase.ERROR -> "unavailable"
    }
    Text("$label: $text", color = when (phase) {
        SpeechStreamPhase.READY -> GxSafe
        SpeechStreamPhase.ERROR -> GxWarning
        else -> GxTextMid
    }, fontSize = 12.sp)
}

@Composable private fun CallSetupDialog(current: SpeechConnectionConfig, error: String?, onDismiss: () -> Unit,
    onSave: (SpeechConnectionConfig, () -> Unit) -> Unit) {
    var backend by rememberSaveable { mutableStateOf(current.backendUrl) }
    var url by rememberSaveable { mutableStateOf(current.serverUrl) }
    var key by rememberSaveable { mutableStateOf(current.apiKey) }
    var service by rememberSaveable { mutableStateOf(current.serviceId) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Call setup") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Use your running call server and provisioned BHASHINI ASR service. Keys are saved in encrypted on-device storage.")
                OutlinedTextField(backend, { backend = it }, label = { Text("Call server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text("BHASHINI Socket.IO URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it }, label = { Text("BHASHINI inference key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(service, { service = it }, label = { Text("BHASHINI ASR service ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = GxDanger) }
                if (saving) Text("Saving…")
            }
        },
        confirmButton = { TextButton(enabled = !saving && SpeechConnectionConfig.validUrl(url) &&
            (backend.isBlank() || SpeechConnectionConfig.validUrl(backend)), onClick = {
            saving = true
            onSave(SpeechConnectionConfig(url.trim(), key.trim(), service.trim(), backend.trim().trimEnd('/'))) { saving = false }
        }) { Text("Save setup") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}
