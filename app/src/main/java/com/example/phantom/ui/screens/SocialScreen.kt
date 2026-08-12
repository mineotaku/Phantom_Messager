package com.example.phantom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.UserEntity
import com.example.phantom.data.relay.RelayServer
import com.example.ui.theme.PhantomBackground
import com.example.ui.theme.PhantomError
import com.example.ui.theme.PhantomOutline
import com.example.ui.theme.PhantomPrimary
import com.example.ui.theme.PhantomPrimaryVariant
import com.example.ui.theme.PhantomSecondary
import com.example.ui.theme.PhantomSurface
import com.example.ui.theme.PhantomSurfaceVariant
import com.example.ui.theme.PhantomTertiary

@Composable
fun SocialScreen(
    currentUser: UserEntity?,
    searchQuery: String,
    searchResults: List<RelayServer.PublicUserProfile>,
    friendships: List<FriendshipEntity>,
    onSearch: (String) -> Unit,
    onSendFriendRequest: (RelayServer.PublicUserProfile) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onBlockUser: (String) -> Unit
) {
    var queryText by remember { mutableStateOf(searchQuery) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBackground)
    ) {
        // App Header Bar
        Surface(
            color = PhantomSurface,
            tonalElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, PhantomOutline.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PhantomSecondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = PhantomSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Network Contacts",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Discover profiles & establish X3DH prekey sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = queryText,
                    onValueChange = {
                        queryText = it
                        onSearch(it)
                    },
                    placeholder = { Text("Search @username or display name...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("social_search_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PhantomPrimary,
                        unfocusedBorderColor = PhantomOutline.copy(alpha = 0.5f),
                        focusedContainerColor = PhantomBackground,
                        unfocusedContainerColor = PhantomBackground
                    ),
                    singleLine = true
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "RELAY DIRECTORY SEARCH",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PhantomPrimaryVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(searchResults) { profile ->
                    if (profile.userId != currentUser?.userId) {
                        val isFriend = friendships.any { it.friendUserId == profile.userId && it.status == "ACCEPTED" }
                        val isPending = friendships.any { it.friendUserId == profile.userId && it.status.startsWith("PENDING") }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = PhantomSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, PhantomOutline.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    PhantomPrimary.copy(alpha = 0.3f),
                                                    PhantomSecondary.copy(alpha = 0.2f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profile.displayName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("@${profile.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (isFriend) {
                                    Text("CONNECTED", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PhantomTertiary)
                                } else if (isPending) {
                                    Text("PENDING", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PhantomSecondary)
                                } else {
                                    Button(
                                        onClick = { onSendFriendRequest(profile) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary),
                                        modifier = Modifier.testTag("add_friend_${profile.username}")
                                    ) {
                                        Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ESTABLISHED FRIENDS & REQUESTS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (friendships.isEmpty()) {
                item {
                    Text(
                        text = "No friends added yet. Use directory search above to discover contacts on the Relay network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(friendships) { friendship ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = PhantomSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, PhantomOutline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(PhantomSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = friendship.friendDisplayName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(friendship.friendDisplayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("@${friendship.friendUsername} • ${friendship.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            when (friendship.status) {
                                "PENDING_RECEIVED" -> {
                                    IconButton(
                                        onClick = { onAcceptRequest(friendship.friendUserId) },
                                        modifier = Modifier.testTag("accept_friend_${friendship.friendUsername}")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Accept Request", tint = PhantomTertiary)
                                    }
                                }
                                "ACCEPTED" -> {
                                    IconButton(
                                        onClick = { onBlockUser(friendship.friendUserId) },
                                        modifier = Modifier.testTag("block_user_${friendship.friendUsername}")
                                    ) {
                                        Icon(Icons.Default.Block, contentDescription = "Block Contact", tint = PhantomError)
                                    }
                                }
                                "BLOCKED" -> {
                                    Text("BLOCKED", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PhantomError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
