package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.media.PlaybackState
import com.example.data.model.ChatMessage
import com.example.data.model.PollData
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.util.RichTextParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    isGroup: Boolean,
    isHighlighted: Boolean,
    currentUserId: String,
    playbackState: PlaybackState,
    participantNames: Map<String, String>,
    onLongPress: () -> Unit,
    onReplyTap: (String) -> Unit,
    onImageTap: (ChatMessage) -> Unit,
    onVideoTap: (ChatMessage) -> Unit,
    onAudioToggle: (ChatMessage) -> Unit,
    onFileTap: (ChatMessage) -> Unit,
    onPollVote: (ChatMessage, String) -> Unit,
    onReactionTap: (ChatMessage, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeText = remember(message.createdAt) {
        val ms = message.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
    }

    if (message.type == "call_log") {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isVideo = message.mediaString("callType") == "video"
                    val isMissed = message.mediaString("callStatus") in listOf("missed", "declined")
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = "Call log",
                        tint = if (isMissed) MaterialTheme.colorScheme.error else EmeraldOnline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${message.content} • $timeText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val bubbleBg = when {
        isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer
        isOwn -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleContentColor = when {
        isHighlighted -> MaterialTheme.colorScheme.onTertiaryContainer
        isOwn -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isOwn) 18.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 18.dp
            ),
            color = bubbleBg,
            tonalElevation = 2.dp,
            modifier = Modifier
                .widthIn(min = 90.dp, max = 310.dp)
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = {
                        when (message.type) {
                            "image" -> onImageTap(message)
                            "video" -> onVideoTap(message)
                            "file" -> onFileTap(message)
                            "voice", "audio" -> onAudioToggle(message)
                            else -> onLongPress()
                        }
                    },
                    onLongClick = onLongPress
                )
                .testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                if ((isGroup || !isOwn) && !isOwn) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                // Reply quote banner
                if (!message.replyToMessageId.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable { onReplyTap(message.replyToMessageId) }
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.replyToSenderName ?: "Reply",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = message.replyToPreview ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Main message payload by type
                if (message.deleted) {
                    Text(
                        text = "This message was deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    when (message.type) {
                        "image" -> ImageMessageContent(message, onClick = { onImageTap(message) })
                        "video" -> VideoMessageContent(message, onClick = { onVideoTap(message) })
                        "voice", "audio" -> AudioMessageContent(
                            message = message,
                            playbackState = playbackState,
                            accentColor = bubbleContentColor,
                            onToggle = { onAudioToggle(message) }
                        )
                        "file" -> FileMessageContent(message, onClick = { onFileTap(message) })
                        "poll" -> {
                            val poll = message.parsedPoll()
                            if (poll != null) {
                                PollMessageContent(
                                    poll = poll,
                                    currentUserId = currentUserId,
                                    participantNames = participantNames,
                                    onVote = { optId -> onPollVote(message, optId) }
                                )
                            }
                        }
                        "gif" -> GifMessageContent(message)
                        else -> {
                            val styledText = remember(
                                message.content,
                                message.fontFamily,
                                message.textColorKey,
                                message.bold,
                                message.italic,
                                message.underline,
                                message.strikethrough,
                                message.inlineCode,
                                message.blockquote,
                                bubbleContentColor
                            ) {
                                RichTextParser.parseStyledMessage(
                                    rawText = message.content,
                                    fontFamilyKey = message.fontFamily,
                                    textColorKey = message.textColorKey,
                                    defaultColor = bubbleContentColor,
                                    forceBold = message.bold,
                                    forceItalic = message.italic,
                                    forceUnderline = message.underline,
                                    forceStrike = message.strikethrough,
                                    forceCode = message.inlineCode,
                                    forceQuote = message.blockquote
                                )
                            }
                            if (message.blockquote) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(22.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(text = styledText)
                                }
                            } else {
                                Text(text = styledText)
                            }
                        }
                    }
                }

                // Footer metadata (pinned, edited, time, receipts)
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (message.edited && !message.deleted) {
                        Text(
                            text = "edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOwn) {
                        val seenByOther = message.seenBy.any { it != currentUserId }
                        val deliveredToOther = message.deliveredTo.any { it != currentUserId }
                        Icon(
                            imageVector = if (seenByOther || deliveredToOther) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = if (seenByOther) "Seen" else "Sent",
                            tint = if (seenByOther) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Reaction Pills
        if (message.reactions.isNotEmpty() && !message.deleted) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.reactions.forEach { (emoji, users) ->
                    if (users.isNotEmpty()) {
                        val iReacted = users.contains(currentUserId)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (iReacted) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (iReacted) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onReactionTap(message, emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = emoji, fontSize = 13.sp)
                                Text(
                                    text = users.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageMessageContent(message: ChatMessage, onClick: () -> Unit) {
    val decoded = remember(message.content) { decodeDataUrlBitmap(message.content) }
    val localPath = message.mediaString("localPath")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        when {
            decoded != null -> Image(
                bitmap = decoded,
                contentDescription = message.mediaString("fileName").ifBlank { "Shared image" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(190.dp)
            )
            localPath.isNotBlank() && File(localPath).exists() -> AsyncImage(
                model = File(localPath),
                contentDescription = "Shared image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(190.dp)
            )
            else -> Box(
                modifier = Modifier.fillMaxWidth().height(190.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Image preview unavailable on this device", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun VideoMessageContent(message: ChatMessage, onClick: () -> Unit) {
    val thumb = remember(message.mediaString("thumbnailData")) {
        decodeDataUrlBitmap(message.mediaString("thumbnailData"))
    }
    val durationMs = message.mediaLong("durationMs")
    val secs = (durationMs / 1000L).coerceAtLeast(0L)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = "Video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(175.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xAA0B1120)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play video",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        Surface(
            color = Color(0xAA0B1120),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = "🎬 ${secs / 60}:${"%02d".format(secs % 60)}",
                color = Color.White,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun AudioMessageContent(
    message: ChatMessage,
    playbackState: PlaybackState,
    accentColor: Color,
    onToggle: () -> Unit
) {
    val isThisActive = playbackState.activeMessageId == message.id
    val isPlaying = isThisActive && playbackState.isPlaying
    val totalMs = if (isThisActive && playbackState.durationMs > 0) playbackState.durationMs
    else message.mediaLong("durationMs").coerceAtLeast(1000L)
    val posMs = if (isThisActive) playbackState.positionMs else 0L
    val progress = (posMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val secs = (totalMs / 1000L).coerceAtLeast(1L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Waveform",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (message.type == "voice") "Voice Note" else message.mediaString("fileName").ifBlank { "Audio" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${secs / 60}:${"%02d".format(secs % 60)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FileMessageContent(message: ChatMessage, onClick: () -> Unit) {
    val fileName = message.mediaString("fileName").ifBlank { message.content }
    val bytes = message.mediaLong("fileSize")
    val chunkCount = message.mediaLong("chunkCount")
    val sizeText = when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "File attachment",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (chunkCount > 0) "$sizeText • ${chunkCount}x 5MB chunks" else sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PollMessageContent(
    poll: PollData,
    currentUserId: String,
    participantNames: Map<String, String>,
    onVote: (String) -> Unit
) {
    val totalVotes = poll.options.sumOf { it.voterIds.size }.coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "📊 ${poll.question}",
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Text(
            text = if (poll.allowMultiple) "Multiple choice • Tap to vote" else "Single choice • Tap to vote",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        poll.options.forEach { option ->
            val votedByMe = option.voterIds.contains(currentUserId)
            val ratio = if (poll.options.sumOf { it.voterIds.size } == 0) 0f
            else option.voterIds.size.toFloat() / totalVotes.toFloat()
            val animatedRatio by animateFloatAsState(
                targetValue = ratio,
                animationSpec = tween(350),
                label = "poll_bar"
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (votedByMe) 1.5.dp else 1.dp,
                        color = if (votedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onVote(option.id) }
                    .testTag("poll_option_${option.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (votedByMe) "✓ ${option.text}" else option.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (votedByMe) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(ratio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { animatedRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (votedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (option.voterIds.isNotEmpty()) {
                        val voterNames = option.voterIds.mapNotNull { participantNames[it] }.take(3)
                        if (voterNames.isNotEmpty()) {
                            Text(
                                text = voterNames.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GifMessageContent(message: ChatMessage) {
    val url = message.mediaString("url")
    val title = message.mediaString("title").ifBlank { message.content }
    val emoji = message.mediaString("emojiBadge").ifBlank { "✨" }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (url.isNotBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            }
            Surface(
                color = Color(0xAA0B1120),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = "$emoji GIF • $title",
                    color = Color.White,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
