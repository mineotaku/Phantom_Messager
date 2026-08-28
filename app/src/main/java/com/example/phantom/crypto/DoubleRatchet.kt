package com.example.phantom.crypto

import java.security.PrivateKey
import java.security.PublicKey

/**
 * Double Ratchet implementation for Phantom E2EE messaging.
 * Provides Forward Secrecy and Post-Compromise Security.
 */
object DoubleRatchet {

    data class SessionState(
        val rootKeyHex: String,
        val localDhPrivateKeyHex: String,
        val localDhPublicKeyHex: String,
        val remoteDhPublicKeyHex: String?,
        val sendingChainKeyHex: String?,
        val receivingChainKeyHex: String?,
        val sendSequenceNumber: Int = 0,
        val receiveSequenceNumber: Int = 0,
        val previousChainLength: Int = 0
    )

    data class MessageHeader(
        val dhEphemeralPublicKeyHex: String,
        val previousChainLength: Int,
        val messageNumber: Int
    )

    data class EncryptedRatchetMessage(
        val header: MessageHeader,
        val ciphertextHex: String,
        val ivHex: String
    )

    /**
     * Initializes a Double Ratchet session from X3DH Shared Master Secret.
     */
    fun initializeAliceSession(
        sharedMasterSecretHex: String,
        bobDhPublicKeyHex: String
    ): SessionState {
        val aliceDhKeyPair = CryptoUtils.generateKeyPair()
        val bobDhPublicKey = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(bobDhPublicKeyHex))

        // DH Step with Alice's local key and Bob's remote key
        val dhSecret = CryptoUtils.diffieHellman(aliceDhKeyPair.private, bobDhPublicKey)

        val masterSecretBytes = CryptoUtils.fromHex(sharedMasterSecretHex)
        val derived = CryptoUtils.hkdfExtractAndExpand(
            salt = masterSecretBytes,
            ikm = dhSecret,
            info = "PhantomDHRatchetStep".toByteArray(Charsets.UTF_8),
            outputLength = 64
        )

        val newRootKeyHex = CryptoUtils.toHex(derived.copyOfRange(0, 32))
        val sendingChainKeyHex = CryptoUtils.toHex(derived.copyOfRange(32, 64))

        return SessionState(
            rootKeyHex = newRootKeyHex,
            localDhPrivateKeyHex = CryptoUtils.toHex(aliceDhKeyPair.private.encoded),
            localDhPublicKeyHex = CryptoUtils.toHex(aliceDhKeyPair.public.encoded),
            remoteDhPublicKeyHex = bobDhPublicKeyHex,
            sendingChainKeyHex = sendingChainKeyHex,
            receivingChainKeyHex = null,
            sendSequenceNumber = 0,
            receiveSequenceNumber = 0,
            previousChainLength = 0
        )
    }

    fun initializeBobSession(
        sharedMasterSecretHex: String,
        bobDhKeyPair: Pair<PrivateKey, PublicKey>
    ): SessionState {
        return SessionState(
            rootKeyHex = sharedMasterSecretHex,
            localDhPrivateKeyHex = CryptoUtils.toHex(bobDhKeyPair.first.encoded),
            localDhPublicKeyHex = CryptoUtils.toHex(bobDhKeyPair.second.encoded),
            remoteDhPublicKeyHex = null,
            sendingChainKeyHex = null,
            receivingChainKeyHex = null,
            sendSequenceNumber = 0,
            receiveSequenceNumber = 0,
            previousChainLength = 0
        )
    }

    /**
     * Encrypts a message using the current Sending Chain Key and advances the symmetric ratchet.
     */
    fun ratchetEncrypt(state: SessionState, plaintext: String): Pair<SessionState, EncryptedRatchetMessage> {
        var currentState = state
        val sendingChainKeyHex = currentState.sendingChainKeyHex
            ?: throw IllegalStateException("DoubleRatchet Error: Sending chain key is null!")

        val currentSendingChainKey = CryptoUtils.fromHex(sendingChainKeyHex)

        // Derive Message Key & Next Chain Key via HKDF
        val messageKeyBytes = CryptoUtils.hkdfExtractAndExpand(
            salt = currentSendingChainKey,
            ikm = "MessageKeyConstant".toByteArray(Charsets.UTF_8),
            info = "PhantomMessageKeyDerivation".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )

        val nextChainKeyBytes = CryptoUtils.hkdfExtractAndExpand(
            salt = currentSendingChainKey,
            ikm = "ChainKeyConstant".toByteArray(Charsets.UTF_8),
            info = "PhantomChainKeyStep".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )

        val header = MessageHeader(
            dhEphemeralPublicKeyHex = currentState.localDhPublicKeyHex,
            previousChainLength = currentState.previousChainLength,
            messageNumber = currentState.sendSequenceNumber
        )

        val associatedData = (header.dhEphemeralPublicKeyHex + ":" + header.messageNumber).toByteArray(Charsets.UTF_8)
        val encryptedData = AEAD.encrypt(messageKeyBytes, plaintext, associatedData)

        val updatedState = currentState.copy(
            sendingChainKeyHex = CryptoUtils.toHex(nextChainKeyBytes),
            sendSequenceNumber = currentState.sendSequenceNumber + 1
        )

        val encryptedMsg = EncryptedRatchetMessage(
            header = header,
            ciphertextHex = encryptedData.ciphertextHex,
            ivHex = encryptedData.ivHex
        )

        return Pair(updatedState, encryptedMsg)
    }

    /**
     * Decrypts a message and advances DH / Symmetric Ratchet as needed.
     */
    fun ratchetDecrypt(state: SessionState, message: EncryptedRatchetMessage): Pair<SessionState, String> {
        var currentState = state

        // Check if remote DH ephemeral key has changed -> perform DH Ratchet step
        if (currentState.remoteDhPublicKeyHex == null || currentState.remoteDhPublicKeyHex != message.header.dhEphemeralPublicKeyHex) {
            currentState = dhRatchetStep(currentState, message.header.dhEphemeralPublicKeyHex)
        }

        val receivingChainKey = CryptoUtils.fromHex(currentState.receivingChainKeyHex!!)

        // Symmetric ratchet step for receiving
        val messageKeyBytes = CryptoUtils.hkdfExtractAndExpand(
            salt = receivingChainKey,
            ikm = "MessageKeyConstant".toByteArray(Charsets.UTF_8),
            info = "PhantomMessageKeyDerivation".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )

        val nextChainKeyBytes = CryptoUtils.hkdfExtractAndExpand(
            salt = receivingChainKey,
            ikm = "ChainKeyConstant".toByteArray(Charsets.UTF_8),
            info = "PhantomChainKeyStep".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )

        val associatedData = (message.header.dhEphemeralPublicKeyHex + ":" + message.header.messageNumber).toByteArray(Charsets.UTF_8)
        val plaintext = AEAD.decrypt(messageKeyBytes, message.ciphertextHex, message.ivHex, associatedData)

        val updatedState = currentState.copy(
            receivingChainKeyHex = CryptoUtils.toHex(nextChainKeyBytes),
            receiveSequenceNumber = currentState.receiveSequenceNumber + 1
        )

        return Pair(updatedState, plaintext)
    }

    private fun dhRatchetStep(state: SessionState, newRemoteDhPublicKeyHex: String): SessionState {
        val rootKeyBytes = CryptoUtils.fromHex(state.rootKeyHex)
        val localPrivateKey = CryptoUtils.parsePrivateKey(CryptoUtils.fromHex(state.localDhPrivateKeyHex))
        val newRemotePublicKey = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(newRemoteDhPublicKeyHex))

        // DH Step 1: Receiving chain key
        val dhSecret1 = CryptoUtils.diffieHellman(localPrivateKey, newRemotePublicKey)
        val derived1 = CryptoUtils.hkdfExtractAndExpand(
            salt = rootKeyBytes,
            ikm = dhSecret1,
            info = "PhantomDHRatchetStep".toByteArray(Charsets.UTF_8),
            outputLength = 64
        )
        val nextRootKey1 = derived1.copyOfRange(0, 32)
        val receivingChainKey = derived1.copyOfRange(32, 64)

        // DH Step 2: New local DH KeyPair & sending chain key
        val newLocalDhKeyPair = CryptoUtils.generateKeyPair()
        val dhSecret2 = CryptoUtils.diffieHellman(newLocalDhKeyPair.private, newRemotePublicKey)
        val derived2 = CryptoUtils.hkdfExtractAndExpand(
            salt = nextRootKey1,
            ikm = dhSecret2,
            info = "PhantomDHRatchetStep".toByteArray(Charsets.UTF_8),
            outputLength = 64
        )
        val nextRootKey2 = derived2.copyOfRange(0, 32)
        val sendingChainKey = derived2.copyOfRange(32, 64)

        return state.copy(
            rootKeyHex = CryptoUtils.toHex(nextRootKey2),
            localDhPrivateKeyHex = CryptoUtils.toHex(newLocalDhKeyPair.private.encoded),
            localDhPublicKeyHex = CryptoUtils.toHex(newLocalDhKeyPair.public.encoded),
            remoteDhPublicKeyHex = newRemoteDhPublicKeyHex,
            sendingChainKeyHex = CryptoUtils.toHex(sendingChainKey),
            receivingChainKeyHex = CryptoUtils.toHex(receivingChainKey),
            previousChainLength = state.sendSequenceNumber,
            sendSequenceNumber = 0,
            receiveSequenceNumber = 0
        )
    }
}
