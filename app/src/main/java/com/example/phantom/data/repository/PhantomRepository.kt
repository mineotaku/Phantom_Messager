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
import com.example.phantom.data.network.FriendRequestAcceptPayload
import com.example.phantom.data.network.FriendRequestPayload
import com.example.phantom.data.network.RetrofitClient
import com.example.phantom.data.network.WebSocketManager
import com.example.phantom.data.network.ProfilePayload
import com.example.phantom.data.network.RegisterPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.util.UUID

class PhantomRepository(private val db: PhantomDatabase) {

    val currentUserFlow: Flow<UserEntity?> = db.userDao().getCurrentUserFlow()

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

        WebSocketManager.connect(userId)

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
                RetrofitClient.api.registerUser(registerPayload)
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
        try {
            // Check if server knows about us
            RetrofitClient.api.getProfile(currentUser.userId)
            Log.d("PhantomRepository", "User already registered on server")
        } catch (e: Exception) {
            // Server doesn't know us — re-register
            Log.w("PhantomRepository", "User not found on server, re-registering...")
            registerOnServer(currentUser)
        }
        // Ensure WebSocket is connected
        WebSocketManager.connect(currentUser.userId)
    }

    /**
     * Fetches and processes any pending friend requests from the server.
     * Called on app startup to sync requests that arrived while offline.
     */
    suspend fun fetchPendingFriendRequests() = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        try {
            val pendingRequests = RetrofitClient.api.getFriendRequests(currentUser.userId)
            for (request in pendingRequests) {
                val existing = db.friendshipDao().getFriendship(currentUser.userId, request.fromUserId)
                if (existing == null) {
                    val friendEntity = FriendshipEntity(
                        localUserId = currentUser.userId,
                        friendUserId = request.fromUserId,
                        friendUsername = request.fromUsername,
                        friendDisplayName = request.fromDisplayName,
                        friendAvatarStyle = request.fromAvatarStyle,
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
        WebSocketManager.connect(targetUserId)
    }

    fun getFriendshipsFlow(localUserId: String): Flow<List<FriendshipEntity>> {
        return db.friendshipDao().getFriendshipsFlow(localUserId)
    }

    fun getAcceptedFriendsFlow(localUserId: String): Flow<List<FriendshipEntity>> {
        return db.friendshipDao().getAcceptedFriendsFlow(localUserId)
    }

    suspend fun searchUsers(query: String): List<ProfilePayload> = withContext(Dispatchers.IO) {
        RetrofitClient.api.searchProfiles(query)
    }

    /**
     * Sends a friend request both locally AND to the server for relay to the recipient.
     */
    suspend fun sendFriendRequest(friendUserId: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        val profile = RetrofitClient.api.getProfile(friendUserId)
            
        val friendEntity = FriendshipEntity(
            localUserId = currentUser.userId,
            friendUserId = friendUserId,
            friendUsername = profile.username,
            friendDisplayName = profile.displayName,
            friendAvatarStyle = profile.avatarStyle,
            status = "PENDING_SENT"
        )
        db.friendshipDao().insertFriendship(friendEntity)

        // Notify the server so the recipient gets the request
        try {
            RetrofitClient.api.sendFriendRequest(
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
            RetrofitClient.api.acceptFriendRequest(
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
    suspend fun processFriendRequestEvents() = withContext(Dispatchers.IO) {
        WebSocketManager.friendRequestFlow.collect { event ->
            val currentUser = getCurrentUser() ?: return@collect
            val existing = db.friendshipDao().getFriendship(currentUser.userId, event.fromUserId)
            if (existing == null) {
                val friendEntity = FriendshipEntity(
                    localUserId = currentUser.userId,
                    friendUserId = event.fromUserId,
                    friendUsername = event.fromUsername,
                    friendDisplayName = event.fromDisplayName,
                    friendAvatarStyle = event.fromAvatarStyle,
                    status = "PENDING_RECEIVED"
                )
                db.friendshipDao().insertFriendship(friendEntity)
                Log.d("PhantomRepository", "Received friend request from ${event.fromUserId}")
            }
        }
    }

    /**
     * Processes incoming friend acceptance WebSocket events in real-time.
     * Updates PENDING_SENT entries to ACCEPTED.
     */
    suspend fun processFriendAcceptedEvents() = withContext(Dispatchers.IO) {
        WebSocketManager.friendAcceptedFlow.collect { event ->
            val currentUser = getCurrentUser() ?: return@collect
            val existing = db.friendshipDao().getFriendship(currentUser.userId, event.acceptedByUserId)
            if (existing != null && existing.status == "PENDING_SENT") {
                db.friendshipDao().updateStatus(currentUser.userId, event.acceptedByUserId, "ACCEPTED")
                Log.d("PhantomRepository", "Friend request accepted by ${event.acceptedByUserId}")
            } else if (existing == null) {
                // Edge case: create an accepted friendship if we didn't have one
                val friendEntity = FriendshipEntity(
                    localUserId = currentUser.userId,
                    friendUserId = event.acceptedByUserId,
                    friendUsername = event.acceptedByUsername,
                    friendDisplayName = event.acceptedByDisplayName,
                    friendAvatarStyle = event.acceptedByAvatarStyle,
                    status = "ACCEPTED"
                )
                db.friendshipDao().insertFriendship(friendEntity)
            }
        }
    }

    /**
     * Establishes or retrieves the E2EE Double Ratchet session for a contact.
     */
    suspend fun getOrCreateSession(contactUserId: String): SessionEntity = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: throw IllegalStateException("No active user logged in!")
        val existingSession = db.sessionDao().getSession(currentUser.userId, contactUserId)
        if (existingSession != null) return@withContext existingSession

        val bundleResponse = RetrofitClient.api.getPrekeyBundle(contactUserId)
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
            aliceBaseKeyHex = x3dhResult.senderEphemeralPublicKeyHex
        )

        db.sessionDao().saveSession(sessionEntity)
        sessionEntity
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSessionFlow(contactUserId: String): Flow<SessionEntity?> {
        return currentUserFlow.flatMapLatest { user ->
            if (user != null) {
                db.sessionDao().getSessionFlow(user.userId, contactUserId)
            } else {
                flowOf(null)
            }
        }
    }

    fun getMessagesForConversation(contactUserId: String): Flow<List<MessageEntity>> {
        return db.messageDao().getMessagesForConversation(contactUserId)
    }

    /**
     * Sends an encrypted 1:1 text message using Double Ratchet.
     */
    suspend fun sendMessage(contactUserId: String, plaintext: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        val session = getOrCreateSession(contactUserId)

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

        val (updatedDrState, encryptedMsg) = DoubleRatchet.ratchetEncrypt(drState, plaintext)

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
            plaintext = plaintext,
            timestamp = System.currentTimeMillis(),
            isOutgoing = true,
            isDelivered = true,
            dhEphemeralKeyHex = encryptedMsg.header.dhEphemeralPublicKeyHex,
            sequenceNumber = encryptedMsg.header.messageNumber
        )
        db.messageDao().insertMessage(messageEntity)

        // Dispatch encrypted packet to Relay Server
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
            senderEphemeralKeyHex = if (session.sendSequenceNumber == 0) session.aliceBaseKeyHex else null
        )
        WebSocketManager.sendMessage(packet)
    }

    /**
     * Polling is now replaced by real-time WebSocket observing.
     */
    suspend fun pollAndDecryptIncomingMessages(): Unit = withContext(Dispatchers.IO) {
        // Collect messages from the shared flow
        WebSocketManager.messageFlow.collect { packet ->
            val currentUser = getCurrentUser() ?: return@collect
            if (packet.recipientUserId != currentUser.userId) return@collect
            
            val senderUserId = packet.senderUserId
            var session = db.sessionDao().getSession(currentUser.userId, senderUserId)

            if (session == null) {
                // Sender initiated X3DH with me. Reconstruct matching secret!
                val senderProfile = try { RetrofitClient.api.getProfile(senderUserId) } catch (e: Exception) { null }
                if (senderProfile == null) return@collect
                val myIdentityKeyPair = Pair(
                    CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(currentUser.identityPrivateKeyHex)),
                    CryptoUtils.parsePublicKey(CryptoUtils.fromHex(currentUser.identityPublicKeyHex))
                )
                val mySignedPrekeyPriv = CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(currentUser.signedPrekeyPrivateHex))

                val mySignedPrekeyPub = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(currentUser.signedPrekeyPublicHex))
                val mySignedPrekeyPair = Pair(mySignedPrekeyPriv, mySignedPrekeyPub)

                val masterSecretHex = X3DH.receiveHandshake(
                    bobIdentityKeyPair = myIdentityKeyPair,
                    bobSignedPrekeyPrivate = mySignedPrekeyPriv,
                    bobOneTimePrekeyPrivate = null,
                    aliceIdentityKeyHex = senderProfile.identityPublicKeyHex,
                    aliceEphemeralKeyHex = packet.senderEphemeralKeyHex ?: return@collect
                )

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
                        friendUsername = senderProfile.username,
                        friendDisplayName = senderProfile.displayName,
                        friendAvatarStyle = senderProfile.avatarStyle,
                        status = "ACCEPTED"
                    )
                    db.friendshipDao().insertFriendship(friendEntity)
                    Log.d("PhantomRepository", "Auto-created contact for incoming message sender: $senderUserId")
                }
            }

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

                val messageEntity = MessageEntity(
                    messageId = packet.packetId,
                    conversationUserId = senderUserId,
                    senderUserId = senderUserId,
                    recipientUserId = currentUser.userId,
                    ciphertextHex = packet.ciphertextHex,
                    ivHex = packet.ivHex,
                    plaintext = decryptedPlaintext,
                    timestamp = packet.timestamp,
                    isOutgoing = false,
                    isDelivered = true,
                    dhEphemeralKeyHex = packet.dhEphemeralKeyHex,
                    sequenceNumber = packet.messageNumber
                )
                db.messageDao().insertMessage(messageEntity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
