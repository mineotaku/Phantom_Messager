package com.example.phantom.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.MessageEntity
import com.example.phantom.data.db.PhantomDatabase
import com.example.phantom.data.db.SessionEntity
import com.example.phantom.data.db.UserEntity
import com.example.phantom.data.network.ProfilePayload
import com.example.phantom.data.repository.PhantomRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhantomViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhantomRepository(PhantomDatabase.getDatabase(application))

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

        // Fetch any pending friend requests from server (offline sync)
        viewModelScope.launch {
            repository.fetchPendingFriendRequests()
        }

        // Listen for real-time incoming friend requests via WebSocket
        viewModelScope.launch {
            repository.processFriendRequestEvents()
        }

        // Listen for real-time friend request acceptances via WebSocket
        viewModelScope.launch {
            repository.processFriendAcceptedEvents()
        }

        // Listen for real-time incoming encrypted messages via WebSocket
        viewModelScope.launch {
            repository.pollAndDecryptIncomingMessages()
        }
    }

    fun registerAccount(username: String, displayName: String, avatarStyle: String, bio: String) {
        viewModelScope.launch {
            repository.registerAccount(username, displayName, avatarStyle, bio)
        }
    }

    fun switchActiveUser(userId: String) {
        viewModelScope.launch {
            repository.switchActiveUser(userId)
            _activeChatContact.value = null
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
                    val mediaTypeParsed = (mimeType ?: "application/octet-stream").toMediaTypeOrNull()
                    val requestFile = bytes.toRequestBody(mediaTypeParsed)
                    val body = okhttp3.MultipartBody.Part.createFormData("file", "upload_${System.currentTimeMillis()}", requestFile)

                    val response = com.example.phantom.data.network.RetrofitClient.api.uploadMedia(body)
                    val fullUrl = "https://phantom-relay-jvm2.onrender.com" + response.url
                    
                    repository.sendMessage(contact.friendUserId, text, fullUrl, mediaType)

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
