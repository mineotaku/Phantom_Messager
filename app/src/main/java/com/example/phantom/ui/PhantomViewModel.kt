package com.example.phantom.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.MessageEntity
import com.example.phantom.data.db.SessionEntity
import com.example.phantom.data.db.UserEntity
import com.example.phantom.data.network.ProfilePayload
import com.example.phantom.data.network.SupabaseManager
import com.example.phantom.data.repository.PhantomRepository

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhantomViewModel @Inject constructor(
    private val repository: PhantomRepository,
    private val supabaseManager: SupabaseManager
) : ViewModel() {

    val currentUser: StateFlow<UserEntity?> = repository.currentUserFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val serverEvents: StateFlow<String> = MutableStateFlow("").asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ProfilePayload>>(emptyList())
    val searchResults: StateFlow<List<ProfilePayload>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private var searchJob: Job? = null

    private val _activeChatContact = MutableStateFlow<FriendshipEntity?>(null)
    val activeChatContact: StateFlow<FriendshipEntity?> = _activeChatContact.asStateFlow()

    val currentFriends: StateFlow<List<FriendshipEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getAcceptedFriendsFlow(user.userId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentFriendships: StateFlow<List<FriendshipEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getFriendshipsFlow(user.userId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMessages: StateFlow<List<MessageEntity>> = activeChatContact.flatMapLatest { contact ->
        if (contact != null) repository.getMessagesForConversation(contact.friendUserId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionState = MutableStateFlow<SessionEntity?>(null)
    val activeSessionState: StateFlow<SessionEntity?> = _activeSessionState.asStateFlow()

    init {
        // Ensure user is registered on server (handles Render cold restarts)
        viewModelScope.launch {
            repository.ensureRegisteredOnServer()
        }

        // Listen for real-time incoming encrypted messages and friend requests via Supabase Realtime
        // with auto-restart on failure
        viewModelScope.launch {
            // Small delay to let registration finish first
            delay(2000)
            while (true) {
                try {
                    repository.observeRealtimeMessages()
                } catch (e: Exception) {
                    Log.e("PhantomViewModel", "Realtime message observation failed, restarting in 3s", e)
                }
                delay(3000) // Wait before restarting observation
            }
        }

        viewModelScope.launch {
            delay(2000)
            while (true) {
                try {
                    repository.processFriendRequestEvents()
                } catch (e: Exception) {
                    Log.e("PhantomViewModel", "Friend request observation failed, restarting in 3s", e)
                }
                delay(3000)
            }
        }

        viewModelScope.launch {
            delay(2000)
            while (true) {
                try {
                    repository.processFriendAcceptedEvents()
                } catch (e: Exception) {
                    Log.e("PhantomViewModel", "Friend accepted observation failed, restarting in 3s", e)
                }
                delay(3000)
            }
        }

        // Periodic polling fallback — ensures messages arrive even if Realtime is down
        viewModelScope.launch {
            // Initial delay to let registration/realtime setup finish
            delay(5000)
            while (true) {
                try {
                    repository.pollForNewMessages()
                    repository.fetchPendingFriendRequests() // Also poll for friend requests
                } catch (e: Exception) {
                    Log.w("PhantomViewModel", "Polling failed", e)
                }
                delay(5000) // Poll every 5 seconds
            }
        }
    }

    fun registerAccount(username: String, displayName: String, avatarStyle: String, bio: String) {
        viewModelScope.launch {
            repository.registerAccount(username, displayName, avatarStyle, bio)
        }
    }

    fun signInWithGoogle(idToken: String, onSetupRequired: () -> Unit) {
        viewModelScope.launch {
            try {
                val hasLocalUser = repository.signInWithGoogle(idToken)
                if (!hasLocalUser) {
                    onSetupRequired()
                }
                // If hasLocalUser is true, the repository just set the active user flag.
                // The Dao will emit the new active user to currentUserFlow, 
                // which will automatically navigate the UI away from AuthScreen!
            } catch (e: Exception) {
                Log.e("PhantomViewModel", "Google Sign In failed in ViewModel", e)
            }
        }
    }

    fun switchActiveUser(userId: String) {
        viewModelScope.launch {
            repository.switchActiveUser(userId)
            _activeChatContact.value = null
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _activeChatContact.value = null
            _activeSessionState.value = null
            repository.signOut()
        }
    }

    fun clearActiveChat() {
        _activeChatContact.value = null
        _activeSessionState.value = null
    }

    fun searchUsers(query: String) {
        _searchQuery.value = query

        // Clear results immediately if query is too short
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            _searchError.value = null
            _isSearching.value = false
            searchJob?.cancel()
            return
        }

        // Cancel any in-flight search and debounce 400ms
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            delay(400L) // Debounce: wait for user to stop typing
            try {
                val results = repository.searchUsers(query.trim())
                _searchResults.value = results
                _searchError.value = null
            } catch (e: Exception) {
                Log.e("PhantomViewModel", "Search failed for query '$query'", e)
                _searchResults.value = emptyList()
                _searchError.value = "Could not reach server. Please try again."
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun sendFriendRequest(friendUserId: String) {
        viewModelScope.launch {
            repository.sendFriendRequest(friendUserId)
        }
    }

    fun acceptFriendRequest(friendUserId: String) {
        viewModelScope.launch {
            repository.acceptFriendRequest(friendUserId)
        }
    }

    fun blockUser(friendUserId: String) {
        viewModelScope.launch {
            repository.blockUser(friendUserId)
        }
    }

    fun openChatWith(friendship: FriendshipEntity) {
        _activeChatContact.value = friendship
        viewModelScope.launch {
            val session = repository.getOrCreateSession(friendship.friendUserId)
            _activeSessionState.value = session
        }
    }

    fun sendMessage(text: String) {
        val contact = activeChatContact.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(contact.friendUserId, text)
            // Refresh active session state for UI inspection
            val session = repository.getOrCreateSession(contact.friendUserId)
            _activeSessionState.value = session
        }
    }

    fun sendMediaMessage(text: String, fileUri: android.net.Uri, mimeType: String?, context: android.content.Context) {
        val contact = activeChatContact.value ?: return
        viewModelScope.launch {
            try {
                val mediaType = when {
                    mimeType?.startsWith("image/") == true -> "IMAGE"
                    mimeType?.startsWith("video/") == true -> "VIDEO"
                    mimeType?.startsWith("audio/") == true -> "AUDIO"
                    else -> "FILE"
                }
                
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null) {
                    val fullUrl = supabaseManager.uploadMedia(bytes, mimeType ?: "application/octet-stream")
                    
                    if (fullUrl != null) {
                        repository.sendMessage(contact.friendUserId, text, fullUrl, mediaType)
                    } else {
                        // Handle error or send just text
                        repository.sendMessage(contact.friendUserId, text)
                    }

                    val session = repository.getOrCreateSession(contact.friendUserId)
                    _activeSessionState.value = session
                }
            } catch (e: Exception) {
                Log.e("PhantomViewModel", "Failed to upload and send media", e)
            }
        }
    }

    fun toggleKeyVerified(friendUserId: String, isVerified: Boolean) {
        viewModelScope.launch {
            repository.toggleKeyVerified(friendUserId, isVerified)
            // Update current active chat contact if matching
            val contact = _activeChatContact.value
            if (contact?.friendUserId == friendUserId) {
                _activeChatContact.value = contact.copy(isVerifiedKey = isVerified)
            }
        }
    }
}
