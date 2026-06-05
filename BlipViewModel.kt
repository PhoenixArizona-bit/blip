package com.blip.app

import android.content.Context
import androidx.room.Room
import com.blip.app.data.storage.BlipDatabase
import com.blip.app.data.storage.MessageDao
import com.blip.app.data.storage.ConversationDao
import com.blip.app.data.storage.ConnectionRequestDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): BlipDatabase =
        Room.databaseBuilder(ctx, BlipDatabase::class.java, "blip.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMessageDao(db: BlipDatabase): MessageDao = db.messageDao()
    @Provides fun provideConversationDao(db: BlipDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideConnectionRequestDao(db: BlipDatabase): ConnectionRequestDao = db.connectionRequestDao()
}
