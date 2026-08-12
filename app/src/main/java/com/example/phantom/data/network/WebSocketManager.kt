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

object WebSocketManager {
    private var socket: Socket? = null
    private val _messageFlow = MutableSharedFlow<EncryptedMessagePacket>(extraBufferCapacity = 50)
    val messageFlow: SharedFlow<EncryptedMessagePacket> = _messageFlow

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val packetAdapter = moshi.adapter(EncryptedMessagePacket::class.java)

    // Replace with Render backend URL once deployed, using localhost (10.0.2.2 for emulator) for now
    private const val SERVER_URL = "http://10.0.2.2:3000"

    fun connect(userId: String) {
        if (socket?.connected() == true) return

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
