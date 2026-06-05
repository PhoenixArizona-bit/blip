package com.blip.app.data.storage

import androidx.room.*
import com.blip.app.data.model.BlipMessage
import com.blip.app.data.model.Conversation
import com.blip.app.data.model.MessageStatus
import com.blip.app.data.model.MessageType
import kotlinx.coroutines.flow.Flow

// ─── Type Converters ──────────────────────────────────────────────────────────

class BlipConverters {
    @TypeConverter fun fromMessageType(v: MessageType) = v.name
    @TypeConverter fun toMessageType(v: String) = MessageType.valueOf(v)
    @TypeConverter fun fromMessageStatus(v: MessageStatus) = v.name
    @TypeConverter fun toMessageStatus(v: String) = MessageStatus.valueOf(v)
}

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<BlipMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: BlipMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<BlipMessage>)

    @Update
    suspend fun updateMessage(message: BlipMessage)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: MessageStatus)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteConversation(chatId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isIncoming = 1 AND status != 'DELIVERED'")
    fun getUnreadCount(chatId: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(chatId: String): BlipMessage?
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY lastMessageTime DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation)

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun getConversationByPeer(peerId: String): Conversation?

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun markAsRead(id: String)
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [BlipMessage::class, Conversation::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(BlipConverters::class)
abstract class BlipDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
}
