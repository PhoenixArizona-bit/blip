package com.blip.app.viewmodel

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blip.app.data.ble.BleManager
import com.blip.app.data.ble.BlipMeshService
import com.blip.app.data.crypto.BlipCrypto
import com.blip.app.data.mesh.MeshRouter
import com.blip.app.data.model.*
import com.blip.app.data.storage.ConversationDao
import com.blip.app.data.storage.MessageDao
import com.blip.app.data.storage.UserPreferences
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BlipViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleManager: BleManager,
    private val meshRouter: MeshRouter,
    private val crypto: BlipCrypto,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val nearbyUsers = bleManager.nearbyUsers
    val meshNodes   = meshRouter.meshNodes
    val isAdvertising = bleManager.isAdvertising
    val isScanning    = bleManager.isScanning

    val conversations = conversationDao.getAllConversations()
    val userName      = userPreferences.userName
    val avatarColor   = userPreferences.avatarColor
    val stealthMode   = userPreferences.stealthMode
    val isOnboarded   = userPreferences.isOnboarded

    private val _localUserId = MutableStateFlow("")
    val localUserId: StateFlow<String> = _localUserId

    private val _activeChat = MutableStateFlow<String?>(null)

    private val gson = Gson()
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    init {
        viewModelScope.launch {
            _localUserId.value = userPreferences.ensureUserId()
            userPreferences.userId.collect { _localUserId.value = it }
        }
        viewModelScope.launch {
            bleManager.incomingPackets.collect { packet ->
                packet?.let { handleIncomingPacket(it) }
            }
        }
    }

    // ─── Onboarding ───────────────────────────────────────────────────────────

    fun completeOnboarding(name: String, color: Int) {
        viewModelScope.launch {
            userPreferences.saveUserName(name)
            userPreferences.saveAvatarColor(color)
            userPreferences.setOnboarded()
        }
    }

    // ─── Mesh Control ─────────────────────────────────────────────────────────

    fun startMesh() {
        Intent(context, BlipMeshService::class.java).also {
            it.action = BlipMeshService.ACTION_START
            context.startForegroundService(it)
        }
    }

    fun stopMesh() {
        Intent(context, BlipMeshService::class.java).also {
            it.action = BlipMeshService.ACTION_STOP
            context.startService(it)
        }
    }

    // ─── Messaging ────────────────────────────────────────────────────────────

    fun getMessages(chatId: String) = messageDao.getMessages(chatId)

    fun sendTextMessage(chatId: String, peerId: String, peerName: String, text: String) {
        viewModelScope.launch {
            val userId = _localUserId.value
            val userName = userPreferences.userName.first() ?: "Me"
            val msg = BlipMessage(
                chatId = chatId,
                senderId = userId,
                senderName = userName,
                type = MessageType.TEXT,
                content = text,
                status = MessageStatus.SENDING
            )
            messageDao.insertMessage(msg)
            updateConversationLastMessage(chatId, peerId, peerName, text)

            val payload = text.toByteArray()
            val packet = BlipPacket(
                type = PacketType.MESSAGE,
                senderId = userId,
                recipientId = peerId,
                payload = payload
            )
            meshRouter.sendToUser(peerId, packet)
            messageDao.updateStatus(msg.id, MessageStatus.SENT)
        }
    }

    fun sendLocationMessage(chatId: String, peerId: String, peerName: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            val userId = _localUserId.value
            val userName = userPreferences.userName.first() ?: "Me"
            val locationJson = gson.toJson(mapOf("lat" to lat, "lng" to lng))
            val msg = BlipMessage(
                chatId = chatId, senderId = userId, senderName = userName,
                type = MessageType.LOCATION, content = locationJson
            )
            messageDao.insertMessage(msg)
            updateConversationLastMessage(chatId, peerId, peerName, "📍 Location")

            val packet = BlipPacket(
                type = PacketType.LOCATION, senderId = userId,
                recipientId = peerId, payload = locationJson.toByteArray()
            )
            meshRouter.sendToUser(peerId, packet)
        }
    }

    // ─── Voice Recording ──────────────────────────────────────────────────────

    fun startVoiceRecording() {
        currentRecordingFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentRecordingFile!!.absolutePath)
            prepare()
            start()
        }
    }

    fun stopVoiceRecording(chatId: String, peerId: String, peerName: String) {
        viewModelScope.launch {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            val file = currentRecordingFile ?: return@launch
            val userId = _localUserId.value
            val userName = userPreferences.userName.first() ?: "Me"

            // Chunked send of voice file
            val bytes = file.readBytes()
            val chunkSize = BleManager.MAX_PACKET_SIZE - 64
            val totalChunks = (bytes.size + chunkSize - 1) / chunkSize
            val transferId = UUID.randomUUID().toString()

            val msg = BlipMessage(
                chatId = chatId, senderId = userId, senderName = userName,
                type = MessageType.VOICE,
                content = file.absolutePath,
                totalChunks = totalChunks,
                transferId = transferId
            )
            messageDao.insertMessage(msg)
            updateConversationLastMessage(chatId, peerId, peerName, "🎙 Voice message")

            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = minOf(start + chunkSize, bytes.size)
                val chunk = bytes.copyOfRange(start, end)
                val meta = "$transferId:$i:$totalChunks:".toByteArray()
                val packet = BlipPacket(
                    type = PacketType.VOICE_CHUNK,
                    senderId = userId, recipientId = peerId,
                    payload = meta + chunk
                )
                meshRouter.sendToUser(peerId, packet)
            }
        }
    }

    // ─── Incoming Packet Handler ──────────────────────────────────────────────

    private suspend fun handleIncomingPacket(packet: BlipPacket) {
        val userId = _localUserId.value
        if (packet.recipientId != userId && packet.recipientId != "BROADCAST") return

        when (packet.type) {
            PacketType.MESSAGE -> {
                val text = String(packet.payload)
                val chatId = packet.senderId  // DM chat id = peer's userId
                val msg = BlipMessage(
                    chatId = chatId, senderId = packet.senderId,
                    senderName = nearbyUsers.value.find { it.id == packet.senderId }?.name ?: packet.senderId,
                    type = MessageType.TEXT, content = text,
                    status = MessageStatus.DELIVERED, isIncoming = true
                )
                messageDao.insertMessage(msg)
                // Send ACK
                val ack = BlipPacket(
                    type = PacketType.MESSAGE_ACK, senderId = userId,
                    recipientId = packet.senderId, payload = msg.id.toByteArray()
                )
                meshRouter.sendToUser(packet.senderId, ack)
            }
            PacketType.MESSAGE_ACK -> {
                val msgId = String(packet.payload)
                messageDao.updateStatus(msgId, MessageStatus.DELIVERED)
            }
            PacketType.LOCATION -> {
                val locationJson = String(packet.payload)
                val msg = BlipMessage(
                    chatId = packet.senderId, senderId = packet.senderId,
                    senderName = nearbyUsers.value.find { it.id == packet.senderId }?.name ?: "Unknown",
                    type = MessageType.LOCATION, content = locationJson,
                    status = MessageStatus.DELIVERED, isIncoming = true
                )
                messageDao.insertMessage(msg)
            }
            PacketType.HELLO -> {
                // Profile exchange — handled by BleManager
            }
            else -> {}
        }
    }

    private suspend fun updateConversationLastMessage(
        chatId: String, peerId: String, peerName: String, preview: String
    ) {
        val existing = conversationDao.getConversationByPeer(peerId)
        val conv = existing?.copy(lastMessage = preview, lastMessageTime = System.currentTimeMillis())
            ?: Conversation(
                id = chatId, peerId = peerId, peerName = peerName,
                peerAvatarColor = nearbyUsers.value.find { it.id == peerId }?.avatarColor ?: 0xFF2979FF.toInt(),
                lastMessage = preview, lastMessageTime = System.currentTimeMillis()
            )
        conversationDao.insertConversation(conv)
    }

    // ─── Profile ──────────────────────────────────────────────────────────────

    fun updateName(name: String) = viewModelScope.launch { userPreferences.saveUserName(name) }
    fun updateAvatarColor(color: Int) = viewModelScope.launch { userPreferences.saveAvatarColor(color) }
    fun toggleStealthMode(on: Boolean) = viewModelScope.launch { userPreferences.setStealthMode(on) }

    fun getMeshStats() = meshRouter.getMeshStats()
}
