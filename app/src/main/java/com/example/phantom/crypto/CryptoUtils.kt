package com.example.phantom.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Core cryptographic utility functions for Phantom.
 * Uses standard Java/Android Cryptography Architecture (JCA) for compatibility.
 */
object CryptoUtils {

    private val secureRandom = SecureRandom()

    fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun fromHex(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Generates a 256-bit KeyPair for Identity or Ephemeral Keys.
     * Uses EC (secp256r1) or X25519 depending on runtime support.
     */
    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    /**
     * Performs Diffie-Hellman Key Agreement between local private key and remote public key.
     */
    fun diffieHellman(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(publicKey, true)
        return ka.generateSecret()
    }

    /**
     * Signs data using Ed25519/ECDSA private identity key.
     */
    fun signData(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(data)
        return sig.sign()
    }

    /**
     * Verifies data signature using remote identity public key.
     */
    fun verifySignature(publicKey: PublicKey, data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reconstructs PublicKey from encoded X509 bytes.
     */
    fun parsePublicKey(encoded: ByteArray): PublicKey {
        val spec = X509EncodedKeySpec(encoded)
        return try {
            KeyFactory.getInstance("EC").generatePublic(spec)
        } catch (e: Exception) {
            KeyFactory.getInstance("RSA").generatePublic(spec)
        }
    }

    /**
     * Reconstructs PrivateKey from encoded PKCS8 bytes.
     */
    fun parsePrivateKey(encoded: ByteArray): PrivateKey {
        val spec = PKCS8EncodedKeySpec(encoded)
        return try {
            KeyFactory.getInstance("EC").generatePrivate(spec)
        } catch (e: Exception) {
            KeyFactory.getInstance("RSA").generatePrivate(spec)
        }
    }

    /**
     * HKDF-SHA256 Key Derivation Function (RFC 5869).
     */
    fun hkdfExtractAndExpand(
        salt: ByteArray,
        ikm: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        // HKDF-Extract
        val macExtract = Mac.getInstance("HmacSHA256")
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        macExtract.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = macExtract.doFinal(ikm)

        // HKDF-Expand
        val macExpand = Mac.getInstance("HmacSHA256")
        macExpand.init(SecretKeySpec(prk, "HmacSHA256"))

        val result = ByteArray(outputLength)
        var t = ByteArray(0)
        var generated = 0
        var i = 1

        while (generated < outputLength) {
            macExpand.update(t)
            macExpand.update(info)
            macExpand.update(i.toByte())
            t = macExpand.doFinal()

            val toCopy = Math.min(t.size, outputLength - generated)
            System.arraycopy(t, 0, result, generated, toCopy)
            generated += toCopy
            i++
        }

        return result
    }

    /**
     * Generates a 48-digit Safety Number (Fingerprint) for identity verification between 2 users.
     * Formatted as 12 groups of 4 digits (e.g., "1839 4028 1948 2059...").
     */
    fun generateSafetyNumber(userAIdentityKeyHex: String, userBIdentityKeyHex: String): String {
        val sortedKeys = listOf(userAIdentityKeyHex, userBIdentityKeyHex).sorted()
        val combined = sortedKeys[0] + ":" + sortedKeys[1]
        val digest = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray(Charsets.UTF_8))
        
        val sb = StringBuilder()
        for (i in 0 until 12) {
            val offset = (i * 2) % (digest.size - 1)
            val val16 = ((digest[offset].toInt() and 0xFF) shl 8) or (digest[offset + 1].toInt() and 0xFF)
            val chunk = (val16 % 10000).toString().padStart(4, '0')
            sb.append(chunk)
            if (i < 11) {
                if ((i + 1) % 3 == 0) sb.append("\n") else sb.append(" ")
            }
        }
        return sb.toString()
    }

    /**
     * Generates a 24-word recovery seed phrase string.
     */
    fun generateRecoveryKey(): String {
        val wordList = listOf(
            "phantom", "cipher", "ratchet", "shadow", "vault", "matrix", "nexus", "quantum",
            "crypto", "shield", "orbit", "vector", "signal", "beacon", "prism", "vortex",
            "zenith", "apex", "echo", "starlight", "hyperion", "cobalt", "obsidian", "emerald",
            "titan", "polaris", "aurora", "solaris", "horizon", "velocity", "pioneer", "specter"
        )
        val words = mutableListOf<String>()
        val bytes = generateRandomBytes(24)
        for (i in 0 until 24) {
            val index = (bytes[i].toInt() and 0xFF) % wordList.size
            words.add(wordList[index])
        }
        return words.chunked(6).joinToString("-") { it.joinToString(" ") }
    }
}
