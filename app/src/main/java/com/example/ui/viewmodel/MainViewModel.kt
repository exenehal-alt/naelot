package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CallSession
import com.example.data.model.CircleMemberSummary
import com.example.data.model.Conversation
import com.example.data.model.FriendCircle
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.CircleChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: CircleChatRepository,
    val currentUserId: String
) : ViewModel() {

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }

    val userState: StateFlow<UiState<UserProfile?>> = repository.observeCurrentUser(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val circleState: StateFlow<UiState<FriendCircle?>> = repository.observeUserCircle(currentUserId)
        .map<FriendCircle?, UiState<FriendCircle?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing circle", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load circle"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val conversationsState: StateFlow<UiState<List<Conversation>>> = repository.observeConversations(currentUserId)
        .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing conversations", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load conversations"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val activeCallsState: StateFlow<List<CallSession>> = repository.observeActiveCalls(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing calls", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun selectConversation(conversationId: String?) {
        _selectedConversationId.value = conversationId
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun completeOnboarding(
        name: String,
        username: String,
        statusText: String,
        avatarData: String,
        inviteCode: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val res = repository.joinOrCreateCircleAndSaveProfile(
                name = name,
                username = username,
                statusText = statusText,
                avatarData = avatarData,
                inviteCode = inviteCode
            )
            _isBusy.value = false
            res.onSuccess { onSuccess() }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to join circle" }
        }
    }

    fun updateProfile(
        currentUser: UserProfile,
        name: String,
        username: String,
        statusText: String,
        avatarData: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val res = repository.updateProfile(currentUser, name, username, statusText, avatarData)
            _isBusy.value = false
            res.onSuccess { onSuccess() }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update profile" }
        }
    }

    fun setOnlineStatus(online: Boolean) {
        val current = (userState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.setOnlineStatus(current, online)
        }
    }

    fun updateCircleSettings(circleId: String, name: String, inviteCode: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateCircleSettings(circleId, name, inviteCode)
            _isBusy.value = false
            res.onSuccess { onSuccess() }
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update circle" }
        }
    }

    fun openDirectMessage(
        currentUser: UserProfile,
        friend: CircleMemberSummary,
        onOpened: (String) -> Unit
    ) {
        val existing = (conversationsState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.getOrCreateDirectConversation(currentUser, friend, existing)
            _isBusy.value = false
            res.onSuccess { convId ->
                _selectedConversationId.value = convId
                onOpened(convId)
            }.onFailure {
                _actionError.value = it.localizedMessage ?: "Could not open chat"
            }
        }
    }

    fun createGroupChat(
        currentUser: UserProfile,
        groupName: String,
        members: List<CircleMemberSummary>,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createGroupConversation(currentUser, groupName, members)
            _isBusy.value = false
            res.onSuccess { convId ->
                _selectedConversationId.value = convId
                onCreated(convId)
            }.onFailure {
                _actionError.value = it.localizedMessage ?: "Could not create group"
            }
        }
    }

    fun answerCall(callId: String) {
        viewModelScope.launch {
            repository.answerCall(callId)
                .onFailure { _actionError.value = it.localizedMessage ?: "Failed to answer call" }
        }
    }

    fun updateCallControls(call: CallSession, muted: Boolean, cameraOn: Boolean, screenSharing: Boolean, durationSec: Long) {
        viewModelScope.launch {
            repository.updateCallControls(call.id, muted, cameraOn, screenSharing, durationSec)
        }
    }

    fun endOrDeclineCall(
        call: CallSession,
        finalStatus: String,
        durationSeconds: Long
    ) {
        val conversations = (conversationsState.value as? UiState.Success)?.data ?: emptyList()
        val conv = conversations.firstOrNull { it.id == call.conversationId }
        val currentName = (userState.value as? UiState.Success)?.data?.name ?: "Friend"
        viewModelScope.launch {
            repository.endOrDeclineCall(call, conv, currentName, finalStatus, durationSeconds)
        }
    }
}
