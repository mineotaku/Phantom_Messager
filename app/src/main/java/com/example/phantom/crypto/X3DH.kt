package com.example.phantom.crypto

import java.security.PrivateKey
import java.security.PublicKey

object X3DH {

    data class PrekeyBundle(
        val recipientUserId: String,
        val identityKeyHex: String,
        val signedPrekeyHex: String,
        val signedPrekeySignatureHex: String,
        val oneTimePrekeyHex: String?
    )

    data class InitiationResult(
        val sharedMasterSecretHex: String,
        val senderIdentityKeyHex: String,
        val senderEphemeralPublicKeyHex: String,
        val oneTimePrekeyUsedHex: String?
    )

    /**
     * Alice initiates X3DH handshake with Bob's Prekey Bundle.
     */
    fun initiateHandshake(
        senderIdentityKeyPair: Pair<PrivateKey, PublicKey>,
        bobBundle: PrekeyBundle
    ): InitiationResult {
        val bobIdentityKey = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(bobBundle.identityKeyHex))
        val bobSignedPrekey = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(bobBundle.signedPrekeyHex))
        val sigBytes = CryptoUtils.fromHex(bobBundle.signedPrekeySignatureHex)

        // 1. Verify Bob's Signed Prekey signature using Bob's Identity Key
        val sigValid = CryptoUtils.verifySignature(bobIdentityKey, bobSignedPrekey.encoded, sigBytes)
        if (!sigValid) {
            throw IllegalArgumentException("X3DH Error: Signed prekey signature verification failed!")
        }

        // 2. Generate Ephemeral Key pair for Alice
        val senderEphemeralKeyPair = CryptoUtils.generateKeyPair()

        // 3. Compute Diffie-Hellman secrets
        val dh1 = CryptoUtils.diffieHellman(senderIdentityKeyPair.first, bobSignedPrekey)
        val dh2 = CryptoUtils.diffieHellman(senderEphemeralKeyPair.private, bobIdentityKey)
        val dh3 = CryptoUtils.diffieHellman(senderEphemeralKeyPair.private, bobSignedPrekey)

        val ikmList = mutableListOf<Byte>()
        ikmList.addAll(dh1.toList())
        ikmList.addAll(dh2.toList())
        ikmList.addAll(dh3.toList())

        if (!bobBundle.oneTimePrekeyHex.isNullOrEmpty()) {
            val bobOPK = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(bobBundle.oneTimePrekeyHex))
            val dh4 = CryptoUtils.diffieHellman(senderEphemeralKeyPair.private, bobOPK)
            ikmList.addAll(dh4.toList())
        }

        // 4. Derive Shared Master Secret via HKDF
        val masterSecretBytes = CryptoUtils.hkdfExtractAndExpand(
            salt = ByteArray(32),
            ikm = ikmList.toByteArray(),
            info = "PhantomX3DHProtocolV1".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )

        return InitiationResult(
            sharedMasterSecretHex = CryptoUtils.toHex(masterSecretBytes),
            senderIdentityKeyHex = CryptoUtils.toHex(senderIdentityKeyPair.second.encoded),
            senderEphemeralPublicKeyHex = CryptoUtils.toHex(senderEphemeralKeyPair.public.encoded),
            oneTimePrekeyUsedHex = bobBundle.oneTimePrekeyHex
        )
    }

    /**
     * Bob receives initial message from Alice containing Alice's Ephemeral Key and Identity Key.
     * Bob calculates the matching X3DH Shared Master Secret.
     */
    fun receiveHandshake(
        bobIdentityKeyPair: Pair<PrivateKey, PublicKey>,
        bobSignedPrekeyPrivate: PrivateKey,
        bobOneTimePrekeyPrivate: PrivateKey?,
        aliceIdentityKeyHex: String,
        aliceEphemeralKeyHex: String
    ): String {
        val aliceIdentityKey = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(aliceIdentityKeyHex))
        val aliceEphemeralKey = CryptoUtils.parsePublicKey(CryptoUtils.fromHex(aliceEphemeralKeyHex))

        val dh1 = CryptoUtils.diffieHellman(bobSignedPrekeyPrivate, aliceIdentityKey)
        val dh2 = CryptoUtils.diffieHellman(bobIdentityKeyPair.first, aliceEphemeralKey)
        val dh3 = CryptoUtils.diffieHellman(bobSignedPrekeyPrivate, aliceEphemeralKey)

        val ikmList = mutableListOf<Byte>()
        ikmList.addAll(dh1.toList())
        ikmList.addAll(dh2.toList())
        ikmList.addAll(dh3.toList())

        if (bobOneTimePrekeyPrivate != null) {
            val dh4 = CryptoUtils.diffieHellman(bobOneTimePrekeyPrivate, aliceEphemeralKey)
            ikmList.addAll(dh4.toList())
        }

        val masterSecretBytes = CryptoUtils.hkdfExtractAndExpand(
            salt = ByteArray(32),
            ikm = ikmList.toByteArray(),
            info = "PhantomX3DHProtocolV1".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )

        return CryptoUtils.toHex(masterSecretBytes)
    }
}
