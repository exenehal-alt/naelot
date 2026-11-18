package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CircleMemberSummary
import com.example.data.model.Conversation
import com.example.data.model.FriendCircle
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.CreateGroupDialog
import com.example.ui.components.FriendAvatar
import com.example.ui.components.ProfileSheet
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    currentUser: UserProfile,
    circle: FriendCircle?,
    conversationsState: UiState<List<Conversation>>,
    selectedConversationId: String?,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onOpenConversation: (String) -> Unit,
    onStartDirectChat: (CircleMemberSummary) -> Unit,
    onCreateGroup: (String, List<CircleMemberSummary>) -> Unit,
    onSaveProfile: (name: String, username: String, status: String, avatar: String) -> Unit,
    onSignOut: () -> Unit,
    detailPane: @Composable (Conversation) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val allMembers = remember(circle) { circle?.parsedMembers() ?: emptyList() }
    val otherFriends = remember(allMembers, currentUser.userId) {
        allMembers.filter { it.userId != currentUser.userId }
    }
    val conversations = (conversationsState as? UiState.Success)?.data ?: emptyList()
    val filteredConversations = remember(conversations, searchQuery, currentUser.userId) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) conversations
        else conversations.filter { conv ->
            val title = if (conv.groupChat) conv.groupName
            else conv.participantNames.entries.firstOrNull { it.key != currentUser.userId }?.value ?: ""
            title.lowercase().contains(q) || conv.lastMessageText.lowercase().contains(q)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 700.dp

        if (isExpandedLayout) {
            // Canonical Tablet / Foldable NavigationRail + List-Detail Two-Pane Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                NavigationRail(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(Modifier.height(8.dp))
                    IconButton(onClick = { showProfileSheet = true }) {
                        FriendAvatar(currentUser.name, currentUser.avatarData, size = 36.dp, showOnlineDot = true, isOnline = true)
                    }
                    Spacer(Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Icon(if (activeTab == 0) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline, contentDescription = "Chats") },
                        label = { Text("Chats") }
                    )
                    NavigationRailItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Icon(if (activeTab == 1) Icons.Filled.People else Icons.Outlined.People, contentDescription = "Friends") },
                        label = { Text("Friends") }
                    )
                    NavigationRailItem(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        icon = { Icon(if (activeTab == 2) Icons.Filled.Group else Icons.Outlined.Group, contentDescription = "Circle") },
                        label = { Text("Circle") }
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onToggleDarkMode(!isDarkMode) }) {
                        Icon(
                            if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme"
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Left List Pane
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                ) {
                    Scaffold(
                        contentWindowInsets = WindowInsets.statusBars,
                        floatingActionButton = {
                            FloatingActionButton(onClick = { showCreateGroupDialog = true }) {
                                Icon(Icons.Default.GroupAdd, contentDescription = "New group chat")
                            }
                        }
                    ) { innerPadding ->
                        HomeListContent(
                            activeTab = activeTab,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            currentUser = currentUser,
                            circle = circle,
                            friends = otherFriends,
                            conversations = filteredConversations,
                            selectedConversationId = selectedConversationId,
                            onOpenConversation = onOpenConversation,
                            onStartDirectChat = onStartDirectChat,
                            onOpenProfile = { showProfileSheet = true },
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = onToggleDarkMode,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                // Right Detail Pane
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val selectedConv = conversations.firstOrNull { it.id == selectedConversationId }
                    if (selectedConv != null) {
                        detailPane(selectedConv)
                    } else {
                        EmptyDetailPane()
                    }
                }
            }
        } else {
            // Compact Phone Layout
            Scaffold(
                contentWindowInsets = WindowInsets.statusBars,
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            icon = { Icon(if (activeTab == 0) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline, contentDescription = "Chats") },
                            label = { Text("Chats") },
                            modifier = Modifier.testTag("nav_tab_chats")
                        )
                        NavigationBarItem(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            icon = { Icon(if (activeTab == 1) Icons.Filled.People else Icons.Outlined.People, contentDescription = "Friends") },
                            label = { Text("Friends") },
                            modifier = Modifier.testTag("nav_tab_friends")
                        )
                        NavigationBarItem(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            icon = { Icon(if (activeTab == 2) Icons.Filled.Group else Icons.Outlined.Group, contentDescription = "Circle") },
                            label = { Text("Circle") },
                            modifier = Modifier.testTag("nav_tab_circle")
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showCreateGroupDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("new_group_fab")
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "Create private group")
                    }
                }
            ) { innerPadding ->
                HomeListContent(
                    activeTab = activeTab,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    currentUser = currentUser,
                    circle = circle,
                    friends = otherFriends,
                    conversations = filteredConversations,
                    selectedConversationId = selectedConversationId,
                    onOpenConversation = onOpenConversation,
                    onStartDirectChat = onStartDirectChat,
                    onOpenProfile = { showProfileSheet = true },
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    if (showProfileSheet) {
        ProfileSheet(
            user = currentUser,
            circle = circle,
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            onSaveProfile = onSaveProfile,
            onSignOut = onSignOut,
            onDismiss = { showProfileSheet = false }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            friends = otherFriends,
            onDismiss = { showCreateGroupDialog = false },
            onCreateGroup = onCreateGroup
        )
    }
}

@Composable
private fun HomeListContent(
    activeTab: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currentUser: UserProfile,
    circle: FriendCircle?,
    friends: List<CircleMemberSummary>,
    conversations: List<Conversation>,
    selectedConversationId: String?,
    onOpenConversation: (String) -> Unit,
    onStartDirectChat: (CircleMemberSummary) -> Unit,
    onOpenProfile: () -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "CircleChat",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    text = "${circle?.name ?: "Inner Circle"} • @${currentUser.username}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { onToggleDarkMode(!isDarkMode) },
                    modifier = Modifier.testTag("header_theme_toggle")
                ) {
                    Icon(
                        if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle light/dark theme"
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenProfile)
                        .testTag("open_profile_button")
                ) {
                    FriendAvatar(
                        name = currentUser.name,
                        avatarData = currentUser.avatarData,
                        size = 40.dp,
                        showOnlineDot = true,
                        isOnline = true
                    )
                }
            }
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search friends & chats...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
        )

        when (activeTab) {
            0 -> {
                // Active Friends Strip
                if (friends.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        items(friends, key = { it.userId }) { friend ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(62.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onStartDirectChat(friend) }
                                    .padding(vertical = 4.dp)
                            ) {
                                FriendAvatar(
                                    name = friend.name,
                                    avatarData = friend.avatarData,
                                    size = 50.dp,
                                    showOnlineDot = true,
                                    isOnline = friend.online
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = friend.name.substringBefore(" "),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (conversations.isEmpty()) {
                    EmptyChatsState(friendsCount = friends.size)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(conversations, key = { it.id }) { conv ->
                            val peerUid = conv.participantIds.firstOrNull { it != currentUser.userId }
                            val peer = friends.firstOrNull { it.userId == peerUid }
                            val title = if (conv.groupChat) conv.groupName.ifBlank { "Group Chat" }
                            else peer?.name ?: conv.participantNames[peerUid] ?: "Direct Chat"
                            val avatar = if (conv.groupChat) conv.groupAvatar else peer?.avatarData ?: "preset:cyan"
                            val selected = conv.id == selectedConversationId
                            val timeStr = remember(conv.lastMessageAt) {
                                val ms = conv.lastMessageAt?.toDate()?.time
                                if (ms != null) SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms)) else ""
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onOpenConversation(conv.id) }
                                    .testTag("conversation_card_${conv.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FriendAvatar(
                                        name = title,
                                        avatarData = avatar,
                                        size = 48.dp,
                                        showOnlineDot = !conv.groupChat,
                                        isOnline = peer?.online == true
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = timeStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        val typingOther = conv.typingUserIds.any { it != currentUser.userId }
                                        Text(
                                            text = if (typingOther) "typing..." else conv.lastMessageText.ifBlank { "No messages yet" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (typingOther) EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Friends Directory Tab
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (friends.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Share Your Circle Invite Code",
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Text(
                                        text = "When your friends sign in and enter invite code ${circle?.inviteCode ?: "FRIENDS26"}, they appear here automatically for 1-on-1 DMs and calls.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(friends, key = { it.userId }) { friend ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onStartDirectChat(friend) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FriendAvatar(
                                        name = friend.name,
                                        avatarData = friend.avatarData,
                                        size = 46.dp,
                                        showOnlineDot = true,
                                        isOnline = friend.online
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(friend.name, fontWeight = FontWeight.Bold)
                                        Text("@${friend.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        if (friend.statusText.isNotBlank()) {
                                            Text(
                                                friend.statusText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // Circle Info Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = circle?.name ?: "Inner Circle",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Invite Code: ${circle?.inviteCode ?: "FRIENDS26"}",
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(circle?.memberUids?.size ?: 1)} member(s) • End-to-end Firestore persistence",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = "Tip: Tap your avatar in the top-right to customize your display name, Photo Picker avatar, status message, or switch between Dark and Light mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsState(friendsCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_chats),
            contentDescription = "No conversations yet",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Your Squad Space Is Ready",
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (friendsCount == 0) "Tap the + button to create a private group, or invite friends with your circle code."
            else "Tap any friend above or create a private group chat to start messaging.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyDetailPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyChatsState(friendsCount = 1)
    }
}
