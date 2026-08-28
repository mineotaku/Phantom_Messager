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
    version = 6,
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
                // Generate or retrieve the encryption key securely
                val sharedPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
                    "phantom_secure_prefs",
                    androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC),
                    context.applicationContext,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                var dbPassword = sharedPreferences.getString("db_encryption_key", null)
                if (dbPassword == null) {
                    // Generate a new 256-bit key
                    val secureRandom = java.security.SecureRandom()
                    val keyBytes = ByteArray(32)
                    secureRandom.nextBytes(keyBytes)
                    dbPassword = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
                    
                    sharedPreferences.edit().putString("db_encryption_key", dbPassword).apply()
                }

                val finalDbPassword = dbPassword!!
                val factory = net.sqlcipher.database.SupportFactory(finalDbPassword.toByteArray())
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhantomDatabase::class.java,
                    "phantom_vault.db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(true)
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
