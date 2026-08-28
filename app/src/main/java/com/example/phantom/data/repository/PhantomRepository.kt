package com.example.phantom.data.repository

import android.util.Log
import com.example.phantom.crypto.CryptoUtils
import com.example.phantom.crypto.DoubleRatchet
import com.example.phantom.crypto.X3DH
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.MessageEntity
import com.example.phantom.data.db.PhantomDatabase
import com.example.phantom.data.db.PrekeyEntity
import com.example.phantom.data.db.SessionEntity
import com.example.phantom.data.db.UserEntity
import com.example.phantom.data.network.SupabaseManager
import com.example.phantom.data.network.ProfilePayload
import com.example.phantom.data.network.RegisterPayload
import com.example.phantom.data.network.FriendRequestPayload
import com.example.phantom.data.network.FriendRequestAcceptPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhantomRepository @Inject constructor(
    private val db: PhantomDatabase,
    private val supabaseManager: SupabaseManager
) {

    private val isObservingMessages = AtomicBoolean(false)
    private val isObservingRequests = AtomicBoolean(false)
    private val isObservingAccepts = AtomicBoolean(false)

    val currentUserFlow: Flow<UserEntity?> = flow {
        emitAll(db.userDao().getCurrentUserFlow())
    }.flowOn(Dispatchers.IO)

    suspend fun getCurrentUser(): UserEntity? = withContext(Dispatchers.IO) {
        db.userDao().getCurrentUser()
    }

    /**
     * Registers a new local user account and provisions E2EE cryptographic identity.
     */
    suspend fun registerAccount(
        username: String,
        displayName: String,
        avatarStyle: String,
        bio: String
    ): UserEntity = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().lowercase().removePrefix("@")
        val userId = "u_$cleanUsername"

        // 1. Generate Identity KeyPair
        val identityKeyPair = CryptoUtils.generateKeyPair()
        val identityPubHex = CryptoUtils.toHex(identityKeyPair.public.encoded)
        val identityPrivHex = CryptoUtils.toHex(identityKeyPair.private.encoded)

        // 2. Generate Signed Prekey
        val signedPrekeyPair = CryptoUtils.generateKeyPair()
        val signedPrekeyPubHex = CryptoUtils.toHex(signedPrekeyPair.public.encoded)
        val signedPrekeyPrivHex = CryptoUtils.toHex(signedPrekeyPair.private.encoded)
        val signature = CryptoUtils.signData(identityKeyPair.private, signedPrekeyPair.public.encoded)
        val sigHex = CryptoUtils.toHex(signature)

        // 3. Generate 10 One-Time Prekeys (OPKs)
        val opkEntities = mutableListOf<PrekeyEntity>()
        val opkPublicHexes = mutableListOf<String>()

        for (i in 1..10) {
            val opkPair = CryptoUtils.generateKeyPair()
            val opkPubHex = CryptoUtils.toHex(opkPair.public.encoded)
            val opkPrivHex = CryptoUtils.toHex(opkPair.private.encoded)
            val opkId = "opk_${userId}_$i"

            opkEntities.add(
                PrekeyEntity(
                    prekeyId = opkId,
                    userId = userId,
                    publicKeyHex = opkPubHex,
                    privateKeyHex = opkPrivHex
                )
            )
            opkPublicHexes.add(opkPubHex)
        }

        // 4. Recovery Key
        val recoveryKey = CryptoUtils.generateRecoveryKey()

        val userEntity = UserEntity(
            userId = userId,
            username = cleanUsername,
            displayName = displayName,
            avatarStyle = avatarStyle,
            bio = bio,
            identityPublicKeyHex = identityPubHex,
            identityPrivateKeyHex = identityPrivHex,
            signedPrekeyPublicHex = signedPrekeyPubHex,
            signedPrekeyPrivateHex = signedPrekeyPrivHex,
            signedPrekeySignatureHex = sigHex,
            recoveryKey = recoveryKey,
            isCurrentLocalUser = true
        )

        db.userDao().clearActiveUserFlag()
        db.userDao().insertUser(userEntity)
        db.prekeyDao().insertPrekeys(opkEntities)

        // Register on server with retry
        registerOnServer(userEntity, opkPublicHexes)

        userEntity
    }

    /**
     * Registers or re-registers the user profile on the relay server.
     */
    private suspend fun registerOnServer(user: UserEntity, opkPublicHexes: List<String>? = null) {
        val profilePayload = ProfilePayload(
            userId = user.userId,
            username = user.username,
            displayName = user.displayName,
            avatarStyle = user.avatarStyle,
            bio = user.bio,
            identityPublicKeyHex = user.identityPublicKeyHex
        )

        // Gather OPK hex values: use provided list or regenerate from stored prekeys
        val opkHexes = opkPublicHexes ?: run {
            // Fallback: send empty list for re-registration (server will keep existing if any)
            emptyList()
        }

        val registerPayload = RegisterPayload(
            profile = profilePayload,
            signedPrekeyPublicHex = user.signedPrekeyPublicHex,
            signedPrekeySignatureHex = user.signedPrekeySignatureHex,
            oneTimePrekeysHex = opkHexes
        )

        // Retry up to 3 times
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                supabaseManager.registerUser(registerPayload)
                Log.d("PhantomRepository", "Registered on server (attempt $attempt)")
                return
            } catch (e: Exception) {
                lastError = e
                Log.e("PhantomRepository", "Registration attempt $attempt failed", e)
                if (attempt < 3) {
                    kotlinx.coroutines.delay(1000L * attempt) // Backoff
                }
            }
        }
        Log.e("PhantomRepository", "All registration attempts failed", lastError)
    }

    /**
     * Ensures the current user is registered on the server.
     * Call on app startup to handle server cold restarts that wipe in-memory data.
     */
    suspend fun ensureRegisteredOnServer() = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        // Check if server knows about us
        val profile = supabaseManager.getProfile(currentUser.userId)
        if (profile == null) {
            // Server doesn't know us — re-register
            Log.w("PhantomRepository", "User not found on server, re-registering...")
            registerOnServer(currentUser)
        } else {
            Log.d("PhantomRepository", "User already registered on server")
        }
    }

    /**
     * Fetches and processes any pending friend requests from the server.
     * Called on app startup to sync requests that arrived while offline.
     */
    suspend fun fetchPendingFriendRequests() = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        try {
            val pendingRequests = supabaseManager.getFriendRequests(currentUser.userId)
            for (request in pendingRequests) {
                val existing = db.friendshipDao().getFriendship(currentUser.userId, request.fromUserId)
                if (existing == null) {
                    val friendEntity = FriendshipEntity(
                        localUserId = currentUser.userId,
                        friendUserId = request.fromUserId,
                        friendUsername = request.fromUsername ?: "Unknown",
                        friendDisplayName = request.fromDisplayName ?: request.fromUsername ?: "Unknown",
                        friendAvatarStyle = request.fromAvatarStyle ?: "",
                        status = "PENDING_RECEIVED"
                    )
                    db.friendshipDao().insertFriendship(friendEntity)
                    Log.d("PhantomRepository", "Synced pending request from ${request.fromUserId}")
                }
            }
        } catch (e: Exception) {
            Log.e("PhantomRepository", "Failed to fetch pending friend requests", e)
        }
    }

    suspend fun switchActiveUser(targetUserId: String) = withContext(Dispatchers.IO) {
        db.userDao().clearActiveUserFlag()
        db.userDao().setActiveUser(targetUserId)
    }

    fun getFriendshipsFlow(localUserId: String): Flow<List<FriendshipEntity>> = flow {
        emitAll(db.friendshipDao().getFriendshipsFlow(localUserId))
    }.flowOn(Dispatchers.IO)

    fun getAcceptedFriendsFlow(localUserId: String): Flow<List<FriendshipEntity>> = flow {
        emitAll(db.friendshipDao().getAcceptedFriendsFlow(localUserId))
    }.flowOn(Dispatchers.IO)

    suspend fun searchUsers(query: String): List<ProfilePayload> = withContext(Dispatchers.IO) {
        supabaseManager.searchProfiles(query)
    }

    /**
     * Sends a friend request both locally AND to the server for relay to the recipient.
     */
    suspend fun sendFriendRequest(friendUserId: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        val profile = supabaseManager.getProfile(friendUserId) ?: return@withContext
            
        val friendEntity = FriendshipEntity(
            localUserId = currentUser.userId,
            friendUserId = friendUserId,
            friendUsername = profile.username ?: "Unknown",
            friendDisplayName = profile.displayName ?: profile.username ?: "Unknown",
            friendAvatarStyle = profile.avatarStyle ?: "",
            status = "PENDING_SENT"
        )
        db.friendshipDao().insertFriendship(friendEntity)

        // Notify the server so the recipient gets the request
        try {
            supabaseManager.sendFriendRequest(
                FriendRequestPayload(
                    fromUserId = currentUser.userId,
                    toUserId = friendUserId
                )
            )
            Log.d("PhantomRepository", "Friend request sent to server for $friendUserId")
        } catch (e: Exception) {
            Log.e("PhantomRepository", "Failed to send friend request to server", e)
        }
    }

    /**
     * Accepts a friend request both locally AND notifies the server.
     */
    suspend fun acceptFriendRequest(friendUserId: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        db.friendshipDao().updateStatus(currentUser.userId, friendUserId, "ACCEPTED")

        // Notify the server so the sender gets updated
        try {
            supabaseManager.acceptFriendRequest(
                FriendRequestAcceptPayload(
                    userId = currentUser.userId,
                    friendUserId = friendUserId
                )
            )
            Log.d("PhantomRepository", "Friend request acceptance sent to server for $friendUserId")
        } catch (e: Exception) {
            Log.e("PhantomRepository", "Failed to notify server of friend acceptance", e)
        }
    }

    suspend fun blockUser(friendUserId: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        db.friendshipDao().updateStatus(currentUser.userId, friendUserId, "BLOCKED")
    }

    suspend fun toggleKeyVerified(friendUserId: String, isVerified: Boolean) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        db.friendshipDao().setKeyVerified(currentUser.userId, friendUserId, isVerified)
    }

    /**
     * Processes incoming friend request WebSocket events in real-time.
     * Creates PENDING_RECEIVED friendship entries in local DB.
     */
    suspend fun processFriendRequestEvents(): Unit = withContext(Dispatchers.IO) {
        if (!isObservingRequests.compareAndSet(false, true)) return@withContext
        try {
            val currentUser = getCurrentUser() ?: return@withContext
            supabaseManager.observeFriendRequests(currentUser.userId).collect { event ->
                val activeUser = getCurrentUser() ?: return@collect
                val existing = db.friendshipDao().getFriendship(activeUser.userId, event.fromUserId)
                if (existing == null) {
                    val friendEntity = FriendshipEntity(
                        localUserId = activeUser.userId,
                        friendUserId = event.fromUserId,
                        friendUsername = event.fromUsername ?: "Unknown",
                        friendDisplayName = event.fromDisplayName ?: event.fromUsername ?: "Unknown",
                        friendAvatarStyle = event.fromAvatarStyle ?: "",
                        status = "PENDING_RECEIVED"
                    )
                    db.friendshipDao().insertFriendship(friendEntity)
                    Log.d("PhantomRepository", "Received friend request from ${event.fromUserId}")
                }
            }
        } catch (e: Exception) {
            Log.e("PhantomRepository", "Error observing friend requests", e)
        } finally {
            isObservingRequests.set(false)
        }
    }

    /**
     * Processes incoming friend acceptance WebSocket events in real-time.
     * Updates PENDING_SENT entries to ACCEPTED.
     */
    suspend fun processFriendAcceptedEvents(): Unit = withContext(Dispatchers.IO) {
        if (!isObservingAccepts.compareAndSet(false, true)) return@withContext
        try {
            val currentUser = getCurrentUser() ?: return@withContext
            supabaseManager.observeFriendAccepted(currentUser.userId).collect { event ->
                val activeUser = getCurrentUser() ?: return@collect
                val existing = db.friendshipDao().getFriendship(activeUser.userId, event.userId)
                if (existing != null && existing.status == "PENDING_SENT") {
                    db.friendshipDao().updateStatus(activeUser.userId, event.userId, "ACCEPTED")
                    Log.d("PhantomRepository", "Friend request accepted by ${event.userId}")
                } else if (existing == null) {
                    // Edge case: create an accepted friendship if we didn't have one
                    val friendEntity = FriendshipEntity(
                        localUserId = activeUser.userId,
                        friendUserId = event.userId,
                        friendUsername = event.acceptedByUsername ?: "Unknown",
                        friendDisplayName = event.acceptedByDisplayName ?: event.acceptedByUsername ?: "Unknown",
                        friendAvatarStyle = event.acceptedByAvatarStyle ?: "",
                        status = "ACCEPTED"
                    )
                    db.friendshipDao().insertFriendship(friendEntity)
                }
            }
        } catch (e: Exception) {
            Log.e("PhantomRepository", "Error observing friend accepts", e)
        } finally {
            isObservingAccepts.set(false)
        }
    }

    /**
     * Establishes or retrieves the E2EE Double Ratchet session for a contact.
     */
    suspend fun getOrCreateSession(contactUserId: String): SessionEntity = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: throw IllegalStateException("No active user logged in!")
        val existingSession = db.sessionDao().getSession(currentUser.userId, contactUserId)
        if (existingSession != null) return@withContext existingSession

        val bundleResponse = supabaseManager.getPrekeyBundle(contactUserId)
            ?: throw IllegalStateException("Failed to fetch prekey bundle for \$contactUserId")
        val prekeyBundle = com.example.phantom.crypto.X3DH.PrekeyBundle(
            recipientUserId = contactUserId,
            identityKeyHex = bundleResponse.identityPublicKeyHex,
            signedPrekeyHex = bundleResponse.signedPrekeyPublicHex,
            signedPrekeySignatureHex = bundleResponse.signedPrekeySignatureHex,
            oneTimePrekeyHex = bundleResponse.oneTimePrekeyHex
        )

        val myIdentityKey = Pair(
            CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(currentUser.identityPrivateKeyHex)),
            CryptoUtils.parsePublicKey(CryptoUtils.fromHex(currentUser.identityPublicKeyHex))
        )

        val x3dhResult = X3DH.initiateHandshake(myIdentityKey, prekeyBundle)

        // Initialize Alice Double Ratchet Session
        val drState = DoubleRatchet.initializeAliceSession(
            sharedMasterSecretHex = x3dhResult.sharedMasterSecretHex,
            bobDhPublicKeyHex = prekeyBundle.signedPrekeyHex
        )

        val sessionEntity = SessionEntity(
            contactUserId = contactUserId,
            localUserId = currentUser.userId,
            rootKeyHex = drState.rootKeyHex,
            localDhPrivateKeyHex = drState.localDhPrivateKeyHex,
            localDhPublicKeyHex = drState.localDhPublicKeyHex,
            remoteDhPublicKeyHex = drState.remoteDhPublicKeyHex,
            sendingChainKeyHex = drState.sendingChainKeyHex,
            receivingChainKeyHex = drState.receivingChainKeyHex,
            sendSequenceNumber = drState.sendSequenceNumber,
            receiveSequenceNumber = drState.receiveSequenceNumber,
            previousChainLength = drState.previousChainLength,
            sharedMasterSecretHex = x3dhResult.sharedMasterSecretHex,
            aliceBaseKeyHex = x3dhResult.senderEphemeralPublicKeyHex,
            oneTimePrekeyUsedHex = x3dhResult.oneTimePrekeyUsedHex
        )

        db.sessionDao().saveSession(sessionEntity)
        Log.d("PhantomRepository", "Created new session with ${contactUserId}, OPK used: ${x3dhResult.oneTimePrekeyUsedHex != null}")
        sessionEntity
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSessionFlow(contactUserId: String): Flow<SessionEntity?> {
        return currentUserFlow.flatMapLatest { user ->
            if (user != null) {
                flow { emitAll(db.sessionDao().getSessionFlow(user.userId, contactUserId)) }.flowOn(Dispatchers.IO)
            } else {
                flowOf(null)
            }
        }
    }

    fun getMessagesForConversation(contactUserId: String): Flow<List<MessageEntity>> = flow {
        emitAll(db.messageDao().getMessagesForConversation(contactUserId))
    }.flowOn(Dispatchers.IO)

    /**
     * Sends an encrypted 1:1 text/media message using Double Ratchet.
     */
    suspend fun sendMessage(contactUserId: String, text: String, mediaUrl: String? = null, mediaType: String? = null) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        val session = getOrCreateSession(contactUserId)

        // Serialize message to JSON internally to support media
        val payloadStr = org.json.JSONObject().apply {
            put("text", text)
            if (mediaUrl != null) put("mediaUrl", mediaUrl)
            if (mediaType != null) put("mediaType", mediaType)
        }.toString()

        val drState = DoubleRatchet.SessionState(
            rootKeyHex = session.rootKeyHex,
            localDhPrivateKeyHex = session.localDhPrivateKeyHex,
            localDhPublicKeyHex = session.localDhPublicKeyHex,
            remoteDhPublicKeyHex = session.remoteDhPublicKeyHex,
            sendingChainKeyHex = session.sendingChainKeyHex,
            receivingChainKeyHex = session.receivingChainKeyHex,
            sendSequenceNumber = session.sendSequenceNumber,
            receiveSequenceNumber = session.receiveSequenceNumber,
            previousChainLength = session.previousChainLength
        )

        val (updatedDrState, encryptedMsg) = DoubleRatchet.ratchetEncrypt(drState, payloadStr)

        // Update local session
        val updatedSession = session.copy(
            rootKeyHex = updatedDrState.rootKeyHex,
            localDhPrivateKeyHex = updatedDrState.localDhPrivateKeyHex,
            localDhPublicKeyHex = updatedDrState.localDhPublicKeyHex,
            remoteDhPublicKeyHex = updatedDrState.remoteDhPublicKeyHex,
            sendingChainKeyHex = updatedDrState.sendingChainKeyHex,
            receivingChainKeyHex = updatedDrState.receivingChainKeyHex,
            sendSequenceNumber = updatedDrState.sendSequenceNumber,
            receiveSequenceNumber = updatedDrState.receiveSequenceNumber,
            previousChainLength = updatedDrState.previousChainLength
        )
        db.sessionDao().saveSession(updatedSession)

        // Save local MessageEntity
        val msgId = "msg_${UUID.randomUUID().toString().take(8)}"
        val messageEntity = MessageEntity(
            messageId = msgId,
            conversationUserId = contactUserId,
            senderUserId = currentUser.userId,
            recipientUserId = contactUserId,
            ciphertextHex = encryptedMsg.ciphertextHex,
            ivHex = encryptedMsg.ivHex,
            plaintext = text,
            timestamp = System.currentTimeMillis(),
            isOutgoing = true,
            isDelivered = true,
            dhEphemeralKeyHex = encryptedMsg.header.dhEphemeralPublicKeyHex,
            sequenceNumber = encryptedMsg.header.messageNumber,
            mediaUrl = mediaUrl,
            mediaType = mediaType
        )
        db.messageDao().insertMessage(messageEntity)

        // Dispatch encrypted packet to Relay Server
        val isFirstMessage = session.sendSequenceNumber == 0
        val packet = com.example.phantom.data.network.EncryptedMessagePacket(
            packetId = msgId,
            senderUserId = currentUser.userId,
            recipientUserId = contactUserId,
            dhEphemeralKeyHex = encryptedMsg.header.dhEphemeralPublicKeyHex,
            previousChainLength = encryptedMsg.header.previousChainLength,
            messageNumber = encryptedMsg.header.messageNumber,
            ciphertextHex = encryptedMsg.ciphertextHex,
            ivHex = encryptedMsg.ivHex,
            timestamp = System.currentTimeMillis(),
            senderEphemeralKeyHex = if (isFirstMessage) session.aliceBaseKeyHex else null,
            oneTimePrekeyUsedHex = if (isFirstMessage) session.oneTimePrekeyUsedHex else null
        )
        supabaseManager.sendMessage(packet)
        Log.d("PhantomRepository", "Sent message $msgId to $contactUserId (first=$isFirstMessage, opk=${session.oneTimePrekeyUsedHex != null})")
    }

    /**
     * Polls the server for pending messages, processes them, and deletes them from the server.
     * This is a reliable fallback for when Supabase Realtime doesn't deliver messages.
     */
    suspend fun pollForNewMessages() = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        try {
            val serverMessages = supabaseManager.getMessages(currentUser.userId)
            if (serverMessages.isEmpty()) return@withContext
            
            Log.d("PhantomRepository", "Polling found ${serverMessages.size} messages on server")
            val processedIds = mutableListOf<String>()
            
            for (packet in serverMessages) {
                // Skip if we already have this message locally
                if (db.messageDao().messageExists(packet.packetId)) {
                    Log.d("PhantomRepository", "Message ${packet.packetId} already exists locally, deleting from server")
                    processedIds.add(packet.packetId)
                    continue
                }
                
                try {
                    processMessagePacket(packet, currentUser)
                    processedIds.add(packet.packetId)
                    Log.d("PhantomRepository", "Successfully processed message ${packet.packetId}")
                } catch (e: Exception) {
                    Log.e("PhantomRepository", "Failed to process message ${packet.packetId}", e)
                    // Still delete to prevent replay corruption
                    processedIds.add(packet.packetId)
                }
            }
            
            // Delete all processed messages from the server
            if (processedIds.isNotEmpty()) {
                supabaseManager.deleteMessages(processedIds)
                Log.d("PhantomRepository", "Deleted ${processedIds.size} processed messages from server")
            }
        } catch (e: Exception) {
            Log.e("PhantomRepository", "Polling failed", e)
        }
    }

    /**
     * Observes real-time messages via Supabase Realtime channels.
     * Messages received here are also deleted from the server after processing.
     */
    suspend fun observeRealtimeMessages(): Unit = withContext(Dispatchers.IO) {
        if (!isObservingMessages.compareAndSet(false, true)) {
            Log.d("PhantomRepository", "Already observing realtime messages, skipping")
            return@withContext
        }
        try {
            val currentUser = getCurrentUser() ?: return@withContext
            Log.d("PhantomRepository", "Starting realtime message observation for ${currentUser.userId}")
            
            supabaseManager.observeMessages(currentUser.userId).collect { packet ->
                val user = getCurrentUser() ?: return@collect
                
                // Skip duplicate messages
                if (db.messageDao().messageExists(packet.packetId)) {
                    Log.d("PhantomRepository", "Realtime: Message ${packet.packetId} already exists, deleting from server")
                    supabaseManager.deleteMessage(packet.packetId)
                    return@collect
                }
                
                try {
                    processMessagePacket(packet, user)
                    // Delete from server after successful processing
                    supabaseManager.deleteMessage(packet.packetId)
                    Log.d("PhantomRepository", "Realtime: Processed and deleted message ${packet.packetId}")
                } catch (e: Exception) {
                    Log.e("PhantomRepository", "Realtime: Failed to process message ${packet.packetId}", e)
                    // Delete to prevent re-processing corrupt packets
                    supabaseManager.deleteMessage(packet.packetId)
                }
            }
        } catch (e: Exception) {
            Log.e("PhantomRepository", "Realtime observation error", e)
        } finally {
            isObservingMessages.set(false)
            Log.d("PhantomRepository", "Realtime message observation ended")
        }
    }

    /**
     * Legacy method name kept for compatibility. Now uses the two-pronged approach:
     * realtime observation + polling fallback.
     */
    suspend fun pollAndDecryptIncomingMessages(): Unit = withContext(Dispatchers.IO) {
        // First poll for any missed messages
        pollForNewMessages()
        // Then start realtime observation
        observeRealtimeMessages()
    }

    private suspend fun processMessagePacket(packet: com.example.phantom.data.network.EncryptedMessagePacket, currentUser: UserEntity) {
        if (packet.recipientUserId != currentUser.userId) return

        // CRITICAL: Skip messages we already processed to prevent Double Ratchet corruption
        if (db.messageDao().messageExists(packet.packetId)) {
            Log.d("PhantomRepository", "Skipping already-processed message ${packet.packetId}")
            return
        }

        val senderUserId = packet.senderUserId
        var session = db.sessionDao().getSession(currentUser.userId, senderUserId)

        if (session == null) {
            // Sender initiated X3DH with me. Reconstruct matching secret!
            Log.d("PhantomRepository", "No session for $senderUserId — performing X3DH receive handshake")
            val senderProfile = try { supabaseManager.getProfile(senderUserId) } catch (e: Exception) {
                Log.e("PhantomRepository", "Failed to fetch sender profile for $senderUserId", e)
                null
            }
            if (senderProfile == null) return
            val myIdentityKeyPair = Pair(
                CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(currentUser.identityPrivateKeyHex)),
                CryptoUtils.parsePublicKey(CryptoUtils.fromHex(currentUser.identityPublicKeyHex))
            )
            val mySignedPrekeyPriv = CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(currentUser.signedPrekeyPrivateHex))

            val mySignedPrekeyPub = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(currentUser.signedPrekeyPublicHex))
            val mySignedPrekeyPair = Pair(mySignedPrekeyPriv, mySignedPrekeyPub)

            // Look up the One-Time Prekey private key that Alice consumed
            var opkPrivateKey: java.security.PrivateKey? = null
            if (packet.oneTimePrekeyUsedHex != null) {
                val opkEntity = db.prekeyDao().getPrekeyByPublicKey(packet.oneTimePrekeyUsedHex)
                if (opkEntity != null) {
                    opkPrivateKey = CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(opkEntity.privateKeyHex))
                    db.prekeyDao().markUsed(opkEntity.prekeyId)
                    Log.d("PhantomRepository", "Found matching OPK: ${opkEntity.prekeyId}")
                } else {
                    Log.w("PhantomRepository", "OPK public key not found locally — session secret may mismatch!")
                }
            } else {
                Log.d("PhantomRepository", "No OPK was used in this handshake")
            }

            val masterSecretHex = X3DH.receiveHandshake(
                bobIdentityKeyPair = myIdentityKeyPair,
                bobSignedPrekeyPrivate = mySignedPrekeyPriv,
                bobOneTimePrekeyPrivate = opkPrivateKey,
                aliceIdentityKeyHex = senderProfile.identityPublicKeyHex ?: return,
                aliceEphemeralKeyHex = packet.senderEphemeralKeyHex ?: return
            )
            Log.d("PhantomRepository", "X3DH receive handshake complete for $senderUserId")

            val drState = DoubleRatchet.initializeBobSession(masterSecretHex, mySignedPrekeyPair)
            session = SessionEntity(
                contactUserId = senderUserId,
                localUserId = currentUser.userId,
                rootKeyHex = drState.rootKeyHex,
                localDhPrivateKeyHex = drState.localDhPrivateKeyHex,
                localDhPublicKeyHex = drState.localDhPublicKeyHex,
                remoteDhPublicKeyHex = drState.remoteDhPublicKeyHex,
                sendingChainKeyHex = drState.sendingChainKeyHex,
                receivingChainKeyHex = drState.receivingChainKeyHex,
                sendSequenceNumber = drState.sendSequenceNumber,
                receiveSequenceNumber = drState.receiveSequenceNumber,
                previousChainLength = drState.previousChainLength,
                sharedMasterSecretHex = masterSecretHex
            )

            // Auto-create a contact for the sender if we don't have one
            val existingFriendship = db.friendshipDao().getFriendship(currentUser.userId, senderUserId)
            if (existingFriendship == null) {
                val friendEntity = FriendshipEntity(
                    localUserId = currentUser.userId,
                    friendUserId = senderUserId,
                    friendUsername = senderProfile.username ?: "Unknown",
                    friendDisplayName = senderProfile.displayName ?: senderProfile.username ?: "Unknown",
                    friendAvatarStyle = senderProfile.avatarStyle ?: "",
                    status = "ACCEPTED"
                )
                db.friendshipDao().insertFriendship(friendEntity)
                Log.d("PhantomRepository", "Auto-created contact for incoming message sender: $senderUserId")
            } else if (existingFriendship.status != "ACCEPTED") {
                db.friendshipDao().updateStatus(currentUser.userId, senderUserId, "ACCEPTED")
                Log.d("PhantomRepository", "Updated contact status to ACCEPTED for incoming message sender: $senderUserId")
            }
        }

        val drState = DoubleRatchet.SessionState(
            rootKeyHex = session!!.rootKeyHex,
            localDhPrivateKeyHex = session.localDhPrivateKeyHex,
            localDhPublicKeyHex = session.localDhPublicKeyHex,
            remoteDhPublicKeyHex = session.remoteDhPublicKeyHex,
            sendingChainKeyHex = session.sendingChainKeyHex,
            receivingChainKeyHex = session.receivingChainKeyHex,
            sendSequenceNumber = session.sendSequenceNumber,
            receiveSequenceNumber = session.receiveSequenceNumber,
            previousChainLength = session.previousChainLength
        )

        val ratchetMsg = DoubleRatchet.EncryptedRatchetMessage(
            header = DoubleRatchet.MessageHeader(
                dhEphemeralPublicKeyHex = packet.dhEphemeralKeyHex,
                previousChainLength = packet.previousChainLength,
                messageNumber = packet.messageNumber
            ),
            ciphertextHex = packet.ciphertextHex,
            ivHex = packet.ivHex
        )

        try {
            val (updatedDrState, decryptedPlaintext) = DoubleRatchet.ratchetDecrypt(drState, ratchetMsg)

            val updatedSession = session.copy(
                rootKeyHex = updatedDrState.rootKeyHex,
                localDhPrivateKeyHex = updatedDrState.localDhPrivateKeyHex,
                localDhPublicKeyHex = updatedDrState.localDhPublicKeyHex,
                remoteDhPublicKeyHex = updatedDrState.remoteDhPublicKeyHex,
                sendingChainKeyHex = updatedDrState.sendingChainKeyHex,
                receivingChainKeyHex = updatedDrState.receivingChainKeyHex,
                sendSequenceNumber = updatedDrState.sendSequenceNumber,
                receiveSequenceNumber = updatedDrState.receiveSequenceNumber,
                previousChainLength = updatedDrState.previousChainLength
            )
            db.sessionDao().saveSession(updatedSession)

            var text = decryptedPlaintext
            var mediaUrl: String? = null
            var mediaType: String? = null

            try {
                val json = org.json.JSONObject(decryptedPlaintext)
                if (json.has("text")) text = json.getString("text")
                if (json.has("mediaUrl")) mediaUrl = json.getString("mediaUrl")
                if (json.has("mediaType")) mediaType = json.getString("mediaType")
            } catch (e: Exception) {
                // Backwards compatibility for plain string messages
            }

            val messageEntity = MessageEntity(
                messageId = packet.packetId,
                conversationUserId = senderUserId,
                senderUserId = senderUserId,
                recipientUserId = currentUser.userId,
                ciphertextHex = packet.ciphertextHex,
                ivHex = packet.ivHex,
                plaintext = text,
                timestamp = packet.timestamp,
                isOutgoing = false,
                isDelivered = true,
                dhEphemeralKeyHex = packet.dhEphemeralKeyHex,
                sequenceNumber = packet.messageNumber,
                mediaUrl = mediaUrl,
                mediaType = mediaType
            )
            db.messageDao().insertMessage(messageEntity)
            Log.i("PhantomE2E", "SUCCESSFULLY_DECRYPTED_MESSAGE: $text")
        } catch (e: Exception) {
            Log.e("PhantomRepository", "Failed to decrypt incoming message", e)
        }
    }
}
