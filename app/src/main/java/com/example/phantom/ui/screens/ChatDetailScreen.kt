package com.example.phantom.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.InsertDriveFile
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage

import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.MessageEntity
import com.example.phantom.data.db.SessionEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatDetailScreen(
    contact: FriendshipEntity,
    messages: List<MessageEntity>,
    session: SessionEntity?,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendMedia: (String, android.net.Uri, String?) -> Unit,
    onOpenKeyVerification: () -> Unit
) {
    val context = LocalContext.current
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            onSendMedia(messageText, uri, mimeType)
            messageText = ""
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Process messages into date groups
    val groupedMessages = remember(messages) {
        groupMessagesByDate(messages)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBackground)
    ) {
        // Header
        Surface(
            color = PhantomBackground,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PhantomOnBackground
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                        text = contact.friendDisplayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenKeyVerification() }
                ) {
                    Text(
                        text = contact.friendDisplayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PhantomOnBackground
                    )
                    Text(
                        text = if (contact.isVerifiedKey) "Verified" else "Online",
                        fontSize = 12.sp,
                        color = if (contact.isVerifiedKey) PhantomTertiary else PhantomPrimary
                    )
                }
            }
        }

        // Messages Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            groupedMessages.forEach { (dateHeader, msgs) ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dateHeader,
                            fontSize = 12.sp,
                            color = PhantomOnBackground,
                            modifier = Modifier
                                .background(PhantomSurfaceVariant, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                
                items(msgs) { msg ->
                    MessageBubble(msg)
                }
            }
        }

        // Input Bar
        Surface(
            color = PhantomSurface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { mediaPickerLauncher.launch("*/*") },
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach Media",
                        tint = PhantomOnSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Message...", fontSize = 15.sp, color = PhantomOnSurfaceVariant) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = PhantomSurfaceVariant,
                        unfocusedContainerColor = PhantomSurfaceVariant,
                        focusedTextColor = PhantomOnBackground,
                        unfocusedTextColor = PhantomOnBackground
                    ),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    modifier = Modifier.padding(bottom = 2.dp)
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
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank()) Color.White else PhantomOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: MessageEntity) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(msg.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = if (msg.isOutgoing) PhantomBubbleOutgoing else PhantomBubbleIncoming,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (msg.isOutgoing) 16.dp else 4.dp,
                        bottomEnd = if (msg.isOutgoing) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (msg.mediaUrl != null) {
                    when (msg.mediaType) {
                        "IMAGE" -> {
                            AsyncImage(
                                model = msg.mediaUrl,
                                contentDescription = "Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        "VIDEO" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayCircle, contentDescription = "Video", tint = if (msg.isOutgoing) PhantomOnBubbleOutgoing else PhantomOnBubbleIncoming)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Video Attachment", color = if (msg.isOutgoing) PhantomOnBubbleOutgoing else PhantomOnBubbleIncoming)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        "AUDIO" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = if (msg.isOutgoing) PhantomOnBubbleOutgoing else PhantomOnBubbleIncoming)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Audio Attachment", color = if (msg.isOutgoing) PhantomOnBubbleOutgoing else PhantomOnBubbleIncoming)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = "File", tint = if (msg.isOutgoing) PhantomOnBubbleOutgoing else PhantomOnBubbleIncoming)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("File Attachment", color = if (msg.isOutgoing) PhantomOnBubbleOutgoing else PhantomOnBubbleIncoming)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                if (msg.plaintext.isNotBlank()) {
                    Text(
                        text = msg.plaintext,
                        color = if (msg.isOutgoing) PhantomOnBubbleOutgoing else PhantomOnBubbleIncoming,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        color = (if (msg.isOutgoing) PhantomOnBubbleOutgoing else PhantomOnBubbleIncoming).copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    
                    if (msg.isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (msg.isDelivered) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = "Status",
                            tint = PhantomOnBubbleOutgoing.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun groupMessagesByDate(messages: List<MessageEntity>): Map<String, List<MessageEntity>> {
    val grouped = mutableMapOf<String, MutableList<MessageEntity>>()
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    
    for (msg in messages) {
        val cal = Calendar.getInstance().apply { timeInMillis = msg.timestamp }
        val header = when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
            else -> dateFormat.format(cal.time)
        }
        grouped.getOrPut(header) { mutableListOf() }.add(msg)
    }
    
    return grouped
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
