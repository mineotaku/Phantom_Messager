package com.example.phantom.di

import android.app.Application
import com.example.phantom.data.db.PhantomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePhantomDatabase(app: Application): PhantomDatabase {
        return PhantomDatabase.getDatabase(app)
    }

}
