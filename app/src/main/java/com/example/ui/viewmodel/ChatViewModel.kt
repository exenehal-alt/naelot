package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.media.ChunkedFileEngine
import com.example.data.media.GifItem
import com.example.data.media.PlaybackState
import com.example.data.media.RecordingState
import com.example.data.media.VoiceNoteManager
import com.example.data.model.ChatMessage
import com.example.data.model.ChunkTransferState
import com.example.data.model.Conversation
import com.example.data.model.RichTextStyleState
import com.example.data.model.UiState
import com.example.data.repository.CircleChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: CircleChatRepository,
    val chunkedFileEngine: ChunkedFileEngine,
    val voiceNoteManager: VoiceNoteManager,
    val conversationId: String,
    val currentUserId: String
) : ViewModel() {

    private companion object {
        const val TAG = "ChatViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }

    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        repository.observeMessages(conversationId, currentUserId)
            .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing messages", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to observe messages"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val chunkTransferState: StateFlow<ChunkTransferState?> = chunkedFileEngine.transferState
    val recordingState: StateFlow<RecordingState> = voiceNoteManager.recordingState
    val playbackState: StateFlow<PlaybackState> = voiceNoteManager.playbackState

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _styleState = MutableStateFlow(RichTextStyleState())
    val styleState: StateFlow<RichTextStyleState> = _styleState.asStateFlow()

    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private var typingTimeoutJob: Job? = null

    fun onComposerTextChanged(newText: String, conversation: Conversation) {
        _composerText.value = newText
        typingTimeoutJob?.cancel()
        if (newText.isNotBlank()) {
            viewModelScope.launch {
                repository.setTypingStatus(conversation.id, conversation.typingUserIds, true)
            }
            typingTimeoutJob = viewModelScope.launch {
                delay(2800L)
                repository.setTypingStatus(conversation.id, conversation.typingUserIds, false)
            }
        } else {
            viewModelScope.launch {
                repository.setTypingStatus(conversation.id, conversation.typingUserIds, false)
            }
        }
    }

    fun updateStyle(transform: (RichTextStyleState) -> RichTextStyleState) {
        _styleState.value = transform(_styleState.value)
    }

    fun appendEmojiToComposer(emoji: String) {
        _composerText.value += emoji
    }

    fun setReplyTarget(message: ChatMessage?) {
        _replyToMessage.value = message
        if (message != null) _editingMessage.value = null
    }

    fun startEditingMessage(message: ChatMessage) {
        _editingMessage.value = message
        _replyToMessage.value = null
        _composerText.value = message.content
    }

    fun cancelEditingOrReply() {
        _editingMessage.value = null
        _replyToMessage.value = null
        if (_editingMessage.value != null) _composerText.value = ""
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _errorBanner.value = null
    }

    fun sendTextOrSaveEdit(conversation: Conversation, senderName: String) {
        val text = _composerText.value.trim()
        if (text.isEmpty()) return
        val editing = _editingMessage.value
        val reply = _replyToMessage.value
        val style = _styleState.value

        _composerText.value = ""
        _editingMessage.value = null
        _replyToMessage.value = null
        typingTimeoutJob?.cancel()

        viewModelScope.launch {
            val res = if (editing != null) {
                repository.editMessage(conversation.id, editing.id, text)
            } else {
                repository.sendMessage(
                    conversation = conversation,
                    senderName = senderName,
                    type = "text",
                    content = text,
                    style = style,
                    replyTo = reply
                )
            }
            res.onFailure { _errorBanner.value = it.localizedMessage ?: "Failed to send message" }
        }
    }

    fun sendImages(uris: List<Uri>, conversation: Conversation, senderName: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.take(6).forEach { uri ->
                val vaultRes = chunkedFileEngine.copyMediaToVault(uri, "img")
                val b64 = chunkedFileEngine.encodeCompressedImageBase64(uri)
                vaultRes.onSuccess { (file, desc) ->
                    repository.sendMessage(
                        conversation = conversation,
                        senderName = senderName,
                        type = "image",
                        content = b64,
                        mediaMeta = mapOf(
                            "fileName" to desc.fileName,
                            "fileSize" to desc.fileSize,
                            "mimeType" to desc.mimeType,
                            "localPath" to file.absolutePath
                        ),
                        replyTo = _replyToMessage.value
                    ).onFailure { _errorBanner.value = it.localizedMessage }
                }.onFailure {
                    _errorBanner.value = it.localizedMessage ?: "Failed to process image"
                }
            }
            _replyToMessage.value = null
        }
    }

    fun sendVideo(uri: Uri, conversation: Conversation, senderName: String) {
        viewModelScope.launch {
            val res = chunkedFileEngine.copyMediaToVault(uri, "vid")
            res.onSuccess { (file, desc) ->
                val (durationMs, thumbB64) = chunkedFileEngine.extractVideoMetaAndThumbnail(file)
                repository.sendMessage(
                    conversation = conversation,
                    senderName = senderName,
                    type = "video",
                    content = desc.fileName,
                    mediaMeta = mapOf(
                        "fileName" to desc.fileName,
                        "fileSize" to file.length(),
                        "mimeType" to desc.mimeType,
                        "localPath" to file.absolutePath,
                        "durationMs" to durationMs,
                        "thumbnailData" to thumbB64
                    ),
                    replyTo = _replyToMessage.value
                ).onFailure { _errorBanner.value = it.localizedMessage }
                _replyToMessage.value = null
            }.onFailure {
                _errorBanner.value = it.localizedMessage ?: "Failed to attach video"
            }
        }
    }

    fun sendAudioFile(uri: Uri, conversation: Conversation, senderName: String) {
        viewModelScope.launch {
            val res = chunkedFileEngine.copyMediaToVault(uri, "aud")
            res.onSuccess { (file, desc) ->
                repository.sendMessage(
                    conversation = conversation,
                    senderName = senderName,
                    type = "audio",
                    content = desc.fileName,
                    mediaMeta = mapOf(
                        "fileName" to desc.fileName,
                        "fileSize" to file.length(),
                        "mimeType" to desc.mimeType,
                        "localPath" to file.absolutePath,
                        "durationMs" to 0L
                    ),
                    replyTo = _replyToMessage.value
                ).onFailure { _errorBanner.value = it.localizedMessage }
                _replyToMessage.value = null
            }.onFailure {
                _errorBanner.value = it.localizedMessage ?: "Failed to attach audio"
            }
        }
    }

    fun uploadLargeFileChunked(uri: Uri, conversation: Conversation, senderName: String) {
        val job = viewModelScope.launch {
            val transferRes = chunkedFileEngine.processChunkedUpload(uri)
            transferRes.onSuccess { done ->
                repository.sendMessage(
                    conversation = conversation,
                    senderName = senderName,
                    type = "file",
                    content = done.fileName,
                    mediaMeta = mapOf(
                        "fileName" to done.fileName,
                        "fileSize" to done.totalBytes,
                        "mimeType" to done.mimeType,
                        "localPath" to done.assembledPath,
                        "chunkCount" to done.totalChunks,
                        "sha256" to done.sha256,
                        "transferId" to done.transferId
                    ),
                    replyTo = _replyToMessage.value
                ).onFailure { _errorBanner.value = it.localizedMessage }
                _replyToMessage.value = null
                delay(1500L)
                chunkedFileEngine.clearTransferState()
            }.onFailure { err ->
                if (err !is CancellationException) {
                    _errorBanner.value = err.localizedMessage ?: "Chunked upload failed"
                }
            }
        }
        chunkedFileEngine.attachActiveJob(job)
    }

    fun cancelChunkedUpload() {
        chunkedFileEngine.cancelActiveTransfer()
    }

    fun startVoiceRecording() {
        voiceNoteManager.startRecording(viewModelScope).onFailure {
            _errorBanner.value = it.localizedMessage ?: "Unable to start microphone"
        }
    }

    fun stopAndSendVoiceRecording(save: Boolean, conversation: Conversation, senderName: String) {
        val recorded = voiceNoteManager.stopRecording(save)
        if (!save || recorded == null) return
        viewModelScope.launch {
            repository.sendMessage(
                conversation = conversation,
                senderName = senderName,
                type = "voice",
                content = "Voice Note",
                mediaMeta = mapOf(
                    "fileName" to recorded.file.name,
                    "fileSize" to recorded.file.length(),
                    "mimeType" to "audio/mp4",
                    "localPath" to recorded.file.absolutePath,
                    "durationMs" to recorded.durationMs,
                    "audioBase64" to recorded.base64Audio
                ),
                replyTo = _replyToMessage.value
            ).onFailure { _errorBanner.value = it.localizedMessage }
            _replyToMessage.value = null
        }
    }

    fun toggleAudioPlayback(message: ChatMessage) {
        voiceNoteManager.togglePlayAudio(
            messageId = message.id,
            localPath = message.mediaString("localPath"),
            base64Audio = message.mediaString("audioBase64"),
            scope = viewModelScope
        )
    }

    fun sendPoll(
        question: String,
        options: List<String>,
        allowMultiple: Boolean,
        conversation: Conversation,
        senderName: String
    ) {
        val cleanQuestion = question.trim()
        val cleanOpts = options.map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        if (cleanQuestion.isEmpty() || cleanOpts.size < 2) {
            _errorBanner.value = "Polls need a question and at least 2 options."
            return
        }
        val pollMap = mapOf(
            "question" to cleanQuestion,
            "allowMultiple" to allowMultiple,
            "options" to cleanOpts.mapIndexed { idx, optText ->
                mapOf(
                    "id" to "opt_$idx",
                    "text" to optText,
                    "voterIds" to emptyList<String>()
                )
            }
        )
        viewModelScope.launch {
            repository.sendMessage(
                conversation = conversation,
                senderName = senderName,
                type = "poll",
                content = cleanQuestion,
                pollData = pollMap
            ).onFailure { _errorBanner.value = it.localizedMessage }
        }
    }

    fun voteOnPoll(message: ChatMessage, optionId: String) {
        val poll = message.parsedPoll() ?: return
        viewModelScope.launch {
            repository.voteOnPoll(conversationId, message.id, optionId, poll)
                .onFailure { _errorBanner.value = it.localizedMessage }
        }
    }

    fun sendGif(gif: GifItem, conversation: Conversation, senderName: String) {
        viewModelScope.launch {
            repository.sendMessage(
                conversation = conversation,
                senderName = senderName,
                type = "gif",
                content = gif.title,
                mediaMeta = mapOf(
                    "url" to gif.previewUrl,
                    "title" to gif.title,
                    "emojiBadge" to gif.emojiBadge,
                    "accentHex" to gif.accentHex
                )
            ).onFailure { _errorBanner.value = it.localizedMessage }
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            repository.toggleReaction(conversationId, message.id, emoji, message.reactions)
        }
    }

    fun togglePin(conversation: Conversation, message: ChatMessage) {
        viewModelScope.launch {
            repository.togglePinMessage(conversation, message)
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            repository.deleteMessage(conversationId, message.id)
        }
    }

    fun markUnseenAsSeen(messages: List<ChatMessage>) {
        val unseen = messages.filter { it.senderId != currentUserId && !it.seenBy.contains(currentUserId) }
        if (unseen.isEmpty()) return
        viewModelScope.launch {
            repository.markMessagesSeen(conversationId, unseen)
        }
    }

    fun initiateCall(conversation: Conversation, callerName: String, callType: String) {
        viewModelScope.launch {
            repository.startCall(conversation, callerName, callType)
                .onFailure { _errorBanner.value = it.localizedMessage ?: "Unable to start call" }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceNoteManager.stopRecording(save = false)
        voiceNoteManager.stopPlayback()
    }
}
