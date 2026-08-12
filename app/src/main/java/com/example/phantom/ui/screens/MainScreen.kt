package com.example.phantom.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.phantom.ui.PhantomViewModel
import com.example.ui.theme.PhantomOutline
import com.example.ui.theme.PhantomPrimary
import com.example.ui.theme.PhantomSurface

sealed class PhantomNavRoute {
    object Chats : PhantomNavRoute()
    object Social : PhantomNavRoute()
    object Profile : PhantomNavRoute()
}

@Composable
fun MainScreen(viewModel: PhantomViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val friends by viewModel.currentFriends.collectAsState()
    val friendships by viewModel.currentFriendships.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val activeContact by viewModel.activeChatContact.collectAsState()
    val activeMessages by viewModel.activeMessages.collectAsState()
    val activeSession by viewModel.activeSessionState.collectAsState()
    val serverEvents by viewModel.serverEvents.collectAsState()

    var currentTab by remember { mutableStateOf<PhantomNavRoute>(PhantomNavRoute.Chats) }
    var isVerifyingKeyForContact by remember { mutableStateOf(false) }

    if (currentUser == null) {
        AuthScreen(
            onRegister = { username, displayName, avatar, bio ->
                viewModel.registerAccount(username, displayName, avatar, bio)
            }
        )
        return
    }

    if (isVerifyingKeyForContact && activeContact != null) {
        KeyVerificationScreen(
            currentUser = currentUser,
            contact = activeContact!!,
            onBack = { isVerifyingKeyForContact = false },
            onToggleVerified = { isVerified ->
                viewModel.toggleKeyVerified(activeContact!!.friendUserId, isVerified)
            }
        )
        return
    }

    if (activeContact != null) {
        ChatDetailScreen(
            contact = activeContact!!,
            messages = activeMessages,
            session = activeSession,
            onBack = { viewModel.openChatWith(activeContact!!.copy(friendUserId = "")) },
            onSendMessage = { text -> viewModel.sendMessage(text) },
            onOpenKeyVerification = { isVerifyingKeyForContact = true }
        )
        return
    }

    Scaffold(
        bottomBar = {
            Surface(
                color = PhantomSurface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, PhantomOutline.copy(alpha = 0.5f))
            ) {
                NavigationBar(
                    containerColor = PhantomSurface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == PhantomNavRoute.Chats,
                        onClick = { currentTab = PhantomNavRoute.Chats },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                        label = { Text("Chats") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PhantomPrimary,
                            indicatorColor = PhantomPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_chats")
                    )

                    NavigationBarItem(
                        selected = currentTab == PhantomNavRoute.Social,
                        onClick = { currentTab = PhantomNavRoute.Social },
                        icon = { Icon(Icons.Default.Group, contentDescription = "Social") },
                        label = { Text("Social") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PhantomPrimary,
                            indicatorColor = PhantomPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_social")
                    )

                    NavigationBarItem(
                        selected = currentTab == PhantomNavRoute.Profile,
                        onClick = { currentTab = PhantomNavRoute.Profile },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Vault") },
                        label = { Text("Vault") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PhantomPrimary,
                            indicatorColor = PhantomPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_profile")
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentTab) {
                PhantomNavRoute.Chats -> ChatsListScreen(
                    currentUser = currentUser,
                    friends = friends,
                    onSelectContact = { friendship -> viewModel.openChatWith(friendship) },
                    onSwitchUser = { userId -> viewModel.switchActiveUser(userId) },
                    onOpenSocial = { currentTab = PhantomNavRoute.Social }
                )
                PhantomNavRoute.Social -> SocialScreen(
                    currentUser = currentUser,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    friendships = friendships,
                    onSearch = { query -> viewModel.searchUsers(query) },
                    onSendFriendRequest = { profile -> viewModel.sendFriendRequest(profile) },
                    onAcceptRequest = { friendUserId -> viewModel.acceptFriendRequest(friendUserId) },
                    onBlockUser = { friendUserId -> viewModel.blockUser(friendUserId) }
                )
                PhantomNavRoute.Profile -> ProfileScreen(
                    currentUser = currentUser,
                    serverEvents = serverEvents
                )
            }
        }
    }
}
