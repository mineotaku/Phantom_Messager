package com.example.phantom.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val displayName: String,
    val avatarStyle: String,
    val bio: String,
    val identityPublicKeyHex: String,
    val identityPrivateKeyHex: String,
    val signedPrekeyPublicHex: String,
    val signedPrekeyPrivateHex: String,
    val signedPrekeySignatureHex: String,
    val recoveryKey: String,
    val isCurrentLocalUser: Boolean = false
)

@Entity(tableName = "friendships")
data class FriendshipEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val localUserId: String,
    val friendUserId: String,
    val friendUsername: String,
    val friendDisplayName: String,
    val friendAvatarStyle: String,
    val status: String, // PENDING_SENT, PENDING_RECEIVED, ACCEPTED, BLOCKED
    val isVerifiedKey: Boolean = false,
    val identityKeyChangedWarning: Boolean = false
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val contactUserId: String,
    val localUserId: String,
    val rootKeyHex: String,
    val localDhPrivateKeyHex: String,
    val localDhPublicKeyHex: String,
    val remoteDhPublicKeyHex: String?,
    val sendingChainKeyHex: String?,
    val receivingChainKeyHex: String?,
    val sendSequenceNumber: Int,
    val receiveSequenceNumber: Int,
    val previousChainLength: Int,
    val sharedMasterSecretHex: String
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val conversationUserId: String,
    val senderUserId: String,
    val recipientUserId: String,
    val ciphertextHex: String,
    val ivHex: String,
    val plaintext: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isDelivered: Boolean,
    val dhEphemeralKeyHex: String,
    val sequenceNumber: Int
)

@Entity(tableName = "prekeys")
data class PrekeyEntity(
    @PrimaryKey val prekeyId: String,
    val userId: String,
    val publicKeyHex: String,
    val privateKeyHex: String,
    val isUsed: Boolean = false
)
