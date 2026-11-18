package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val username: String = "",
    val statusText: String = "",
    val avatarData: String = "preset:cyan",
    val circleId: String = "friends_main",
    val online: Boolean = true,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CircleMemberSummary(
    val userId: String = "",
    val name: String = "",
    val username: String = "",
    val avatarData: String = "preset:cyan",
    val statusText: String = "",
    val online: Boolean = false,
    val lastSeenMillis: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class FriendCircle(
    @DocumentId val id: String = "",
    val name: String = "Inner Circle",
    val inviteCode: String = "FRIENDS26",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val members: Map<String, Map<String, Any?>> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedMembers(): List<CircleMemberSummary> {
        return members.values.mapNotNull { map ->
            val uid = map["userId"] as? String ?: return@mapNotNull null
            CircleMemberSummary(
                userId = uid,
                name = map["name"] as? String ?: "Friend",
                username = map["username"] as? String ?: "",
                avatarData = map["avatarData"] as? String ?: "preset:cyan",
                statusText = map["statusText"] as? String ?: "",
                online = map["online"] as? Boolean ?: false,
                lastSeenMillis = (map["lastSeenMillis"] as? Number)?.toLong() ?: 0L
            )
        }.sortedWith(compareByDescending<CircleMemberSummary> { it.online }.thenBy { it.name.lowercase() })
    }
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val groupChat: Boolean = false,
    val groupName: String = "",
    val groupAvatar: String = "preset:indigo",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageType: String = "text",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val pinnedMessageIds: List<String> = emptyList(),
    val typingUserIds: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val participantIds: List<String> = emptyList(),
    val type: String = "text",
    val content: String = "",
    val fontFamily: String = "sans",
    val textColorKey: String = "default",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val inlineCode: Boolean = false,
    val blockquote: Boolean = false,
    val mediaMeta: Map<String, Any?>? = null,
    val pollData: Map<String, Any?>? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val seenBy: List<String> = emptyList(),
    val deliveredTo: List<String> = emptyList(),
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedPoll(): PollData? {
        val raw = pollData ?: return null
        val question = raw["question"] as? String ?: return null
        val allowMultiple = raw["allowMultiple"] as? Boolean ?: false
        val rawOptions = raw["options"] as? List<*> ?: emptyList<Any>()
        val options = rawOptions.mapIndexedNotNull { idx, item ->
            val optMap = item as? Map<*, *> ?: return@mapIndexedNotNull null
            val optId = optMap["id"] as? String ?: "opt_$idx"
            val text = optMap["text"] as? String ?: return@mapIndexedNotNull null
            val voters = (optMap["voterIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            PollOptionData(id = optId, text = text, voterIds = voters)
        }
        return PollData(question = question, allowMultiple = allowMultiple, options = options)
    }

    fun mediaString(key: String): String = mediaMeta?.get(key) as? String ?: ""
    fun mediaLong(key: String): Long = (mediaMeta?.get(key) as? Number)?.toLong() ?: 0L
}

data class PollOptionData(
    val id: String,
    val text: String,
    val voterIds: List<String>
)

data class PollData(
    val question: String,
    val allowMultiple: Boolean,
    val options: List<PollOptionData>
)

data class RichTextStyleState(
    val fontFamily: String = "sans",
    val textColorKey: String = "default",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val inlineCode: Boolean = false,
    val blockquote: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeIds: List<String> = emptyList(),
    val participantIds: List<String> = emptyList(),
    val callType: String = "audio",
    val status: String = "ringing",
    val offerSdp: String = "",
    val answerSdp: String = "",
    val callerMuted: Boolean = false,
    val callerCameraOn: Boolean = false,
    val screenSharing: Boolean = false,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ChunkTransferState(
    val transferId: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val chunkIndex: Int,
    val totalChunks: Int,
    val speedMbps: Double,
    val progress: Float,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val assembledPath: String = "",
    val sha256: String = "",
    val error: String? = null
)
