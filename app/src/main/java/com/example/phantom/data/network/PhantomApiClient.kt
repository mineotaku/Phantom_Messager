package com.example.phantom.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ProfilePayload(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarStyle: String,
    val bio: String,
    val identityPublicKeyHex: String
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
    val timestamp: Long
)

interface PhantomApiClient {
    @POST("/api/register")
    suspend fun registerUser(@Body payload: RegisterPayload): RegisterResponse

    @GET("/api/profile/{userId}")
    suspend fun getProfile(@Path("userId") userId: String): ProfilePayload

    @GET("/api/bundle/{userId}")
    suspend fun getPrekeyBundle(@Path("userId") userId: String): BundleResponse

    @GET("/api/search")
    suspend fun searchProfiles(@Query("q") query: String): List<ProfilePayload>
}
