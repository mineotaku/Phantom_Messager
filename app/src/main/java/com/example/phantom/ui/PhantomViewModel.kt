package com.example.phantom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.MessageEntity
import com.example.phantom.data.db.PhantomDatabase
import com.example.phantom.data.db.SessionEntity
import com.example.phantom.data.db.UserEntity
import com.example.phantom.data.relay.RelayServer
import com.example.phantom.data.repository.PhantomRepository
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

    val serverEvents: StateFlow<String> = RelayServer.serverEvents

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<RelayServer.PublicUserProfile>>(emptyList())
    val searchResults: StateFlow<List<RelayServer.PublicUserProfile>> = _searchResults.asStateFlow()

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
        // Periodically poll for incoming messages
        viewModelScope.launch {
            while (true) {
                repository.pollAndDecryptIncomingMessages()
                kotlinx.coroutines.delay(2000)
            }
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

    fun searchUsers(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            val results = repository.searchUsers(query)
            _searchResults.value = results
        }
    }

    fun sendFriendRequest(profile: RelayServer.PublicUserProfile) {
        viewModelScope.launch {
            repository.sendFriendRequest(profile)
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
