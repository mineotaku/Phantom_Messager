package com.example.phantom.data.repository

import com.example.phantom.crypto.CryptoUtils
import com.example.phantom.crypto.DoubleRatchet
import com.example.phantom.crypto.X3DH
import com.example.phantom.data.db.FriendshipEntity
import com.example.phantom.data.db.MessageEntity
import com.example.phantom.data.db.PhantomDatabase
import com.example.phantom.data.db.PrekeyEntity
import com.example.phantom.data.db.SessionEntity
import com.example.phantom.data.db.UserEntity
import com.example.phantom.data.network.RetrofitClient
import com.example.phantom.data.network.WebSocketManager
import com.example.phantom.data.network.ProfilePayload
import com.example.phantom.data.network.RegisterPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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

        val profilePayload = ProfilePayload(
            userId = userId,
            username = cleanUsername,
            displayName = displayName,
            avatarStyle = avatarStyle,
            bio = bio,
            identityPublicKeyHex = identityPubHex
        )

        val registerPayload = RegisterPayload(
            profile = profilePayload,
            signedPrekeyPublicHex = signedPrekeyPubHex,
            signedPrekeySignatureHex = sigHex,
            oneTimePrekeysHex = opkPublicHexes
        )

        try {
            RetrofitClient.api.registerUser(registerPayload)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        WebSocketManager.connect(userId)

        userEntity
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

    suspend fun searchUsers(query: String) = withContext(Dispatchers.IO) {
        RetrofitClient.api.searchUsers(query)
    }

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
    }

    suspend fun acceptFriendRequest(friendUserId: String) = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: return@withContext
        db.friendshipDao().updateStatus(currentUser.userId, friendUserId, "ACCEPTED")
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
     * Establishes or retrieves the E2EE Double Ratchet session for a contact.
     */
    suspend fun getOrCreateSession(contactUserId: String): SessionEntity = withContext(Dispatchers.IO) {
        val currentUser = getCurrentUser() ?: throw IllegalStateException("No active user logged in!")
        val existingSession = db.sessionDao().getSession(currentUser.userId, contactUserId)
        if (existingSession != null) return@withContext existingSession

        // Perform X3DH Handshake
        val prekeyBundle = RetrofitClient.api.getPrekeyBundle(contactUserId)

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
            sharedMasterSecretHex = x3dhResult.sharedMasterSecretHex
        )

        db.sessionDao().saveSession(sessionEntity)
        sessionEntity
    }

    fun getSessionFlow(contactUserId: String): Flow<SessionEntity?> {
        return db.sessionDao().getSessionFlow("", contactUserId)
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
            timestamp = System.currentTimeMillis()
        )
        WebSocketManager.sendMessage(packet)
    }

    /**
     * Polling is now replaced by real-time WebSocket observing.
     */
    suspend fun pollAndDecryptIncomingMessages() = withContext(Dispatchers.IO) {
        // Collect messages from the shared flow
        WebSocketManager.messageFlow.collect { packet ->
            val currentUser = getCurrentUser() ?: return@collect
            if (packet.recipientUserId != currentUser.userId) return@collect
            
            val senderUserId = packet.senderUserId
            var session = db.sessionDao().getSession(currentUser.userId, senderUserId)

            if (session == null) {
                // Sender initiated X3DH with me. Reconstruct matching secret!
                val senderProfile = try { RetrofitClient.api.getProfile(senderUserId) } catch (e: Exception) { null } ?: return@collect
                val myIdentityKeyPair = Pair(
                    CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(currentUser.identityPrivateKeyHex)),
                    CryptoUtils.parsePublicKey(CryptoUtils.fromHex(currentUser.identityPublicKeyHex))
                )
                val mySignedPrekeyPriv = CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(currentUser.signedPrekeyPrivateHex))

                val masterSecretHex = X3DH.receiveHandshake(
                    bobIdentityKeyPair = myIdentityKeyPair,
                    bobSignedPrekeyPrivate = mySignedPrekeyPriv,
                    bobOneTimePrekeyPrivate = null,
                    aliceIdentityKeyHex = senderProfile.identityPublicKeyHex,
                    aliceEphemeralKeyHex = packet.dhEphemeralKeyHex
                )

                val drState = DoubleRatchet.initializeBobSession(masterSecretHex, myIdentityKeyPair)
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
