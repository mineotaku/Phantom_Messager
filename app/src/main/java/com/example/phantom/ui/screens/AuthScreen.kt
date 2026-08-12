package com.example.phantom.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

enum class AuthMode { PHONE_AUTH, OTP_VERIFY, SETUP, RESTORE }

@Composable
fun AuthScreen(
    onRegister: (username: String, displayName: String, avatar: String, bio: String) -> Unit,
    onRestore: (username: String, recoveryKey: String) -> Unit = { _, _ -> }
) {
    var mode by remember { mutableStateOf(AuthMode.PHONE_AUTH) }
    
    // Auth State
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    
    // Setup State
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf("") }

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            auth.signInWithCredential(credential).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    mode = AuthMode.SETUP
                } else {
                    Log.e("Auth", "SignIn Failed", task.exception)
                }
            }
        }
        override fun onVerificationFailed(e: FirebaseException) {
            Log.e("Auth", "Verification Failed", e)
        }
        override fun onCodeSent(verId: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = verId
            mode = AuthMode.OTP_VERIFY
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PhantomBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Minimalist Logo
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(PhantomPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = PhantomPrimary, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Phantom", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp), color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Private by design.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(48.dp))

            // Mode Selector (Only show before phone auth is complete or if user wants to skip to restore)
            if (mode == AuthMode.PHONE_AUTH || mode == AuthMode.RESTORE) {
                Surface(
                    color = PhantomSurfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mode == AuthMode.PHONE_AUTH) PhantomSurface else Color.Transparent)
                                .clickable { mode = AuthMode.PHONE_AUTH }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("New Account", fontWeight = if (mode == AuthMode.PHONE_AUTH) FontWeight.SemiBold else FontWeight.Normal, color = if (mode == AuthMode.PHONE_AUTH) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mode == AuthMode.RESTORE) PhantomSurface else Color.Transparent)
                                .clickable { mode = AuthMode.RESTORE }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Restore", fontWeight = if (mode == AuthMode.RESTORE) FontWeight.SemiBold else FontWeight.Normal, color = if (mode == AuthMode.RESTORE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            AnimatedContent(targetState = mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "AuthModeTransition") { currentMode ->
                Column {
                    when (currentMode) {
                        AuthMode.PHONE_AUTH -> {
                            OutlinedTextField(
                                value = phoneNumber, onValueChange = { phoneNumber = it }, placeholder = { Text("Phone Number (+1234...)") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PhantomPrimary, unfocusedBorderColor = PhantomOutline, focusedContainerColor = PhantomSurface, unfocusedContainerColor = PhantomSurface),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    if (phoneNumber.isNotBlank()) {
                                        val options = PhoneAuthOptions.newBuilder(auth)
                                            .setPhoneNumber(phoneNumber)
                                            .setTimeout(60L, TimeUnit.SECONDS)
                                            .setActivity(context as Activity)
                                            .setCallbacks(callbacks)
                                            .build()
                                        PhoneAuthProvider.verifyPhoneNumber(options)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary)
                            ) { Text("Send SMS", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        AuthMode.OTP_VERIFY -> {
                            OutlinedTextField(
                                value = otpCode, onValueChange = { otpCode = it }, placeholder = { Text("6-Digit OTP") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PhantomPrimary, unfocusedBorderColor = PhantomOutline, focusedContainerColor = PhantomSurface, unfocusedContainerColor = PhantomSurface),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    if (otpCode.isNotBlank() && verificationId.isNotBlank()) {
                                        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                                        auth.signInWithCredential(credential).addOnCompleteListener { task ->
                                            if (task.isSuccessful) mode = AuthMode.SETUP
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary)
                            ) { Text("Verify Code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        AuthMode.SETUP -> {
                            OutlinedTextField(
                                value = username, onValueChange = { username = it.lowercase().trim() }, placeholder = { Text("Username") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PhantomSurface, unfocusedContainerColor = PhantomSurface), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = displayName, onValueChange = { displayName = it }, placeholder = { Text("Display Name") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PhantomSurface, unfocusedContainerColor = PhantomSurface), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { if (username.isNotBlank() && displayName.isNotBlank()) onRegister(username, displayName, "avatar_cyber", "") },
                                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary)
                            ) { Text("Complete Setup", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        AuthMode.RESTORE -> {
                            OutlinedTextField(
                                value = username, onValueChange = { username = it.lowercase().trim() }, placeholder = { Text("Username") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PhantomSurface, unfocusedContainerColor = PhantomSurface), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = recoveryKey, onValueChange = { recoveryKey = it.uppercase() }, placeholder = { Text("16-Character Recovery Key") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = PhantomSurface, unfocusedContainerColor = PhantomSurface), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { if (username.isNotBlank() && recoveryKey.isNotBlank()) onRestore(username, recoveryKey) },
                                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary)
                            ) { Text("Restore Identity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}
