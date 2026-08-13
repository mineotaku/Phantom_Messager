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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.UserEntity
import com.example.ui.theme.PhantomBackground
import com.example.ui.theme.PhantomOnlineGreen
import com.example.ui.theme.PhantomOnSurfaceVariant
import com.example.ui.theme.PhantomOutline
import com.example.ui.theme.PhantomPrimary
import com.example.ui.theme.PhantomSecondary
import com.example.ui.theme.PhantomSurfaceVariant
import com.example.ui.theme.PhantomTertiary
import com.example.ui.theme.PhantomUnreadBadge

@Composable
fun ChatsListScreen(
    currentUser: UserEntity?,
    friends: List<FriendshipEntity>,
    onSelectContact: (FriendshipEntity) -> Unit,
    onSwitchUser: (String) -> Unit,
    onOpenSocial: () -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }

    val filteredFriends = friends.filter {
        it.friendDisplayName.contains(filterQuery, ignoreCase = true) ||
                it.friendUsername.contains(filterQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Search...", fontSize = 15.sp) },
                leadingIcon = { 
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = null, 
                        tint = PhantomOnSurfaceVariant, 
                        modifier = Modifier.size(20.dp)
                    ) 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = PhantomSurfaceVariant.copy(alpha = 0.8f),
                    unfocusedContainerColor = PhantomSurfaceVariant.copy(alpha = 0.5f)
                ),
                singleLine = true
            )
        }

        if (filteredFriends.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Say Hello \uD83D\uDC4B",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your friends will appear here once you start chatting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomOnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredFriends) { friendship ->
                    ChatItemRow(
                        friendship = friendship,
                        onClick = { onSelectContact(friendship) }
                    )
                    HorizontalDivider(
                        color = PhantomOutline.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 86.dp) // Aligns with the text content
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatItemRow(
    friendship: FriendshipEntity,
    onClick: () -> Unit
) {
    val initial = friendship.friendDisplayName.take(1).uppercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(54.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                PhantomPrimary.copy(alpha = 0.8f),
                                PhantomSecondary.copy(alpha = 0.8f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(PhantomOnlineGreen)
                    .border(2.dp, PhantomBackground, CircleShape)
                    .align(Alignment.BottomEnd)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = friendship.friendDisplayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (friendship.isVerifiedKey) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = PhantomTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "now",
                    style = MaterialTheme.typography.bodySmall,
                    color = PhantomOnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to start chatting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PhantomOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .background(PhantomUnreadBadge, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "1",
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
