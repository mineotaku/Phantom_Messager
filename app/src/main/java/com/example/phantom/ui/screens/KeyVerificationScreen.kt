package com.example.phantom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phantom.crypto.CryptoUtils
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.UserEntity
import com.example.ui.theme.PhantomBackground
import com.example.ui.theme.PhantomOutline
import com.example.ui.theme.PhantomPrimary
import com.example.ui.theme.PhantomSecondary
import com.example.ui.theme.PhantomSurface
import com.example.ui.theme.PhantomSurfaceVariant
import com.example.ui.theme.PhantomTertiary

@Composable
fun KeyVerificationScreen(
    currentUser: UserEntity?,
    contact: FriendshipEntity,
    onBack: () -> Unit,
    onToggleVerified: (Boolean) -> Unit
) {
    val rawSafetyNumber = rememberSafetyNumber(currentUser?.identityPublicKeyHex, contact.friendUserId)
    // Split 48-digit string into 12 blocks of 4 digits
    val digitBlocks = rawSafetyNumber.chunked(4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Header Bar
        Surface(
            color = PhantomSurface,
            tonalElevation = 2.dp,
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
                    modifier = Modifier.testTag("verification_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verify Security Code",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PhantomTertiary.copy(alpha = 0.15f))
                    .border(1.dp, PhantomTertiary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = PhantomTertiary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Security code with ${contact.friendDisplayName}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Compare these numbers with your contact to confirm your conversation is secure.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Safety Number 12-Block Grid Card (Signal Style)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PhantomSurface,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PhantomOutline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Security Code",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PhantomPrimary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2-Column 6-Row Grid of 4-digit blocks
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (row in 0 until 6) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val block1 = digitBlocks.getOrNull(row * 2) ?: "0000"
                                val block2 = digitBlocks.getOrNull(row * 2 + 1) ?: "0000"

                                SafetyBlockItem(block1, Modifier.weight(1f))
                                SafetyBlockItem(block2, Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // QR Matrix Visual Representation
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PhantomSurfaceVariant)
                            .border(1.dp, PhantomOutline, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "QR Code Matrix",
                            tint = PhantomSecondary,
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Verification Toggle Switch Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PhantomSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PhantomOutline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Mark as Verified",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (contact.isVerifiedKey) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = PhantomTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "Confirm that you have verified this contact's security code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = contact.isVerifiedKey,
                        onCheckedChange = { onToggleVerified(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PhantomTertiary,
                            checkedTrackColor = PhantomTertiary.copy(alpha = 0.3f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = PhantomSurfaceVariant
                        ),
                        modifier = Modifier.testTag("verify_key_switch")
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyBlockItem(digits: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(PhantomSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(0.5.dp, PhantomOutline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digits,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun rememberSafetyNumber(myIdentityKeyHex: String?, contactUserId: String): String {
    val myKey = myIdentityKeyHex ?: "000000000000000000000000"
    return CryptoUtils.generateSafetyNumber(myKey, contactUserId)
}
