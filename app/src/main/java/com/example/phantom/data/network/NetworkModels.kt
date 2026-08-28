package com.example.phantom.data.network

/** Response for user registration operations. */
data class RegisterResponse(val success: Boolean, val error: String?)

/** Prekey bundle fetched from the server for X3DH handshake. */
data class BundleResponse(
    val identityPublicKeyHex: String,
    val signedPrekeyPublicHex: String,
    val signedPrekeySignatureHex: String,
    val oneTimePrekeyHex: String?
)

/** Response for friend request operations. */
data class FriendRequestResponse(val success: Boolean)

/** Incoming friend request item from the server. */
data class FriendRequestItem(
    val fromUserId: String,
    val fromUsername: String? = null,
    val fromDisplayName: String? = null,
    val fromAvatarStyle: String? = null,
    val timestamp: Long = 0L
)

/** Server health-check ping response. */
data class PingResponse(val status: String, val users: Int)

/** Response containing a URL for an uploaded media file. */
data class UploadResponse(val url: String)

/** Real-time event fired when a friend request is accepted. */
data class FriendRequestAcceptedEvent(
    val acceptedByUserId: String,
    val acceptedByUsername: String? = null,
    val acceptedByDisplayName: String? = null,
    val acceptedByAvatarStyle: String? = null
)
