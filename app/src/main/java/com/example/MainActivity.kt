package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.auth.attemptAutoSignIn
import com.example.data.auth.authStateFlow
import com.example.data.auth.signOut
import com.example.data.media.ChunkedFileEngine
import com.example.data.media.VoiceNoteManager
import com.example.data.model.UiState
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.CircleChatRepository
import com.example.ui.components.CallOverlay
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingGateScreen
import com.example.ui.theme.CircleChatTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val themePrefs = remember(context) { ThemePreferences(context.applicationContext) }
            val isDarkMode by themePrefs.isDarkMode.collectAsStateWithLifecycle(initialValue = true)

            CircleChatTheme(darkTheme = isDarkMode) {
                AppRoot(
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { enabled ->
                        scope.launch { themePrefs.setDarkMode(enabled) }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedCircleApp(
                currentUserId = user.uid,
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedCircleApp(
    currentUserId: String,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(CircleChatRepository(db), currentUserId)
            }
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mainViewModel.setOnlineStatus(true)
                Lifecycle.Event.ON_STOP -> mainViewModel.setOnlineStatus(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val userState by mainViewModel.userState.collectAsStateWithLifecycle()
    val circleState by mainViewModel.circleState.collectAsStateWithLifecycle()
    val conversationsState by mainViewModel.conversationsState.collectAsStateWithLifecycle()
    val activeCalls by mainViewModel.activeCallsState.collectAsStateWithLifecycle()
    val selectedConversationId by mainViewModel.selectedConversationId.collectAsStateWithLifecycle()
    val isBusy by mainViewModel.isBusy.collectAsStateWithLifecycle()
    val actionError by mainViewModel.actionError.collectAsStateWithLifecycle()

    val navController = rememberNavController()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val uState = userState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val profile = uState.data
                if (profile == null) {
                    OnboardingGateScreen(
                        isBusy = isBusy,
                        errorText = actionError,
                        onComplete = { name, username, status, avatar, invite ->
                            mainViewModel.completeOnboarding(name, username, status, avatar, invite)
                        },
                        onSignOutClick = onSignOut
                    )
                } else {
                    val circle = (circleState as? UiState.Success)?.data
                    val members = circle?.parsedMembers() ?: emptyList()
                    val conversations = (conversationsState as? UiState.Success)?.data ?: emptyList()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                currentUser = profile,
                                circle = circle,
                                conversationsState = conversationsState,
                                selectedConversationId = selectedConversationId,
                                isDarkMode = isDarkMode,
                                onToggleDarkMode = onToggleDarkMode,
                                onOpenConversation = { convId ->
                                    mainViewModel.selectConversation(convId)
                                    navController.navigate("chat/$convId")
                                },
                                onStartDirectChat = { friend ->
                                    mainViewModel.openDirectMessage(profile, friend) { convId ->
                                        navController.navigate("chat/$convId")
                                    }
                                },
                                onCreateGroup = { groupName, selected ->
                                    mainViewModel.createGroupChat(profile, groupName, selected) { convId ->
                                        navController.navigate("chat/$convId")
                                    }
                                },
                                onSaveProfile = { name, username, status, avatar ->
                                    mainViewModel.updateProfile(profile, name, username, status, avatar)
                                },
                                onSignOut = onSignOut,
                                detailPane = { conv ->
                                    val chatVm: ChatViewModel = viewModel(
                                        key = "${currentUserId}_${conv.id}",
                                        factory = viewModelFactory {
                                            initializer {
                                                val app = checkNotNull(this[APPLICATION_KEY])
                                                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                                ChatViewModel(
                                                    repository = CircleChatRepository(db),
                                                    chunkedFileEngine = ChunkedFileEngine(app),
                                                    voiceNoteManager = VoiceNoteManager(app),
                                                    conversationId = conv.id,
                                                    currentUserId = currentUserId
                                                )
                                            }
                                        }
                                    )
                                    ChatScreen(
                                        viewModel = chatVm,
                                        conversation = conv,
                                        currentUser = profile,
                                        circleMembers = members,
                                        showBackButton = false,
                                        onBack = {}
                                    )
                                }
                            )
                        }

                        composable(
                            route = "chat/{conversationId}",
                            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val convId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
                            val conv = conversations.firstOrNull { it.id == convId }
                            if (conv == null) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                val chatVm: ChatViewModel = viewModel(
                                    key = "${currentUserId}_$convId",
                                    factory = viewModelFactory {
                                        initializer {
                                            val app = checkNotNull(this[APPLICATION_KEY])
                                            val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                            ChatViewModel(
                                                repository = CircleChatRepository(db),
                                                chunkedFileEngine = ChunkedFileEngine(app),
                                                voiceNoteManager = VoiceNoteManager(app),
                                                conversationId = convId,
                                                currentUserId = currentUserId
                                            )
                                        }
                                    }
                                )
                                ChatScreen(
                                    viewModel = chatVm,
                                    conversation = conv,
                                    currentUser = profile,
                                    circleMembers = members,
                                    showBackButton = true,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    // Active / Incoming Real-Time Call Overlay
                    val activeCall = activeCalls.firstOrNull()
                    if (activeCall != null) {
                        val callConv = conversations.firstOrNull { it.id == activeCall.conversationId }
                        val peerUid = callConv?.participantIds?.firstOrNull { it != currentUserId }
                        val peerMember = members.firstOrNull { it.userId == peerUid }
                        val peerName = if (callConv?.groupChat == true) callConv.groupName else peerMember?.name ?: activeCall.callerName
                        val peerAvatar = if (callConv?.groupChat == true) callConv.groupAvatar else peerMember?.avatarData ?: "preset:cyan"

                        CallOverlay(
                            call = activeCall,
                            currentUserId = currentUserId,
                            peerName = peerName,
                            peerAvatar = peerAvatar,
                            onAccept = { mainViewModel.answerCall(activeCall.id) },
                            onDecline = {
                                mainViewModel.endOrDeclineCall(activeCall, "declined", 0L)
                            },
                            onUpdateControls = { muted, cam, screen, dur ->
                                mainViewModel.updateCallControls(activeCall, muted, cam, screen, dur)
                            },
                            onEndCall = { dur ->
                                val status = if (activeCall.status == "ringing") "missed" else "ended"
                                mainViewModel.endOrDeclineCall(activeCall, status, dur)
                            }
                        )
                    }
                }
            }
        }
    }
}
