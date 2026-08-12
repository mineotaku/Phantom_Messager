package com.example.phantom.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.phantom.data.db.SessionEntity
import com.example.ui.theme.PhantomOutline
import com.example.ui.theme.PhantomPrimary
import com.example.ui.theme.PhantomPrimaryVariant
import com.example.ui.theme.PhantomSecondary
import com.example.ui.theme.PhantomSurface
import com.example.ui.theme.PhantomSurfaceVariant
import com.example.ui.theme.PhantomTertiary

@Composable
fun RatchetInspectorDialog(
    session: SessionEntity?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            color = PhantomSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PhantomOutline.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = PhantomSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Double Ratchet Inspector",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Live KDF Symmetric & DH Ratchet States",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_ratchet_inspector")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (session == null) {
                    Text(text = "No active Double Ratchet session initialized.", color = MaterialTheme.colorScheme.error)
                } else {
                    CryptoFieldItem("X3DH Shared Secret (SK)", session.sharedMasterSecretHex, PhantomTertiary)
                    CryptoFieldItem("Active Root Key (RK)", session.rootKeyHex, PhantomPrimaryVariant)
                    CryptoFieldItem("Sending Chain Key (CKs)", session.sendingChainKeyHex ?: "Unset", PhantomSecondary)
                    CryptoFieldItem("Receiving Chain Key (CKr)", session.receivingChainKeyHex ?: "Unset", PhantomSecondary)
                    CryptoFieldItem("Local Ephemeral DH PubKey", session.localDhPublicKeyHex, MaterialTheme.colorScheme.onSurface)
                    CryptoFieldItem("Remote Ephemeral DH PubKey", session.remoteDhPublicKeyHex ?: "Pending initial ratchet step", MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sequence Counters Card
                    Surface(
                        color = PhantomSurfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, PhantomOutline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "MESSAGE SEQUENCE COUNTERS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SequenceCounterBadge("Sent (Ns)", session.sendSequenceNumber.toString(), PhantomPrimary)
                                SequenceCounterBadge("Received (Nr)", session.receiveSequenceNumber.toString(), PhantomTertiary)
                                SequenceCounterBadge("Prev Chain (PN)", session.previousChainLength.toString(), PhantomSecondary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary)
                ) {
                    Text("Close Inspector", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CryptoFieldItem(label: String, valueHex: String, valueColor: androidx.compose.ui.graphics.Color) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(PhantomSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .border(0.5.dp, PhantomOutline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable {
                clipboardManager.setText(AnnotatedString(valueHex))
                Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
            }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (valueHex.length > 36) valueHex.take(18) + "..." + valueHex.takeLast(18) else valueHex,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SequenceCounterBadge(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
