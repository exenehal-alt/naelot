package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.media.ChunkedFileEngine
import com.example.ui.components.FriendAvatar
import com.example.ui.components.avatarPresets
import com.example.ui.theme.SpaceGroteskFontFamily
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun OnboardingGateScreen(
    isBusy: Boolean,
    errorText: String?,
    onComplete: (name: String, username: String, status: String, avatar: String, inviteCode: String) -> Unit,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleUser = remember { Firebase.auth.currentUser }
    val defaultInvite = remember { BuildConfig.INVITE_CODE.ifBlank { "FRIENDS26" } }

    var name by remember { mutableStateOf(googleUser?.displayName ?: "") }
    var username by remember {
        val seed = (googleUser?.displayName ?: "friend")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .take(18)
            .trim('_')
            .ifBlank { "friend" }
        mutableStateOf(seed)
    }
    var statusText by remember { mutableStateOf("Ready to hang out ⚡") }
    var inviteCode by remember { mutableStateOf(defaultInvite) }
    var avatarData by remember { mutableStateOf("preset:cyan") }

    val fileEngine = remember(context) { ChunkedFileEngine(context) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = fileEngine.encodeCompressedImageBase64(uri, maxDim = 256, quality = 70)
                if (b64.isNotBlank()) avatarData = b64
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Join Your Friend Circle",
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Text(
                text = "Set up your private handle, avatar, and status. Verify your circle invite code to enter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Avatar Preview + Picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FriendAvatar(
                    name = name.ifBlank { "You" },
                    avatarData = avatarData,
                    size = 72.dp,
                    showOnlineDot = true,
                    isOnline = true
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Pick Photo")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        avatarPresets.forEach { (presetKey, colors) ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(colors))
                                    .border(
                                        width = if (avatarData == presetKey) 2.5.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        shape = CircleShape
                                    )
                                    .clickable { avatarData = presetKey }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input")
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase().replace(Regex("[^a-z0-9_]"), "") },
                label = { Text("Unique Username (lowercase)") },
                prefix = { Text("@") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_username_input")
            )

            OutlinedTextField(
                value = statusText,
                onValueChange = { statusText = it },
                label = { Text("Custom Status") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_status_input")
            )

            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it },
                label = { Text("Private Circle Invite Code") },
                supportingText = { Text("Default circle invite code: $defaultInvite") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("onboarding_invite_input")
            )

            if (!errorText.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = { onComplete(name, username, statusText, avatarData, inviteCode) },
                enabled = !isBusy && name.isNotBlank() && username.length >= 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("complete_onboarding_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Enter CircleChat", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = onSignOutClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Switch Google Account")
            }
        }
    }
}
