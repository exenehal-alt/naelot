package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.media.GifCatalog
import com.example.data.model.ChatMessage
import com.example.data.model.CircleMemberSummary
import com.example.data.model.Conversation
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.CreatePollDialog
import com.example.ui.components.EmojiPickerSheet
import com.example.ui.components.FriendAvatar
import com.example.ui.components.GifPickerSheet
import com.example.ui.components.ImageLightboxDialog
import com.example.ui.components.MessageBubble
import com.example.ui.components.RichTextToolbar
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.messageFontFamily
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.RichTextParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    conversation: Conversation,
    currentUser: UserProfile,
    circleMembers: List<CircleMemberSummary>,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val styleState by viewModel.styleState.collectAsStateWithLifecycle()
    val replyTo by viewModel.replyToMessage.collectAsStateWithLifecycle()
    val editingMsg by viewModel.editingMessage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val transferState by viewModel.chunkTransferState.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()

    var showFormattingToolbar by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showEmojiSheet by remember { mutableStateOf(false) }
    var showGifSheet by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var lightboxMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var videoPlayerMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    // Media & Document Launchers (Zero-Storage-Permission Play-Compliant!)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.sendImages(uris, conversation, currentUser.name)
        }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendVideo(uri, conversation, currentUser.name)
        }
    }
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.sendAudioFile(uri, conversation, currentUser.name)
        }
    }
    val largeFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadLargeFileChunked(uri, conversation, currentUser.name)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        }
    }

    if (showBackButton) {
        BackHandler {
            if (showSearchBar) {
                showSearchBar = false
                viewModel.updateSearchQuery("")
            } else {
                onBack()
            }
        }
    }

    val otherPeer = remember(conversation, circleMembers, currentUser.userId) {
        val peerUid = conversation.participantIds.firstOrNull { it != currentUser.userId }
        circleMembers.firstOrNull { it.userId == peerUid }
    }
    val chatTitle = remember(conversation, otherPeer) {
        if (conversation.groupChat) conversation.groupName.ifBlank { "Group Chat" }
        else otherPeer?.name ?: conversation.participantNames.entries.firstOrNull { it.key != currentUser.userId }?.value ?: "Friend"
    }
    val chatAvatar = remember(conversation, otherPeer) {
        if (conversation.groupChat) conversation.groupAvatar else otherPeer?.avatarData ?: "preset:cyan"
    }

    val typingNames = remember(conversation.typingUserIds, currentUser.userId, conversation.participantNames) {
        conversation.typingUserIds
            .filter { it != currentUser.userId }
            .mapNotNull { conversation.participantNames[it] }
    }

    val allMessages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val filteredMessages = remember(allMessages, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allMessages
        else allMessages.filter {
            it.content.lowercase().contains(q) ||
                it.senderName.lowercase().contains(q) ||
                (it.parsedPoll()?.question?.lowercase()?.contains(q) == true)
        }
    }
    val pinnedMessages = remember(allMessages) { allMessages.filter { it.pinned && !it.deleted } }

    // Mark seen & auto-scroll on new messages
    LaunchedEffect(allMessages.size) {
        viewModel.markUnseenAsSeen(allMessages)
        if (allMessages.isNotEmpty() && searchQuery.isBlank()) {
            listState.animateScrollToItem(allMessages.lastIndex)
        }
    }

    val shareLocalFile: (ChatMessage) -> Unit = { msg ->
        val path = msg.mediaString("localPath")
        val file = File(path)
        if (path.isNotBlank() && file.exists()) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mime = msg.mediaString("mimeType").ifBlank { "*/*" }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Open or Save ${file.name}"))
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Chat Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showBackButton) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    FriendAvatar(
                        name = chatTitle,
                        avatarData = chatAvatar,
                        size = 40.dp,
                        showOnlineDot = !conversation.groupChat,
                        isOnline = otherPeer?.online == true
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chatTitle,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when {
                            typingNames.isNotEmpty() -> "${typingNames.joinToString(", ")} typing..."
                            conversation.groupChat -> "${conversation.participantIds.size} members"
                            otherPeer?.online == true -> "Online • ${otherPeer.statusText}"
                            else -> otherPeer?.statusText?.ifBlank { "Offline" } ?: "Offline"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (typingNames.isNotEmpty() || otherPeer?.online == true) EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.initiateCall(conversation, currentUser.name, "audio") },
                        modifier = Modifier.testTag("start_audio_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Start audio call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { viewModel.initiateCall(conversation, currentUser.name, "video") },
                        modifier = Modifier.testTag("start_video_call_button")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Start video call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) viewModel.updateSearchQuery("")
                        },
                        modifier = Modifier.testTag("toggle_chat_search_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search messages")
                    }
                }

                // In-chat Search Bar
                AnimatedVisibility(visible = showSearchBar) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = { Text("Search messages in chat...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("chat_search_input")
                    )
                }

                // Pinned Message Banner
                if (pinnedMessages.isNotEmpty()) {
                    val latestPin = pinnedMessages.last()
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val idx = filteredMessages.indexOfFirst { it.id == latestPin.id }
                                if (idx >= 0) {
                                    scope.launch {
                                        listState.animateScrollToItem(idx)
                                        highlightedMessageId = latestPin.id
                                        delay(1600L)
                                        highlightedMessageId = null
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Pinned (${pinnedMessages.size}): ${latestPin.content}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Error Banner
        if (!errorBanner.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorBanner ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearError, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Message Timeline
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Error -> {
                    Text(
                        text = (messagesState as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                is UiState.Success -> {
                    if (filteredMessages.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching messages" else "Say hello to $chatTitle!",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Send styled messages, voice notes, polls, GIFs, or 1GB files.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredMessages, key = { it.id }) { msg ->
                                MessageBubble(
                                    message = msg,
                                    isOwn = msg.senderId == currentUser.userId,
                                    isGroup = conversation.groupChat,
                                    isHighlighted = msg.id == highlightedMessageId,
                                    currentUserId = currentUser.userId,
                                    playbackState = playbackState,
                                    participantNames = conversation.participantNames,
                                    onLongPress = { selectedMessageForActions = msg },
                                    onReplyTap = { targetId ->
                                        val idx = filteredMessages.indexOfFirst { it.id == targetId }
                                        if (idx >= 0) {
                                            scope.launch {
                                                listState.animateScrollToItem(idx)
                                                highlightedMessageId = targetId
                                                delay(1500L)
                                                highlightedMessageId = null
                                            }
                                        }
                                    },
                                    onImageTap = { lightboxMessage = it },
                                    onVideoTap = { videoPlayerMessage = it },
                                    onAudioToggle = { viewModel.toggleAudioPlayback(it) },
                                    onFileTap = { shareLocalFile(it) },
                                    onPollVote = { m, optId -> viewModel.voteOnPoll(m, optId) },
                                    onReactionTap = { m, emoji -> viewModel.toggleReaction(m, emoji) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1GB Chunked File Progress Card
        if (transferState != null && !transferState!!.isCancelled) {
            val st = transferState!!
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = st.fileName,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Chunk ${st.chunkIndex}/${st.totalChunks} • ${(st.progress * 100).toInt()}% • %.1f MB/s".format(st.speedMbps),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!st.isCompleted) {
                            TextButton(onClick = viewModel::cancelChunkedUpload) {
                                Text("Cancel", color = CoralAlert)
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { st.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        // Bottom Composer Area
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Reply / Edit Banner
                if (replyTo != null || editingMsg != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (editingMsg != null) "Editing message" else "Replying to ${replyTo?.senderName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = editingMsg?.content ?: replyTo?.content ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = viewModel::cancelEditingOrReply, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel reply or edit")
                            }
                        }
                    }
                }

                // Collapsible Font & Markdown Formatting Toolbar
                AnimatedVisibility(visible = showFormattingToolbar) {
                    RichTextToolbar(
                        styleState = styleState,
                        onUpdateStyle = viewModel::updateStyle
                    )
                }

                if (recordingState.isRecording) {
                    // Live Voice Note Waveform Recorder Bar
                    Surface(
                        color = CoralAlert.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.stopAndSendVoiceRecording(false, conversation, currentUser.name) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Discard voice note", tint = CoralAlert)
                            }
                            Row(
                                modifier = Modifier.weight(1f).height(28.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val bars = recordingState.amplitudes.ifEmpty { listOf(0.2f, 0.4f, 0.3f) }
                                bars.takeLast(24).forEach { amp ->
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height((6 + amp * 22).dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(CoralAlert)
                                    )
                                }
                            }
                            val secs = recordingState.elapsedMs / 1000L
                            Text(
                                text = "%d:%02d".format(secs / 60, secs % 60),
                                fontFamily = JetBrainsMonoFontFamily,
                                color = CoralAlert,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.stopAndSendVoiceRecording(true, conversation, currentUser.name) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("send_voice_note_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice note", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                } else {
                    // Standard Rich Message Composer Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { showAttachSheet = true },
                            modifier = Modifier.testTag("open_attachments_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Attach media, file, poll, or GIF")
                        }
                        IconButton(
                            onClick = { showFormattingToolbar = !showFormattingToolbar },
                            modifier = Modifier.testTag("toggle_font_toolbar_button")
                        ) {
                            Icon(
                                Icons.Default.FormatColorText,
                                contentDescription = "Font and text formatting",
                                tint = if (showFormattingToolbar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val previewColor = RichTextParser.resolveHighlightColor(
                            styleState.textColorKey,
                            MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = composerText,
                            onValueChange = { viewModel.onComposerTextChanged(it, conversation) },
                            placeholder = { Text("Message (${styleState.fontFamily})...") },
                            textStyle = TextStyle(
                                fontFamily = messageFontFamily(styleState.fontFamily),
                                fontWeight = if (styleState.bold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (styleState.italic) FontStyle.Italic else FontStyle.Normal,
                                color = previewColor,
                                fontSize = 15.sp
                            ),
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            trailingIcon = {
                                IconButton(onClick = { showEmojiSheet = true }) {
                                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji picker")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_composer_input")
                        )

                        if (composerText.isNotBlank()) {
                            IconButton(
                                onClick = { viewModel.sendTextOrSaveEdit(conversation, currentUser.name) },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send message",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.startVoiceRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .testTag("record_voice_button")
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Record voice note",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Attachments Modal Bottom Sheet
    if (showAttachSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(
                    text = "Share in CircleChat",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Photos (Gallery Picker)") },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                ListItem(
                    headlineContent = { Text("Video (Inline 1x–2x Player)") },
                    leadingContent = { Icon(Icons.Default.VideoFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                )
                ListItem(
                    headlineContent = { Text("Audio Track (MP3, WAV, M4A, OGG)") },
                    leadingContent = { Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    }
                )
                ListItem(
                    headlineContent = { Text("Large File (Up to 1GB • 5MB Chunked)") },
                    supportingContent = { Text("ZIP, PDF, APK, RAR, DOCX with SHA-256 verification") },
                    leadingContent = { Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        largeFilePickerLauncher.launch(arrayOf("*/*"))
                    }
                )
                ListItem(
                    headlineContent = { Text("Create Live Poll (2–10 options)") },
                    leadingContent = { Icon(Icons.Default.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        showPollDialog = true
                    }.testTag("open_poll_creator")
                )
                ListItem(
                    headlineContent = { Text("Send Animated GIF") },
                    leadingContent = { Icon(Icons.Default.Gif, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        showGifSheet = true
                    }.testTag("open_gif_picker")
                )
            }
        }
    }

    // Message Action Sheet (React, Reply, Pin, Edit, Delete)
    selectedMessageForActions?.let { targetMsg ->
        ModalBottomSheet(onDismissRequest = { selectedMessageForActions = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GifCatalog.quickReactions.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    viewModel.toggleReaction(targetMsg, emoji)
                                    selectedMessageForActions = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Reply") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.setReplyTarget(targetMsg)
                        selectedMessageForActions = null
                    }
                )
                ListItem(
                    headlineContent = { Text(if (targetMsg.pinned) "Unpin Message" else "Pin Message") },
                    leadingContent = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.togglePin(conversation, targetMsg)
                        selectedMessageForActions = null
                    }
                )
                if (targetMsg.senderId == currentUser.userId && targetMsg.type == "text" && !targetMsg.deleted) {
                    ListItem(
                        headlineContent = { Text("Edit Message") },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                        modifier = Modifier.clickable {
                            viewModel.startEditingMessage(targetMsg)
                            selectedMessageForActions = null
                        }
                    )
                }
                if (targetMsg.senderId == currentUser.userId && !targetMsg.deleted) {
                    ListItem(
                        headlineContent = { Text("Delete Message", color = CoralAlert) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = CoralAlert) },
                        modifier = Modifier.clickable {
                            viewModel.deleteMessage(targetMsg)
                            selectedMessageForActions = null
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showPollDialog) {
        CreatePollDialog(
            onDismiss = { showPollDialog = false },
            onCreatePoll = { q, opts, multi ->
                viewModel.sendPoll(q, opts, multi, conversation, currentUser.name)
            }
        )
    }

    if (showEmojiSheet) {
        EmojiPickerSheet(
            onDismiss = { showEmojiSheet = false },
            onEmojiSelected = { emoji -> viewModel.appendEmojiToComposer(emoji) }
        )
    }

    if (showGifSheet) {
        GifPickerSheet(
            onDismiss = { showGifSheet = false },
            onGifSelected = { gif -> viewModel.sendGif(gif, conversation, currentUser.name) }
        )
    }

    lightboxMessage?.let { imgMsg ->
        ImageLightboxDialog(
            message = imgMsg,
            onDismiss = { lightboxMessage = null },
            onShareFile = shareLocalFile
        )
    }

    videoPlayerMessage?.let { vidMsg ->
        VideoPlayerDialog(
            message = vidMsg,
            onDismiss = { videoPlayerMessage = null }
        )
    }
}
