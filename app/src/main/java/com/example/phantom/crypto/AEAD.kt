package com.example.phantom.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AEAD (Authenticated Encryption with Associated Data) using AES-256-GCM.
 * Standard 12-byte IV and 128-bit authentication tag.
 */
object AEAD {

    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    class EncryptedData(
        val ciphertextHex: String,
        val ivHex: String
    )

    fun encrypt(key32Bytes: ByteArray, plaintext: String, associatedData: ByteArray = ByteArray(0)): EncryptedData {
        val iv = CryptoUtils.generateRandomBytes(GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key32Bytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedData(
            ciphertextHex = CryptoUtils.toHex(ciphertext),
            ivHex = CryptoUtils.toHex(iv)
        )
    }

    fun decrypt(key32Bytes: ByteArray, ciphertextHex: String, ivHex: String, associatedData: ByteArray = ByteArray(0)): String {
        val ciphertext = CryptoUtils.fromHex(ciphertextHex)
        val iv = CryptoUtils.fromHex(ivHex)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key32Bytes, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }
}
