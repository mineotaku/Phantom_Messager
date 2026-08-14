package com.example.phantom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.UserEntity
import com.example.phantom.data.network.ProfilePayload
import com.example.ui.theme.*

@Composable
fun SocialScreen(
    currentUser: UserEntity?,
    searchQuery: String,
    searchResults: List<ProfilePayload>,
    isSearching: Boolean,
    searchError: String?,
    friendships: List<FriendshipEntity>,
    onSearch: (String) -> Unit,
    onSendFriendRequest: (String) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onBlockUser: (String) -> Unit
) {
    var queryText by remember { mutableStateOf(searchQuery) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBackground)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Contacts",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = PhantomOnBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Find and connect with people",
                style = MaterialTheme.typography.bodyMedium,
                color = PhantomOnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = queryText,
                onValueChange = {
                    queryText = it
                    onSearch(it)
                },
                placeholder = { Text("Search by username...", color = PhantomTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PhantomTextMuted, modifier = Modifier.size(20.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("social_search_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = PhantomSurfaceVariant,
                    unfocusedContainerColor = PhantomSurfaceVariant
                ),
                singleLine = true
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Loading indicator
            if (isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = PhantomPrimary
                        )
                    }
                }
            }

            // Error message
            if (searchError != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PhantomError.copy(alpha = 0.12f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = PhantomError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = searchError ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhantomError
                        )
                    }
                }
            }

            // Minimum query hint
            if (queryText.isNotEmpty() && queryText.trim().length < 2 && !isSearching && searchError == null) {
                item {
                    Text(
                        text = "Type at least 2 characters to search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                }
            }

            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Results",
                        style = MaterialTheme.typography.titleSmall,
                        color = PhantomOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(searchResults) { profile ->
                    if (profile.userId != currentUser?.userId) {
                        val friendship = friendships.find { it.friendUserId == profile.userId }
                        val friendshipStatus = friendship?.status

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(PhantomSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (profile.displayName ?: profile.username ?: "Unknown").take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = PhantomOnSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.displayName ?: profile.username ?: "Unknown", fontWeight = FontWeight.Bold, color = PhantomOnBackground, fontSize = 16.sp)
                                Text("@${profile.username ?: "unknown"}", style = MaterialTheme.typography.bodyMedium, color = PhantomTextMuted)
                            }

                            when (friendshipStatus) {
                                "ACCEPTED" -> {
                                    Icon(Icons.Default.Check, contentDescription = "Friends", tint = PhantomPrimary)
                                }
                                "PENDING_SENT" -> {
                                    Text("Sent", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PhantomOnSurfaceVariant)
                                }
                                "PENDING_RECEIVED" -> {
                                    Button(
                                        onClick = { onAcceptRequest(profile.userId) },
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("accept_search_${profile.username}")
                                    ) {
                                        Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PhantomOnBackground)
                                    }
                                }
                                "BLOCKED" -> {
                                    Icon(Icons.Default.Block, contentDescription = "Blocked", tint = PhantomError)
                                }
                                else -> {
                                    Button(
                                        onClick = { onSendFriendRequest(profile.userId) },
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("add_friend_${profile.username}")
                                    ) {
                                        Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PhantomOnBackground)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = PhantomDivider, thickness = 1.dp)
                    }
                }

                // No results found (after search completed with 0 results)
            } else if (queryText.trim().length >= 2 && !isSearching && searchError == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No users found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = PhantomOnBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try a different username or display name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhantomTextMuted
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your Contacts",
                    style = MaterialTheme.typography.titleSmall,
                    color = PhantomOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (friendships.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No contacts yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = PhantomOnBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Search for people above to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhantomTextMuted
                        )
                    }
                }
            } else {
                items(friendships) { friendship ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PhantomSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = friendship.friendDisplayName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = PhantomOnSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(friendship.friendDisplayName, fontWeight = FontWeight.Bold, color = PhantomOnBackground, fontSize = 16.sp)
                            
                            val statusColor = when (friendship.status) {
                                "ACCEPTED" -> PhantomSuccess
                                "PENDING_SENT" -> PhantomOnSurfaceVariant
                                "PENDING_RECEIVED" -> PhantomTertiary
                                "BLOCKED" -> PhantomError
                                else -> PhantomOnSurfaceVariant
                            }
                            
                            val statusText = when (friendship.status) {
                                "ACCEPTED" -> "Friends"
                                "PENDING_SENT" -> "Request Sent"
                                "PENDING_RECEIVED" -> "Pending Approval"
                                "BLOCKED" -> "Blocked"
                                else -> friendship.status
                            }

                            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                        }

                        when (friendship.status) {
                            "PENDING_RECEIVED" -> {
                                Row {
                                    IconButton(
                                        onClick = { onAcceptRequest(friendship.friendUserId) },
                                        modifier = Modifier.testTag("accept_friend_${friendship.friendUsername}")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = PhantomSuccess)
                                    }
                                    IconButton(
                                        onClick = { onBlockUser(friendship.friendUserId) },
                                        modifier = Modifier.testTag("block_user_${friendship.friendUsername}")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Block", tint = PhantomError)
                                    }
                                }
                            }
                            "BLOCKED" -> {
                                Text("Blocked", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PhantomError)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = PhantomDivider, thickness = 1.dp)
                }
            }
        }
    }
}
