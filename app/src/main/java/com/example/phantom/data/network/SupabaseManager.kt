package com.example.phantom.data.network

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ProfilePayload(
    @SerialName("user_id") val userId: String,
    @SerialName("username") val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_style") val avatarStyle: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("identity_public_key_hex") val identityPublicKeyHex: String? = null
)

@Serializable
data class PrekeyBundlePayload(
    @SerialName("user_id") val userId: String,
    @SerialName("signed_prekey_public_hex") val signedPrekeyPublicHex: String,
    @SerialName("signed_prekey_signature_hex") val signedPrekeySignatureHex: String,
    @SerialName("one_time_prekeys_hex") val oneTimePrekeysHex: List<String>
)

@Serializable
data class RegisterPayload(
    val profile: ProfilePayload,
    val signedPrekeyPublicHex: String,
    val signedPrekeySignatureHex: String,
    val oneTimePrekeysHex: List<String>
)

@Serializable
data class PrekeyBundleResponse(
    val userId: String,
    val identityPublicKeyHex: String,
    val signedPrekeyPublicHex: String,
    val signedPrekeySignatureHex: String,
    val oneTimePrekeyHex: String
)

@Serializable
data class FriendRequestPayload(
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    @SerialName("status") val status: String = "PENDING"
)

@Serializable
data class FriendRequestAcceptPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("friend_user_id") val friendUserId: String
)

@Serializable
data class EncryptedMessagePacket(
    @SerialName("packet_id") val packetId: String,
    @SerialName("sender_user_id") val senderUserId: String,
    @SerialName("recipient_user_id") val recipientUserId: String,
    @SerialName("dh_ephemeral_key_hex") val dhEphemeralKeyHex: String,
    @SerialName("previous_chain_length") val previousChainLength: Int,
    @SerialName("message_number") val messageNumber: Int,
    @SerialName("ciphertext_hex") val ciphertextHex: String,
    @SerialName("iv_hex") val ivHex: String,
    @SerialName("created_at") val timestamp: Long,
    @SerialName("sender_ephemeral_key_hex") val senderEphemeralKeyHex: String? = null,
    @SerialName("one_time_prekey_used_hex") val oneTimePrekeyUsedHex: String? = null
)



@Singleton
class SupabaseManager @Inject constructor() {
    // Retrieve from BuildConfig (populated by Secrets Gradle Plugin from .env)
    private val SUPABASE_URL = try { com.example.BuildConfig.SUPABASE_URL } catch (e: Exception) { "YOUR_SUPABASE_URL" }
    private val SUPABASE_ANON_KEY = try { com.example.BuildConfig.SUPABASE_ANON_KEY } catch (e: Exception) { "YOUR_SUPABASE_ANON_KEY" }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            val isTest = try {
                Class.forName("org.robolectric.Robolectric")
                true
            } catch (e: Exception) {
                false
            }
            
            if (!isTest) {
                install(Auth)
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    suspend fun disconnectRealtime() {
        try {
            client.realtime.disconnect()
        } catch (e: Exception) {
            Log.w("SupabaseManager", "Failed to disconnect realtime", e)
        }
    }

    private suspend fun <T> withRetry(times: Int = 3, initialDelay: Long = 1000, block: suspend () -> T): T {
        var currentDelay = initialDelay
        for (i in 1 until times) {
            try {
                return block()
            } catch (e: Exception) {
                Log.w("SupabaseManager", "Attempt $i failed, retrying in $currentDelay ms", e)
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return block() // Last attempt throws if it fails
    }

    suspend fun registerUser(payload: RegisterPayload) {
        withRetry {
            try {
                client.postgrest["profiles"].upsert(payload.profile)
                
                // Map to Supabase table schema for prekey_bundles
                val prekeyBundleData = PrekeyBundlePayload(
                    userId = payload.profile.userId,
                    signedPrekeyPublicHex = payload.signedPrekeyPublicHex,
                    signedPrekeySignatureHex = payload.signedPrekeySignatureHex,
                    oneTimePrekeysHex = payload.oneTimePrekeysHex
                )
                client.postgrest["prekey_bundles"].upsert(prekeyBundleData)
            } catch (e: Exception) {
                Log.e("SupabaseManager", "Failed to register user", e)
                throw e
            }
        }
    }

    suspend fun signInWithGoogleIdToken(idTokenStr: String) {
        try {
            client.auth.signInWith(IDToken) {
                idToken = idTokenStr
                provider = Google
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to sign in with Google", e)
            throw e
        }
    }

    suspend fun getProfile(userId: String): ProfilePayload? {
        return try {
            client.postgrest["profiles"].select {
                filter { eq("user_id", userId) }
            }.decodeSingleOrNull<ProfilePayload>()
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to get profile", e)
            null
        }
    }

    suspend fun searchProfiles(query: String): List<ProfilePayload> {
        return try {
            client.postgrest["profiles"].select {
                filter { ilike("username", "%$query%") }
            }.decodeList<ProfilePayload>()
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to search profiles", e)
            emptyList()
        }
    }

    suspend fun sendFriendRequest(payload: FriendRequestPayload) {
        withRetry {
            client.postgrest["friend_requests"].insert(payload)
        }
    }

    suspend fun acceptFriendRequest(payload: FriendRequestAcceptPayload) {
        val updateData = mapOf("status" to "ACCEPTED")
        withRetry {
            client.postgrest["friend_requests"].update(updateData) {
                filter {
                    eq("from_user_id", payload.friendUserId)
                    eq("to_user_id", payload.userId)
                }
            }
        }
    }

    suspend fun getPrekeyBundle(userId: String): PrekeyBundleResponse? {
        val profile = getProfile(userId) ?: return null
        val bundlePayload = try {
            client.postgrest["prekey_bundles"].select {
                filter { eq("user_id", userId) }
            }.decodeSingleOrNull<PrekeyBundlePayload>()
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to get prekey bundle", e)
            null
        } ?: return null

        val opkList = bundlePayload.oneTimePrekeysHex
        val opk = opkList.firstOrNull() ?: ""

        // Ideally, remove the used OPK from the list in the database
        if (opk.isNotEmpty()) {
            val newList = opkList.drop(1)
            client.postgrest["prekey_bundles"].update(mapOf("one_time_prekeys_hex" to newList)) {
                filter { eq("user_id", userId) }
            }
        }

        return PrekeyBundleResponse(
            userId = userId,
            identityPublicKeyHex = profile.identityPublicKeyHex ?: "",
            signedPrekeyPublicHex = bundlePayload.signedPrekeyPublicHex,
            signedPrekeySignatureHex = bundlePayload.signedPrekeySignatureHex,
            oneTimePrekeyHex = opk
        )
    }

    suspend fun sendMessage(packet: EncryptedMessagePacket) {
        withRetry {
            client.postgrest["messages"].insert(packet)
        }
    }

    suspend fun deleteMessage(packetId: String) {
        try {
            client.postgrest["messages"].delete {
                filter { eq("packet_id", packetId) }
            }
        } catch (e: Exception) {
            Log.w("SupabaseManager", "Failed to delete message $packetId", e)
        }
    }

    suspend fun deleteMessages(packetIds: List<String>) {
        if (packetIds.isEmpty()) return
        for (packetId in packetIds) {
            deleteMessage(packetId)
        }
    }

    suspend fun observeMessages(userId: String): Flow<EncryptedMessagePacket> {
        val channelId = "messages-$userId"
        
        val channel = client.realtime.channel(channelId)
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("recipient_user_id", FilterOperator.EQ, userId)
        }.mapNotNull { action ->
            try {
                action.decodeRecord<EncryptedMessagePacket>()
            } catch (e: Exception) {
                Log.e("SupabaseManager", "Failed to decode realtime message", e)
                null
            }
        }
        
        try {
            client.realtime.connect()
            channel.subscribe()
            Log.d("SupabaseManager", "Subscribed to realtime messages for $userId (channel=$channelId)")
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to subscribe to realtime messages", e)
        }
        return flow
    }

    suspend fun getMessages(userId: String): List<EncryptedMessagePacket> {
        return try {
            client.postgrest["messages"].select {
                filter { eq("recipient_user_id", userId) }
            }.decodeList<EncryptedMessagePacket>()
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to fetch messages", e)
            emptyList()
        }
    }

    suspend fun observeFriendRequests(userId: String): Flow<FriendRequestPayload> {
        val channel = client.realtime.channel("public:friend_requests:to_user_id=eq.$userId")
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "friend_requests"
            filter("to_user_id", FilterOperator.EQ, userId)
        }.mapNotNull { action ->
            try {
                action.decodeRecord<FriendRequestPayload>()
            } catch (e: Exception) {
                null
            }
        }
        client.realtime.connect()
        channel.subscribe()
        return flow
    }

    suspend fun observeFriendAccepted(userId: String): Flow<FriendRequestAcceptPayload> {
        val channel = client.realtime.channel("public:friend_requests:from_user_id=eq.$userId")
        val flow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "friend_requests"
            filter("from_user_id", FilterOperator.EQ, userId)
        }.mapNotNull { action ->
            try {
                val record = action.decodeRecord<Map<String, String>>()
                if (record["status"] == "ACCEPTED") {
                    FriendRequestAcceptPayload(
                        userId = record["from_user_id"] ?: "",
                        friendUserId = record["to_user_id"] ?: ""
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        client.realtime.connect()
        channel.subscribe()
        return flow
    }

    suspend fun getFriendRequests(userId: String): List<FriendRequestPayload> {
        return try {
            client.postgrest["friend_requests"].select {
                filter { 
                    eq("to_user_id", userId)
                    eq("status", "PENDING")
                }
            }.decodeList<FriendRequestPayload>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun uploadMedia(bytes: ByteArray, mimeType: String): String? {
        return try {
            val bucket = client.storage["phantom-media"]
            val fileName = "media_${java.util.UUID.randomUUID()}"
            val extension = when {
                mimeType.contains("image") -> ".jpg"
                mimeType.contains("video") -> ".mp4"
                mimeType.contains("audio") -> ".mp3"
                else -> ""
            }
            bucket.upload(
                path = fileName + extension,
                data = bytes
            ) {
                upsert = false
            }
            bucket.publicUrl(fileName + extension)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Failed to upload media", e)
            null
        }
    }
}
