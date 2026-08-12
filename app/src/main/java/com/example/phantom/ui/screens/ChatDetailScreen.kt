package com.example.phantom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.phantom.data.db.MessageEntity
import com.example.phantom.data.db.SessionEntity
import com.example.ui.theme.PhantomBackground
import com.example.ui.theme.PhantomOutline
import com.example.ui.theme.PhantomPrimary
import com.example.ui.theme.PhantomPrimaryVariant
import com.example.ui.theme.PhantomSecondary
import com.example.ui.theme.PhantomSurface
import com.example.ui.theme.PhantomSurfaceVariant
import com.example.ui.theme.PhantomTertiary

@Composable
fun ChatDetailScreen(
    contact: FriendshipEntity,
    messages: List<MessageEntity>,
    session: SessionEntity?,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenKeyVerification: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showRatchetInspector by remember { mutableStateOf(false) }

    // Map to track per-message raw ciphertext visibility toggle on tap
    val showCiphertextMap = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showRatchetInspector) {
        RatchetInspectorDialog(
            session = session,
            onDismiss = { showRatchetInspector = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBackground)
    ) {
        // Chat Header Bar
        Surface(
            color = PhantomSurface,
            tonalElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, PhantomOutline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    PhantomPrimary.copy(alpha = 0.3f),
                                    PhantomSecondary.copy(alpha = 0.2f)
                                )
                            )
                        )
                        .border(1.dp, PhantomOutline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.friendDisplayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Name & Safety Status (Tap to verify)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenKeyVerification() }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.friendDisplayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (contact.isVerifiedKey) Icons.Default.Verified else Icons.Default.Shield,
                            contentDescription = "Verification Status",
                            tint = if (contact.isVerifiedKey) PhantomTertiary else PhantomOutline,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = if (contact.isVerifiedKey) "Key Verified ✓ • Tap for Fingerprint" else "Unverified Key • Tap to compare Safety Number",
                        fontSize = 11.sp,
                        color = if (contact.isVerifiedKey) PhantomTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Double Ratchet Live Inspector Icon
                IconButton(
                    onClick = { showRatchetInspector = true },
                    modifier = Modifier.testTag("inspect_ratchet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Inspect Double Ratchet Session",
                        tint = PhantomSecondary
                    )
                }
            }
        }

        // Messages Conversation Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                // Encryption Banner at start of conversation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = PhantomSurface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, PhantomOutline.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = PhantomTertiary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "End-to-End Encrypted via Signal Double Ratchet. Tap message to inspect AES ciphertext.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            items(messages) { msg ->
                val isOutgoing = msg.isOutgoing
                val isShowingCiphertext = showCiphertextMap[msg.messageId] ?: false

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (isOutgoing) PhantomPrimary else PhantomSurfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isOutgoing) 18.dp else 4.dp,
                            bottomEnd = if (isOutgoing) 4.dp else 18.dp
                        ),
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isOutgoing) 18.dp else 4.dp,
                                    bottomEnd = if (isOutgoing) 4.dp else 18.dp
                                )
                            )
                            .clickable { showCiphertextMap[msg.messageId] = !isShowingCiphertext }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (isShowingCiphertext) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "AES-256-GCM CIPHERTEXT",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PhantomTertiary
                                        )
                                        Text(
                                            text = "TAP FOR PLAIN",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "0x${msg.ciphertextHex}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = PhantomTertiary,
                                        modifier = Modifier
                                            .background(PhantomBackground.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                            .padding(6.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = msg.plaintext,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Seq #${msg.sequenceNumber}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = if (isOutgoing) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "AES-GCM",
                                    tint = if (isOutgoing) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else PhantomTertiary,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Input Area
        Surface(
            color = PhantomSurface,
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, PhantomOutline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Write encrypted message...", fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PhantomPrimary,
                        unfocusedBorderColor = PhantomOutline,
                        focusedContainerColor = PhantomBackground,
                        unfocusedContainerColor = PhantomBackground
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    modifier = Modifier.testTag("send_message_button"),
                    enabled = messageText.isNotBlank()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (messageText.isNotBlank()) PhantomPrimary else PhantomSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
