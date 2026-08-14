package com.example.phantom.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ProfilePayload(
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarStyle: String? = null,
    val bio: String? = null,
    val identityPublicKeyHex: String? = null
)

data class RegisterPayload(
    val profile: ProfilePayload,
    val signedPrekeyPublicHex: String,
    val signedPrekeySignatureHex: String,
    val oneTimePrekeysHex: List<String>
)

data class RegisterResponse(val success: Boolean, val error: String?)

data class BundleResponse(
    val identityPublicKeyHex: String,
    val signedPrekeyPublicHex: String,
    val signedPrekeySignatureHex: String,
    val oneTimePrekeyHex: String?
)

data class EncryptedMessagePacket(
    val packetId: String,
    val senderUserId: String,
    val recipientUserId: String,
    val dhEphemeralKeyHex: String,
    val previousChainLength: Int,
    val messageNumber: Int,
    val ciphertextHex: String,
    val ivHex: String,
    val timestamp: Long,
    val senderEphemeralKeyHex: String? = null,
    val oneTimePrekeyUsedHex: String? = null
)

// Friend request payloads
data class FriendRequestPayload(
    val fromUserId: String,
    val toUserId: String
)

data class FriendRequestAcceptPayload(
    val userId: String,
    val friendUserId: String
)

data class FriendRequestResponse(val success: Boolean)

data class FriendRequestItem(
    val fromUserId: String,
    val fromUsername: String? = null,
    val fromDisplayName: String? = null,
    val fromAvatarStyle: String? = null,
    val timestamp: Long = 0L
)

data class PingResponse(val status: String, val users: Int)

interface PhantomApiClient {
    @POST("/api/register")
    suspend fun registerUser(@Body payload: RegisterPayload): RegisterResponse

    @GET("/api/profile/{userId}")
    suspend fun getProfile(@Path("userId") userId: String): ProfilePayload

    @GET("/api/bundle/{userId}")
    suspend fun getPrekeyBundle(@Path("userId") userId: String): BundleResponse

    @GET("/api/search")
    suspend fun searchProfiles(@Query("q") query: String): List<ProfilePayload>

    @POST("/api/friend-request")
    suspend fun sendFriendRequest(@Body payload: FriendRequestPayload): FriendRequestResponse

    @GET("/api/friend-requests/{userId}")
    suspend fun getFriendRequests(@Path("userId") userId: String): List<FriendRequestItem>

    @POST("/api/friend-request/accept")
    suspend fun acceptFriendRequest(@Body payload: FriendRequestAcceptPayload): FriendRequestResponse

    @GET("/api/users")
    suspend fun getAllUsers(): List<ProfilePayload>

    @GET("/api/ping")
    suspend fun ping(): PingResponse
}
