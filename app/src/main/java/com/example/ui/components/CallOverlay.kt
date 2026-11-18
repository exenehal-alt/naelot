package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.CallSession
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay

@Composable
fun CallOverlay(
    call: CallSession,
    currentUserId: String,
    peerName: String,
    peerAvatar: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onUpdateControls: (muted: Boolean, cameraOn: Boolean, screenShare: Boolean, durationSec: Long) -> Unit,
    onEndCall: (durationSec: Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isIncomingRinging = call.status == "ringing" && call.callerId != currentUserId

    var isMuted by remember(call.id) { mutableStateOf(false) }
    var isCameraOn by remember(call.id) { mutableStateOf(call.callType == "video") }
    var isScreenSharing by remember(call.id) { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var durationSeconds by remember(call.id) { mutableLongStateOf(call.durationSeconds) }

    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAudioPerm = results[Manifest.permission.RECORD_AUDIO] == true || hasAudioPerm
        hasCameraPerm = results[Manifest.permission.CAMERA] == true || hasCameraPerm
    }

    LaunchedEffect(call.id, call.callType) {
        val needed = buildList {
            if (!hasAudioPerm) add(Manifest.permission.RECORD_AUDIO)
            if (call.callType == "video" && !hasCameraPerm) add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permLauncher.launch(needed.toTypedArray())
        }
    }

    // Ringtone tone playback while ringing
    DisposableEffect(call.id, call.status) {
        var toneGen: ToneGenerator? = null
        if (call.status == "ringing") {
            try {
                toneGen = ToneGenerator(AudioManager.STREAM_RING, 60)
                toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500)
            } catch (_: Exception) {}
        }
        onDispose {
            try {
                toneGen?.stopTone()
                toneGen?.release()
            } catch (_: Exception) {}
        }
    }

    // Call timer ticks when active (or outgoing ringing preview)
    LaunchedEffect(call.id, call.status) {
        while (call.status == "active") {
            delay(1000L)
            durationSeconds += 1L
        }
    }

    val pulse = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBg, Color(0xFF0F172A), Color(0xFF1E1B4B))
                )
            )
    ) {
        // Live CameraX preview when video call camera is on
        if (isCameraOn && hasCameraPerm && !isIncomingRinging) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val selector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(context))
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x550B1120))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x661E293B)
                ) {
                    Text(
                        text = when {
                            isIncomingRinging -> "INCOMING ${call.callType.uppercase()} CALL"
                            call.status == "ringing" -> "RINGING..."
                            isScreenSharing -> "SCREEN SHARING ACTIVE"
                            else -> "WEBRTC CONNECTED"
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = if (call.status == "active") EmeraldOnline else Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                if (call.status == "active") {
                    Text(
                        text = "%02d:%02d".format(durationSeconds / 60, durationSeconds % 60),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Center Peer Avatar & Name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .scale(if (call.status == "ringing") pulseScale else 1f)
                            .clip(CircleShape)
                            .background(Color(0x3338BDF8))
                    )
                    FriendAvatar(
                        name = if (isIncomingRinging) call.callerName else peerName,
                        avatarData = peerAvatar,
                        size = 104.dp
                    )
                }
                Text(
                    text = if (isIncomingRinging) call.callerName else peerName,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = Color.White
                )
                Text(
                    text = when {
                        isIncomingRinging -> "Inviting you to a ${call.callType} call"
                        isMuted -> "Microphone muted"
                        else -> if (call.callType == "video") "HD Video Call" else "Crystal Voice Call"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1)
                )
            }

            // Bottom Action Controls
            if (isIncomingRinging) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onDecline,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(CoralAlert)
                                .testTag("decline_call_button")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline call", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Decline", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(EmeraldOnline)
                                .testTag("accept_call_button")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Accept call", tint = ObsidianBg, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Accept", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xCC111827),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallControlButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isMuted) "Unmute" else "Mute",
                            active = isMuted,
                            tag = "call_mute_button",
                            onClick = {
                                isMuted = !isMuted
                                onUpdateControls(isMuted, isCameraOn, isScreenSharing, durationSeconds)
                            }
                        )
                        CallControlButton(
                            icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            label = if (isCameraOn) "Cam On" else "Cam Off",
                            active = isCameraOn,
                            tag = "call_camera_button",
                            onClick = {
                                isCameraOn = !isCameraOn
                                onUpdateControls(isMuted, isCameraOn, isScreenSharing, durationSeconds)
                            }
                        )
                        if (isCameraOn) {
                            CallControlButton(
                                icon = Icons.Default.Cameraswitch,
                                label = "Flip",
                                active = false,
                                tag = "call_flip_button",
                                onClick = {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                                        CameraSelector.LENS_FACING_BACK
                                    else CameraSelector.LENS_FACING_FRONT
                                }
                            )
                        }
                        CallControlButton(
                            icon = Icons.Default.PresentToAll,
                            label = "Share",
                            active = isScreenSharing,
                            tag = "call_screen_share_button",
                            onClick = {
                                isScreenSharing = !isScreenSharing
                                onUpdateControls(isMuted, isCameraOn, isScreenSharing, durationSeconds)
                            }
                        )
                        IconButton(
                            onClick = { onEndCall(durationSeconds) },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(CoralAlert)
                                .testTag("end_call_button")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF38BDF8) else Color(0xFF1E293B))
                .testTag(tag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) ObsidianBg else Color.White
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = Color(0xFFCBD5E1), fontSize = 11.sp)
    }
}
