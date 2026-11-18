package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.CircleMemberSummary
import com.example.data.model.Conversation
import com.example.data.model.FriendCircle
import com.example.data.model.PollData
import com.example.data.model.RichTextStyleState
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CircleChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val DEFAULT_CIRCLE_ID = "friends_main"
    }

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) { "User must be signed in with Google before accessing Firestore." }
        return uid
    }

    private fun <T> Flow<T>.logFirestoreErrors(
        uid: String,
        operationType: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    fun observeCurrentUser(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .logFirestoreErrors(uid, OperationType.GET, docRef.path)
        )
    }

    fun observeUserCircle(userId: String): Flow<FriendCircle?> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("circles")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { querySnapshot ->
                    querySnapshot.documents.firstOrNull()?.toObject(
                        FriendCircle::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .logFirestoreErrors(uid, OperationType.LIST, "circles")
        )
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { conv ->
                        conv.lastMessageAt ?: conv.updatedAt ?: conv.createdAt ?: Timestamp(0, 0)
                    }
                }
                .logFirestoreErrors(uid, OperationType.LIST, "conversations")
        )
    }

    fun observeMessages(conversationId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { msg ->
                        msg.createdAt ?: Timestamp(0, 0)
                    }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeActiveCalls(userId: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.status == "ringing" || it.status == "active" }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(uid, OperationType.LIST, "calls")
        )
    }

    suspend fun joinOrCreateCircleAndSaveProfile(
        name: String,
        username: String,
        statusText: String,
        avatarData: String,
        inviteCode: String
    ): Result<Unit> {
        val cleanName = name.trim().take(60)
        val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
        val cleanStatus = statusText.trim().take(120)
        val cleanInvite = inviteCode.trim()
        val circleRef = db.collection("circles").document(DEFAULT_CIRCLE_ID)

        return try {
            val uid = requireUserId()
            require(cleanName.isNotEmpty()) { "Full name is required." }
            require(cleanUsername.length in 3..30) { "Username must be 3-30 lowercase letters, numbers, or underscores." }

            val envInvite = BuildConfig.INVITE_CODE.ifBlank { "FRIENDS26" }
            val circleSnap = circleRef.get().await()

            val memberSummary = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "username" to cleanUsername,
                "avatarData" to avatarData,
                "statusText" to cleanStatus,
                "online" to true,
                "lastSeenMillis" to System.currentTimeMillis()
            )

            if (!circleSnap.exists()) {
                if (cleanInvite.isNotEmpty() && !cleanInvite.equals(envInvite, ignoreCase = true)) {
                    return Result.failure(IllegalArgumentException("Invalid invite code. Default invite code is $envInvite."))
                }
                val newCirclePayload = mapOf(
                    "name" to "Inner Circle",
                    "inviteCode" to (cleanInvite.ifEmpty { envInvite }),
                    "ownerId" to uid,
                    "memberUids" to listOf(uid),
                    "members" to mapOf(uid to memberSummary),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                circleRef.set(newCirclePayload).await()
            } else {
                val existingCircle = circleSnap.toObject(FriendCircle::class.java)
                val expectedCode = existingCircle?.inviteCode?.ifBlank { envInvite } ?: envInvite
                val alreadyMember = existingCircle?.memberUids?.contains(uid) == true
                if (!alreadyMember && !cleanInvite.equals(expectedCode, ignoreCase = true) && !cleanInvite.equals(envInvite, ignoreCase = true)) {
                    return Result.failure(IllegalArgumentException("Invalid circle invite code."))
                }
                if (!alreadyMember) {
                    val updatedUids = ((existingCircle?.memberUids ?: emptyList()) + uid).distinct()
                    circleRef.update(
                        mapOf(
                            "memberUids" to updatedUids,
                            "members.$uid" to memberSummary,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    circleRef.update(
                        mapOf(
                            "members.$uid" to memberSummary,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val userRef = db.collection("users").document(uid)
            val existingUserSnap = userRef.get().await()
            if (!existingUserSnap.exists()) {
                val userPayload = mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "username" to cleanUsername,
                    "statusText" to cleanStatus,
                    "avatarData" to avatarData,
                    "circleId" to DEFAULT_CIRCLE_ID,
                    "online" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(userPayload).await()
            } else {
                userRef.update(
                    mapOf(
                        "name" to cleanName,
                        "username" to cleanUsername,
                        "statusText" to cleanStatus,
                        "avatarData" to avatarData,
                        "circleId" to DEFAULT_CIRCLE_ID,
                        "online" to true,
                        "lastSeen" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, circleRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        currentUser: UserProfile,
        name: String,
        username: String,
        statusText: String,
        avatarData: String
    ): Result<Unit> {
        val cleanName = name.trim().take(60)
        val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
        val cleanStatus = statusText.trim().take(120)
        val path = "users/${currentUser.userId}"
        return try {
            val uid = requireUserId()
            check(uid == currentUser.userId)
            require(cleanName.isNotEmpty()) { "Name cannot be empty." }
            require(cleanUsername.length in 3..30) { "Username must be 3-30 characters." }

            db.collection("users").document(uid).update(
                mapOf(
                    "name" to cleanName,
                    "username" to cleanUsername,
                    "statusText" to cleanStatus,
                    "avatarData" to avatarData,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val memberSummary = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "username" to cleanUsername,
                "avatarData" to avatarData,
                "statusText" to cleanStatus,
                "online" to true,
                "lastSeenMillis" to System.currentTimeMillis()
            )
            db.collection("circles").document(currentUser.circleId.ifBlank { DEFAULT_CIRCLE_ID })
                .update(
                    mapOf(
                        "members.$uid" to memberSummary,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(user: UserProfile?, online: Boolean): Result<Unit> {
        if (user == null) return Result.success(Unit)
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (uid != user.userId) return Result.success(Unit)
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "online" to online,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val memberSummary = mapOf(
                "userId" to uid,
                "name" to user.name,
                "username" to user.username,
                "avatarData" to user.avatarData,
                "statusText" to user.statusText,
                "online" to online,
                "lastSeenMillis" to System.currentTimeMillis()
            )
            db.collection("circles").document(user.circleId.ifBlank { DEFAULT_CIRCLE_ID })
                .update(
                    mapOf(
                        "members.$uid" to memberSummary,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCircleSettings(circleId: String, name: String, inviteCode: String): Result<Unit> {
        val cleanName = name.trim().take(60)
        val cleanCode = inviteCode.trim().take(32)
        val path = "circles/$circleId"
        return try {
            requireUserId()
            require(cleanName.isNotEmpty()) { "Circle name cannot be empty." }
            require(cleanCode.length >= 3) { "Invite code must be at least 3 characters." }
            db.collection("circles").document(circleId).update(
                mapOf(
                    "name" to cleanName,
                    "inviteCode" to cleanCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun getOrCreateDirectConversation(
        currentUser: UserProfile,
        friend: CircleMemberSummary,
        existingConversations: List<Conversation>
    ): Result<String> {
        val existingDm = existingConversations.firstOrNull { conv ->
            !conv.groupChat &&
                conv.participantIds.size == 2 &&
                conv.participantIds.contains(currentUser.userId) &&
                conv.participantIds.contains(friend.userId)
        }
        if (existingDm != null) return Result.success(existingDm.id)

        val sortedIds = listOf(currentUser.userId, friend.userId).sorted()
        val deterministicId = "dm_${sortedIds[0]}_${sortedIds[1]}".take(120)
        val convRef = db.collection("conversations").document(deterministicId)
        return try {
            val uid = requireUserId()
            check(uid == currentUser.userId)
            val payload = mapOf(
                "groupChat" to false,
                "groupName" to "",
                "groupAvatar" to "",
                "participantIds" to sortedIds,
                "participantNames" to mapOf(
                    currentUser.userId to currentUser.name,
                    friend.userId to friend.name
                ),
                "lastMessageText" to "Started a direct chat",
                "lastMessageType" to "text",
                "lastMessageSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "pinnedMessageIds" to emptyList<String>(),
                "typingUserIds" to emptyList<String>(),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val snap = convRef.get().await()
            if (!snap.exists()) {
                convRef.set(payload).await()
            }
            Result.success(convRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun createGroupConversation(
        currentUser: UserProfile,
        groupName: String,
        selectedFriends: List<CircleMemberSummary>
    ): Result<String> {
        val cleanGroup = groupName.trim().take(80)
        val convRef = db.collection("conversations").document()
        return try {
            val uid = requireUserId()
            require(cleanGroup.isNotEmpty()) { "Group name is required." }
            require(selectedFriends.isNotEmpty()) { "Select at least one friend." }
            val participantIds = (listOf(uid) + selectedFriends.map { it.userId }).distinct()
            val participantNames = buildMap {
                put(uid, currentUser.name)
                selectedFriends.forEach { put(it.userId, it.name) }
            }
            val payload = mapOf(
                "groupChat" to true,
                "groupName" to cleanGroup,
                "groupAvatar" to "preset:indigo",
                "participantIds" to participantIds,
                "participantNames" to participantNames,
                "lastMessageText" to "${currentUser.name} created $cleanGroup",
                "lastMessageType" to "text",
                "lastMessageSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "pinnedMessageIds" to emptyList<String>(),
                "typingUserIds" to emptyList<String>(),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convRef.set(payload).await()
            Result.success(convRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun setTypingStatus(conversationId: String, currentTypingIds: List<String>, isTyping: Boolean): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val updated = if (isTyping) {
                (currentTypingIds + uid).distinct()
            } else {
                currentTypingIds.filterNot { it == uid }
            }
            if (updated == currentTypingIds) return Result.success(Unit)
            db.collection("conversations").document(conversationId).update(
                mapOf(
                    "typingUserIds" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        senderName: String,
        type: String,
        content: String,
        style: RichTextStyleState = RichTextStyleState(),
        mediaMeta: Map<String, Any>? = null,
        pollData: Map<String, Any>? = null,
        replyTo: ChatMessage? = null
    ): Result<Unit> {
        val convRef = db.collection("conversations").document(conversation.id)
        val msgRef = convRef.collection("messages").document()
        return try {
            val uid = requireUserId()
            val safeContent = content.take(250000)
            val payload = mapOf(
                "conversationId" to conversation.id,
                "senderId" to uid,
                "senderName" to senderName.take(60),
                "participantIds" to conversation.participantIds,
                "type" to type,
                "content" to safeContent,
                "fontFamily" to style.fontFamily,
                "textColorKey" to style.textColorKey,
                "bold" to style.bold,
                "italic" to style.italic,
                "underline" to style.underline,
                "strikethrough" to style.strikethrough,
                "inlineCode" to style.inlineCode,
                "blockquote" to style.blockquote,
                "mediaMeta" to mediaMeta,
                "pollData" to pollData,
                "replyToMessageId" to replyTo?.id?.takeIf { it.isNotBlank() },
                "replyToSenderName" to replyTo?.senderName?.take(60),
                "replyToPreview" to replyTo?.let { previewForMessage(it).take(200) },
                "reactions" to emptyMap<String, List<String>>(),
                "seenBy" to listOf(uid),
                "deliveredTo" to listOf(uid),
                "edited" to false,
                "deleted" to false,
                "pinned" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            msgRef.set(payload).await()

            val preview = when (type) {
                "image" -> "📷 Photo"
                "video" -> "🎬 Video"
                "voice" -> "🎤 Voice note"
                "audio" -> "🎵 Audio file"
                "file" -> "📎 ${mediaMeta?.get("fileName") ?: "File"}"
                "poll" -> "📊 Poll: ${pollData?.get("question") ?: content}"
                "gif" -> "GIF"
                "call_log" -> safeContent
                else -> safeContent
            }.take(500)

            convRef.update(
                mapOf(
                    "lastMessageText" to preview,
                    "lastMessageType" to type,
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "typingUserIds" to conversation.typingUserIds.filterNot { it == uid },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun editMessage(conversationId: String, messageId: String, newContent: String): Result<Unit> {
        val clean = newContent.trim().take(8000)
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            requireUserId()
            require(clean.isNotEmpty()) { "Message cannot be empty." }
            db.document(path).update(
                mapOf(
                    "content" to clean,
                    "edited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "content" to "This message was deleted",
                    "deleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
        currentReactions: Map<String, List<String>>
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val mutable = currentReactions.mapValues { it.value.toMutableList() }.toMutableMap()
            val list = mutable.getOrPut(emoji) { mutableListOf() }
            if (list.contains(uid)) {
                list.remove(uid)
                if (list.isEmpty()) mutable.remove(emoji)
            } else {
                list.add(uid)
            }
            db.document(path).update(
                mapOf(
                    "reactions" to mutable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun togglePinMessage(
        conversation: Conversation,
        message: ChatMessage
    ): Result<Unit> {
        val msgPath = "conversations/${conversation.id}/messages/${message.id}"
        return try {
            requireUserId()
            val newPinned = !message.pinned
            db.document(msgPath).update(
                mapOf(
                    "pinned" to newPinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val updatedPins = if (newPinned) {
                (conversation.pinnedMessageIds + message.id).distinct().takeLast(20)
            } else {
                conversation.pinnedMessageIds.filterNot { it == message.id }
            }
            db.collection("conversations").document(conversation.id).update(
                mapOf(
                    "pinnedMessageIds" to updatedPins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgPath)
            Result.failure(e)
        }
    }

    suspend fun voteOnPoll(
        conversationId: String,
        messageId: String,
        optionId: String,
        currentPoll: PollData
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages/$messageId"
        return try {
            val uid = requireUserId()
            val updatedOptions = currentPoll.options.map { opt ->
                val voters = opt.voterIds.toMutableList()
                if (opt.id == optionId) {
                    if (voters.contains(uid)) voters.remove(uid) else voters.add(uid)
                } else if (!currentPoll.allowMultiple) {
                    voters.remove(uid)
                }
                mapOf(
                    "id" to opt.id,
                    "text" to opt.text,
                    "voterIds" to voters.distinct()
                )
            }
            val updatedPollMap = mapOf(
                "question" to currentPoll.question,
                "allowMultiple" to currentPoll.allowMultiple,
                "options" to updatedOptions
            )
            db.document(path).update(
                mapOf(
                    "pollData" to updatedPollMap,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesSeen(conversationId: String, unseenMessages: List<ChatMessage>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (unseenMessages.isEmpty()) return Result.success(Unit)
        return try {
            val batch = db.batch()
            unseenMessages.take(25).forEach { msg ->
                val ref = db.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                val nextSeen = (msg.seenBy + uid).distinct()
                val nextDelivered = (msg.deliveredTo + uid).distinct()
                batch.update(
                    ref,
                    mapOf(
                        "seenBy" to nextSeen,
                        "deliveredTo" to nextDelivered,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages")
            Result.failure(e)
        }
    }

    suspend fun startCall(
        conversation: Conversation,
        callerName: String,
        callType: String
    ): Result<String> {
        val callRef = db.collection("calls").document()
        return try {
            val uid = requireUserId()
            val calleeIds = conversation.participantIds.filterNot { it == uid }
            val pseudoSdp = "v=0\r\no=- ${UUID.randomUUID()} 2 IN IP4 127.0.0.1\r\ns=CircleChat-$callType\r\nt=0 0\r\nm=audio 9 RTP/SAVPF 111\r\n"
            val payload = mapOf(
                "conversationId" to conversation.id,
                "callerId" to uid,
                "callerName" to callerName.take(60),
                "calleeIds" to calleeIds,
                "participantIds" to conversation.participantIds,
                "callType" to callType,
                "status" to "ringing",
                "offerSdp" to pseudoSdp,
                "answerSdp" to "",
                "callerMuted" to false,
                "callerCameraOn" to (callType == "video"),
                "screenSharing" to false,
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            callRef.set(payload).await()
            Result.success(callRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, callRef.path)
            Result.failure(e)
        }
    }

    suspend fun answerCall(callId: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            val answerSdp = "v=0\r\no=- ${UUID.randomUUID()} 2 IN IP4 127.0.0.1\r\ns=CircleChat-Answer\r\nt=0 0\r\n"
            db.document(path).update(
                mapOf(
                    "status" to "active",
                    "answerSdp" to answerSdp,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCallControls(
        callId: String,
        muted: Boolean,
        cameraOn: Boolean,
        screenSharing: Boolean,
        durationSeconds: Long
    ): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "callerMuted" to muted,
                    "callerCameraOn" to cameraOn,
                    "screenSharing" to screenSharing,
                    "durationSeconds" to durationSeconds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun endOrDeclineCall(
        call: CallSession,
        conversation: Conversation?,
        currentUserName: String,
        finalStatus: String,
        durationSeconds: Long
    ): Result<Unit> {
        val path = "calls/${call.id}"
        return try {
            requireUserId()
            if (call.status == "ringing" || call.status == "active") {
                db.document(path).update(
                    mapOf(
                        "status" to finalStatus,
                        "durationSeconds" to durationSeconds.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            if (conversation != null) {
                val label = when (finalStatus) {
                    "declined" -> "Declined ${call.callType} call"
                    "missed" -> "Missed ${call.callType} call"
                    else -> {
                        val mins = durationSeconds / 60
                        val secs = durationSeconds % 60
                        val prefix = if (call.callType == "video") "Video Call" else "Audio Call"
                        "$prefix • ${mins}m ${secs}s"
                    }
                }
                sendMessage(
                    conversation = conversation,
                    senderName = currentUserName,
                    type = "call_log",
                    content = label,
                    mediaMeta = mapOf(
                        "callType" to call.callType,
                        "callStatus" to finalStatus,
                        "durationSeconds" to durationSeconds
                    )
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun previewForMessage(msg: ChatMessage): String = when (msg.type) {
        "image" -> "📷 Photo"
        "video" -> "🎬 Video"
        "voice" -> "🎤 Voice note"
        "audio" -> "🎵 Audio"
        "file" -> "📎 ${msg.mediaString("fileName").ifBlank { "File" }}"
        "poll" -> "📊 ${msg.parsedPoll()?.question ?: msg.content}"
        "gif" -> "GIF"
        else -> msg.content
    }
}
