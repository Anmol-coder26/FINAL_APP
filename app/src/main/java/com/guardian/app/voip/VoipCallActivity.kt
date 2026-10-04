package com.guardian.app.voip

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.guardian.app.ui.theme.GuardianTheme

class VoipCallActivity : ComponentActivity() {
    private val viewModel: VoipCallViewModel by viewModels()
    private var pendingRoom: String? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) completeRequest() else {
            pendingRoom = null
            viewModel.showError("Microphone access was not granted. Allow it in Android app settings, then retry your call.")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoom = savedInstanceState?.getString("pending_room")
        setContent {
            GuardianTheme {
                VoipCallScreen(viewModel, { finish() },
                    onCreateRoomRequested = { requestCall("CREATE") },
                    onJoinRoomRequested = { requestCall(it) })
            }
        }
    }
    private fun requestCall(room: String) {
        pendingRoom = room
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) completeRequest()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun completeRequest() {
        val room = pendingRoom ?: return
        pendingRoom = null
        if (room == "CREATE") viewModel.createRoom() else viewModel.joinRoom(room)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_room", pendingRoom)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        if (isFinishing) viewModel.endCall()
        super.onDestroy()
    }
}
