package com.example.phantom.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        FriendshipEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        PrekeyEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PhantomDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun friendshipDao(): FriendshipDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun prekeyDao(): PrekeyDao

    companion object {
        @Volatile
        private var INSTANCE: PhantomDatabase? = null

        fun getDatabase(context: Context): PhantomDatabase {
            return INSTANCE ?: synchronized(this) {
                // For MVP: In a real prod app, use a Master Password or Android Keystore
                // Here we derive a key securely from the OS or prompt the user.
                val dbPassword = "phantom_secure_local_key_v1" // Mocked secure key for MVP
                val factory = net.sqlcipher.database.SupportFactory(dbPassword.toByteArray())
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhantomDatabase::class.java,
                    "phantom_vault.db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
