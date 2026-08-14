package com.example.phantom.data.network

import android.util.Log
import com.example.phantom.data.network.EncryptedMessagePacket
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.URI

data class FriendRequestEvent(
    val fromUserId: String,
    val fromUsername: String? = null,
    val fromDisplayName: String? = null,
    val fromAvatarStyle: String? = null,
    val timestamp: Long = 0L
)

data class FriendRequestAcceptedEvent(
    val acceptedByUserId: String,
    val acceptedByUsername: String? = null,
    val acceptedByDisplayName: String? = null,
    val acceptedByAvatarStyle: String? = null
)

object WebSocketManager {
    private var socket: Socket? = null
    private val _messageFlow = MutableSharedFlow<EncryptedMessagePacket>(extraBufferCapacity = 50)
    val messageFlow: SharedFlow<EncryptedMessagePacket> = _messageFlow

    private val _friendRequestFlow = MutableSharedFlow<FriendRequestEvent>(extraBufferCapacity = 20)
    val friendRequestFlow: SharedFlow<FriendRequestEvent> = _friendRequestFlow

    private val _friendAcceptedFlow = MutableSharedFlow<FriendRequestAcceptedEvent>(extraBufferCapacity = 20)
    val friendAcceptedFlow: SharedFlow<FriendRequestAcceptedEvent> = _friendAcceptedFlow

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val packetAdapter = moshi.adapter(EncryptedMessagePacket::class.java)
    private val friendRequestAdapter = moshi.adapter(FriendRequestEvent::class.java)
    private val friendAcceptedAdapter = moshi.adapter(FriendRequestAcceptedEvent::class.java)

    private const val SERVER_URL = "https://phantom-relay-jvm2.onrender.com"

    fun connect(userId: String) {
        // Disconnect existing socket before reconnecting with new userId
        if (socket != null) {
            socket?.disconnect()
            socket = null
        }

        try {
            val options = IO.Options.builder()
                .setTransports(arrayOf("websocket"))
                .build()

            socket = IO.socket(URI.create(SERVER_URL), options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("WebSocketManager", "Connected to Relay Server")
                socket?.emit("authenticate", userId)
            }

            socket?.on("receive_message") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val jsonStr = args[0].toString()
                        val packet = packetAdapter.fromJson(jsonStr)
                        if (packet != null) {
                            _messageFlow.tryEmit(packet)
                        }
                    } catch (e: Exception) {
                        Log.e("WebSocketManager", "Failed to parse incoming packet", e)
                    }
                }
            }

            socket?.on("friend_request_received") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val jsonStr = args[0].toString()
                        val event = friendRequestAdapter.fromJson(jsonStr)
                        if (event != null) {
                            Log.d("WebSocketManager", "Friend request from: ${event.fromUserId}")
                            _friendRequestFlow.tryEmit(event)
                        }
                    } catch (e: Exception) {
                        Log.e("WebSocketManager", "Failed to parse friend request event", e)
                    }
                }
            }

            socket?.on("friend_request_accepted") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val jsonStr = args[0].toString()
                        val event = friendAcceptedAdapter.fromJson(jsonStr)
                        if (event != null) {
                            Log.d("WebSocketManager", "Friend request accepted by: ${event.acceptedByUserId}")
                            _friendAcceptedFlow.tryEmit(event)
                        }
                    } catch (e: Exception) {
                        Log.e("WebSocketManager", "Failed to parse friend accepted event", e)
                    }
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("WebSocketManager", "Disconnected from Relay Server")
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("WebSocketManager", "Socket connection error", e)
        }
    }

    fun sendMessage(packet: EncryptedMessagePacket) {
        try {
            val jsonStr = packetAdapter.toJson(packet)
            // Socket.IO sends string payload or JSONObject
            // passing JSON string, backend will route it
            socket?.emit("send_message", org.json.JSONObject(jsonStr))
        } catch (e: Exception) {
            Log.e("WebSocketManager", "Failed to send packet", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}

