package com.example.phantom.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isCurrentLocalUser = 1 LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isCurrentLocalUser = 1 LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("UPDATE users SET isCurrentLocalUser = 0")
    suspend fun clearActiveUserFlag()

    @Query("UPDATE users SET isCurrentLocalUser = 1 WHERE userId = :userId")
    suspend fun setActiveUser(userId: String)
}

@Dao
interface FriendshipDao {
    @Query("SELECT * FROM friendships WHERE localUserId = :localUserId ORDER BY id DESC")
    fun getFriendshipsFlow(localUserId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE localUserId = :localUserId AND status = 'ACCEPTED'")
    fun getAcceptedFriendsFlow(localUserId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE localUserId = :localUserId AND friendUserId = :friendUserId LIMIT 1")
    suspend fun getFriendship(localUserId: String, friendUserId: String): FriendshipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendship(friendship: FriendshipEntity)

    @Query("UPDATE friendships SET status = :status WHERE localUserId = :localUserId AND friendUserId = :friendUserId")
    suspend fun updateStatus(localUserId: String, friendUserId: String, status: String)

    @Query("UPDATE friendships SET isVerifiedKey = :isVerified WHERE localUserId = :localUserId AND friendUserId = :friendUserId")
    suspend fun setKeyVerified(localUserId: String, friendUserId: String, isVerified: Boolean)

    @Query("DELETE FROM friendships WHERE localUserId = :localUserId AND friendUserId = :friendUserId")
    suspend fun deleteFriendship(localUserId: String, friendUserId: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE localUserId = :localUserId AND contactUserId = :contactUserId LIMIT 1")
    suspend fun getSession(localUserId: String, contactUserId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE localUserId = :localUserId AND contactUserId = :contactUserId LIMIT 1")
    fun getSessionFlow(localUserId: String, contactUserId: String): Flow<SessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: SessionEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationUserId = :conversationUserId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationUserId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET isDelivered = 1 WHERE messageId = :messageId")
    suspend fun markDelivered(messageId: String)
}

@Dao
interface PrekeyDao {
    @Query("SELECT * FROM prekeys WHERE userId = :userId AND isUsed = 0 LIMIT 1")
    suspend fun getAvailablePrekey(userId: String): PrekeyEntity?

    @Query("SELECT * FROM prekeys WHERE publicKeyHex = :publicKeyHex LIMIT 1")
    suspend fun getPrekeyByPublicKey(publicKeyHex: String): PrekeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrekeys(prekeys: List<PrekeyEntity>)

    @Query("UPDATE prekeys SET isUsed = 1 WHERE prekeyId = :prekeyId")
    suspend fun markUsed(prekeyId: String)
}
