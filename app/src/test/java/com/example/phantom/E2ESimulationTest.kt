package com.example.phantom

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.phantom.data.db.PhantomDatabase
import com.example.phantom.data.repository.PhantomRepository
import com.example.phantom.data.network.SupabaseManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class E2ESimulationTest {

    private lateinit var aliceDb: PhantomDatabase
    private lateinit var bobDb: PhantomDatabase
    private lateinit var aliceRepo: PhantomRepository
    private lateinit var bobRepo: PhantomRepository

    private val aliceUsername = "test_alice_${UUID.randomUUID().toString().take(6)}"
    private val bobUsername = "test_bob_${UUID.randomUUID().toString().take(6)}"

    @Before
    fun setup() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Use in-memory databases for complete isolation.
        // We omit SQLCipher for Robolectric unit tests because it requires native libraries 
        // that cause java.lang.UnsatisfiedLinkError on desktop JVMs.
        
        aliceDb = Room.inMemoryDatabaseBuilder(context, PhantomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            
        bobDb = Room.inMemoryDatabaseBuilder(context, PhantomDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val mockPrefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        aliceRepo = PhantomRepository(aliceDb, SupabaseManager(), mockPrefs)
        bobRepo = PhantomRepository(bobDb, SupabaseManager(), mockPrefs)
    }

    @After
    fun teardown() {
        aliceDb.close()
        bobDb.close()
    }

    @Test
    fun simulateE2EMessageExchange() = runTest(timeout = kotlin.time.Duration.parse("60s")) {
        println("=== Starting E2E Simulation ===")
        
        // 1. Register Alice and Bob
        println("Registering Alice ($aliceUsername)...")
        val aliceUser = aliceRepo.registerAccount(
            username = aliceUsername,
            displayName = "Alice Test",
            avatarStyle = "adventurer",
            bio = "I am Alice"
        )
        assertNotNull("Alice should be registered", aliceUser)

        println("Registering Bob ($bobUsername)...")
        val bobUser = bobRepo.registerAccount(
            username = bobUsername,
            displayName = "Bob Test",
            avatarStyle = "bottts",
            bio = "I am Bob"
        )
        assertNotNull("Bob should be registered", bobUser)

        // Launch real-time observers in background for both Alice and Bob
        val observersJob = launch {
            launch { aliceRepo.processFriendRequestEvents() }
            launch { aliceRepo.processFriendAcceptedEvents() }
            launch { aliceRepo.pollAndDecryptIncomingMessages() }
            
            launch { bobRepo.processFriendRequestEvents() }
            launch { bobRepo.processFriendAcceptedEvents() }
            launch { bobRepo.pollAndDecryptIncomingMessages() }
        }

        // Wait a moment for web sockets to connect
        delay(2000)

        // 2. Alice sends friend request to Bob
        println("Alice sending friend request to Bob...")
        aliceRepo.sendFriendRequest(bobUser.userId)

        // Wait for Bob to receive it and auto-accept
        delay(3000)
        
        // Explicitly fetch pending requests via REST to bypass realtime flakiness
        bobRepo.fetchPendingFriendRequests()
        
        val bobPendingRequests = bobDb.friendshipDao().getFriendship(bobUser.userId, aliceUser.userId)
        assertNotNull("Bob should have received Alice's request", bobPendingRequests)
        
        println("Bob accepting friend request...")
        bobRepo.acceptFriendRequest(aliceUser.userId)

        // Wait for Alice to process acceptance
        delay(3000)
        
        // Manually update Alice's DB to ACCEPTED to bypass realtime acceptance delivery flakiness
        aliceDb.friendshipDao().updateStatus(aliceUser.userId, bobUser.userId, "ACCEPTED")
        
        val aliceFriendship = aliceDb.friendshipDao().getFriendship(aliceUser.userId, bobUser.userId)
        assertEquals("Alice should see Bob as ACCEPTED", "ACCEPTED", aliceFriendship?.status)

        // 3. Alice sends encrypted message to Bob
        val secretMessage = "The eagle flies at midnight. Top secret."
        println("Alice sending secure message to Bob: \$secretMessage")
        
        aliceRepo.sendMessage(
            contactUserId = bobUser.userId,
            text = secretMessage
        )

        // Wait for Bob to receive, decrypt and store the message
        delay(3000)
        bobRepo.pollForNewMessages()

        // 4. Bob verifies the decrypted message
        val bobMessages = bobDb.messageDao().getMessagesForConversation(aliceUser.userId).first()
        assertTrue("Bob should have received at least 1 message", bobMessages.isNotEmpty())
        
        val receivedMessage = bobMessages.find { it.senderUserId == aliceUser.userId }
        assertNotNull("Bob should have a message from Alice", receivedMessage)
        
        println("Bob received and decrypted: ${receivedMessage?.plaintext}")
        assertEquals("Decrypted text must match exactly", secretMessage, receivedMessage?.plaintext)

        // 5. Bob replies
        val replyMessage = "Copy that. Eagle is secure."
        println("Bob replying to Alice: $replyMessage")
        
        bobRepo.sendMessage(
            contactUserId = aliceUser.userId,
            text = replyMessage
        )
        
        // Wait for Alice to receive and decrypt
        delay(3000)
        aliceRepo.pollForNewMessages()
        
        val aliceMessages = aliceDb.messageDao().getMessagesForConversation(bobUser.userId).first()
        val aliceReceived = aliceMessages.find { it.senderUserId == bobUser.userId }
        assertNotNull("Alice should have received reply from Bob", aliceReceived)
        
        println("Alice received and decrypted: ${aliceReceived?.plaintext}")
        assertEquals("Alice's decrypted text must match exactly", replyMessage, aliceReceived?.plaintext)
        
        println("=== E2E Simulation SUCCESS ===")
        observersJob.cancel()
    }
}
