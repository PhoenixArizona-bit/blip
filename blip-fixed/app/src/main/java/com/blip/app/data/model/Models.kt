package com.blip.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// ─── User / Profile ──────────────────────────────────────────────────────────

data class BlipUser(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarColor: Int,       // ARGB int
    val deviceAddress: String,  // BLE MAC / advertising ID
    val rssi: Int = 0,          // signal strength
    val lastSeen: Long = System.currentTimeMillis(),
    val isStealthMode: Boolean = false,
    val meshHops: Int = 0       // 0 = direct, >0 = relayed
)

// ─── Messages ─────────────────────────────────────────────────────────────────

enum class MessageType { TEXT, IMAGE, FILE, VOICE, LOCATION, SYSTEM }
enum class MessageStatus { SENDING, SENT, DELIVERED, FAILED }

@Entity(tableName = "messages")
data class BlipMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val chatId: String,          // conversationId or groupId
    val senderId: String,
    val senderName: String,
    val type: MessageType = MessageType.TEXT,
    val content: String = "",    // text body OR base64 chunk OR JSON metadata
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENDING,
    val isIncoming: Boolean = false,
    val replyToId: String? = null,
    // For chunked transfers
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val transferId: String? = null
)

// ─── Conversations ────────────────────────────────────────────────────────────

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val peerId: String,          // userId for DM, groupId for group
    val peerName: String,
    val peerAvatarColor: Int,
    val isGroup: Boolean = false,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val memberIds: String = ""   // JSON array for groups
)

// ─── Location ─────────────────────────────────────────────────────────────────

data class BlipLocation(
    val userId: String,
    val userName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val isLive: Boolean = false
)

// ─── Mesh Network ─────────────────────────────────────────────────────────────

data class MeshNode(
    val userId: String,
    val userName: String,
    val avatarColor: Int,
    val deviceAddress: String,
    val connectedPeers: List<String> = emptyList(),
    val rssi: Int = 0,
    val hopsFromSelf: Int = 0
)

// ─── BLE Packet ───────────────────────────────────────────────────────────────

enum class PacketType(val code: Byte) {
    HELLO(0x01),
    MESSAGE(0x02),
    MESSAGE_ACK(0x03),
    FILE_CHUNK(0x04),
    FILE_ACK(0x05),
    LOCATION(0x06),
    VOICE_CHUNK(0x07),
    MESH_RELAY(0x08),
    PROFILE(0x09),
    PING(0x0A),
    PONG(0x0B);

    companion object {
        fun fromCode(code: Byte) = values().firstOrNull { it.code == code }
    }
}

data class BlipPacket(
    val type: PacketType,
    val senderId: String,
    val recipientId: String,    // "BROADCAST" for all
    val payload: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Int = 5            // mesh hop limit
)

// ─── File Transfer ────────────────────────────────────────────────────────────

data class FileTransfer(
    val transferId: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val totalSize: Long,
    val totalChunks: Int,
    val receivedChunks: MutableMap<Int, ByteArray> = mutableMapOf(),
    val isComplete: Boolean = false,
    val progress: Float = 0f
)
