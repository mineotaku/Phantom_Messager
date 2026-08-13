package com.example.phantom.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, SETUP, RESTORE }

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AuthScreen(
    onRegister: (username: String, displayName: String, avatar: String, bio: String) -> Unit,
    onRestore: (username: String, recoveryKey: String) -> Unit = { _, _ -> }
) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Auth State
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    // Setup State
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf("") }

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    // Web Client ID from your google-services.json
    val webClientId = "225173369185-kcvgin56ap0ah1uqhl3pa5nei4ru87n4.apps.googleusercontent.com"

    fun handleGoogleSignIn() {
        val activity = context.findActivity() ?: return
        isLoading = true
        coroutineScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .build()
                    
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                    
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    
                    auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) {
                            mode = AuthMode.SETUP
                        } else {
                            Toast.makeText(context, "Sign in failed. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    isLoading = false
                    Toast.makeText(context, "Sign in failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isLoading = false
                Log.e("AuthScreen", "Google Sign In Failed", e)
                Toast.makeText(context, "Google sign in was cancelled or failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PhantomBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 32.dp, start = 32.dp, end = 32.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PhantomPrimary, PhantomSecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = PhantomOnBackground, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Phantom", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp), color = PhantomOnBackground)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Private by design.", style = MaterialTheme.typography.bodyMedium, color = PhantomOnSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))

            if (mode == AuthMode.LOGIN || mode == AuthMode.RESTORE) {
                Surface(
                    color = PhantomSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mode == AuthMode.LOGIN) PhantomPrimary else Color.Transparent)
                                .clickable { mode = AuthMode.LOGIN }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("New Account", fontWeight = if (mode == AuthMode.LOGIN) FontWeight.SemiBold else FontWeight.Normal, color = if (mode == AuthMode.LOGIN) PhantomOnBackground else PhantomTextMuted)
                        }
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mode == AuthMode.RESTORE) PhantomPrimary else Color.Transparent)
                                .clickable { mode = AuthMode.RESTORE }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Restore", fontWeight = if (mode == AuthMode.RESTORE) FontWeight.SemiBold else FontWeight.Normal, color = if (mode == AuthMode.RESTORE) PhantomOnBackground else PhantomTextMuted)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            AnimatedContent(
                targetState = mode, 
                transitionSpec = { fadeIn() togetherWith fadeOut() }, 
                label = "AuthModeTransition",
                modifier = Modifier.weight(1f)
            ) { currentMode ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (currentMode) {
                        AuthMode.LOGIN -> {
                            OutlinedTextField(
                                value = email, onValueChange = { email = it }, placeholder = { Text("Email Address") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PhantomTextMuted) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomPrimary, unfocusedBorderColor = Color.Transparent, 
                                    focusedContainerColor = PhantomSurfaceVariant, unfocusedContainerColor = PhantomSurfaceVariant
                                ),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = password, onValueChange = { password = it }, placeholder = { Text("Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PhantomTextMuted) },
                                trailingIcon = {
                                    val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = icon, contentDescription = "Toggle password visibility", tint = PhantomTextMuted)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomPrimary, unfocusedBorderColor = Color.Transparent, 
                                    focusedContainerColor = PhantomSurfaceVariant, unfocusedContainerColor = PhantomSurfaceVariant
                                ),
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                        isLoading = true
                                        auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                isLoading = false
                                                mode = AuthMode.SETUP
                                            } else {
                                                auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { createTask ->
                                                    isLoading = false
                                                    if (createTask.isSuccessful) {
                                                        mode = AuthMode.SETUP
                                                    } else {
                                                        Toast.makeText(context, "Could not sign in. Please check your email and password.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary)
                            ) { 
                                if (isLoading) {
                                    CircularProgressIndicator(color = PhantomOnBackground, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Continue with Email", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PhantomOnBackground) 
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = PhantomDivider)
                                Text(" OR ", color = PhantomTextMuted, modifier = Modifier.padding(horizontal = 16.dp))
                                HorizontalDivider(modifier = Modifier.weight(1f), color = PhantomDivider)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            OutlinedButton(
                                onClick = { handleGoogleSignIn() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PhantomOnBackground),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PhantomOutline)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = PhantomOnBackground, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Continue with Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        AuthMode.SETUP -> {
                            OutlinedTextField(
                                value = username, onValueChange = { username = it.lowercase().trim() }, placeholder = { Text("Username") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PhantomTextMuted) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), 
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomPrimary, unfocusedBorderColor = Color.Transparent, 
                                    focusedContainerColor = PhantomSurfaceVariant, unfocusedContainerColor = PhantomSurfaceVariant
                                ), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = displayName, onValueChange = { displayName = it }, placeholder = { Text("Display Name") },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = PhantomTextMuted) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), 
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomPrimary, unfocusedBorderColor = Color.Transparent, 
                                    focusedContainerColor = PhantomSurfaceVariant, unfocusedContainerColor = PhantomSurfaceVariant
                                ), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { if (username.isNotBlank() && displayName.isNotBlank()) onRegister(username, displayName, "avatar_cyber", "") },
                                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary)
                            ) { Text("Complete Setup", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PhantomOnBackground) }
                        }
                        AuthMode.RESTORE -> {
                            OutlinedTextField(
                                value = username, onValueChange = { username = it.lowercase().trim() }, placeholder = { Text("Username") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PhantomTextMuted) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), 
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomPrimary, unfocusedBorderColor = Color.Transparent, 
                                    focusedContainerColor = PhantomSurfaceVariant, unfocusedContainerColor = PhantomSurfaceVariant
                                ), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = recoveryKey, onValueChange = { recoveryKey = it.uppercase() }, placeholder = { Text("Recovery Phrase") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = PhantomTextMuted) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), 
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomPrimary, unfocusedBorderColor = Color.Transparent, 
                                    focusedContainerColor = PhantomSurfaceVariant, unfocusedContainerColor = PhantomSurfaceVariant
                                ), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { if (username.isNotBlank() && recoveryKey.isNotBlank()) onRestore(username, recoveryKey) },
                                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary)
                            ) { Text("Restore Account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PhantomOnBackground) }
                        }
                    }
                }
            }
            
            Text("By continuing, you agree to Phantom's Terms of Service", color = PhantomTextMuted, fontSize = 11.sp)
        }
    }
}
