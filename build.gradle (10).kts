package com.blip.app.viewmodel

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blip.app.data.ble.BleManager
import com.blip.app.data.ble.BlipMeshService
import com.blip.app.data.mesh.MeshRouter
import com.blip.app.data.model.*
import com.blip.app.data.storage.ConnectionRequestDao
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
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val connectionRequestDao: ConnectionRequestDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val nearbyUsers   = bleManager.nearbyUsers
    val meshNodes     = meshRouter.meshNodes
    val isAdvertising = bleManager.isAdvertising
    val isScanning    = bleManager.isScanning

    val conversations         = conversationDao.getAllConversations()
    val pendingConnectionReqs = connectionRequestDao.getPendingIncoming()

    val userName    = userPreferences.userName
    val avatarColor = userPreferences.avatarColor
    val stealthMode = userPreferences.stealthMode
    val isOnboarded = userPreferences.isOnboarded

    private val _localUserId = MutableStateFlow("")
    val localUserId: StateFlow<String> = _localUserId

    private val gson = Gson()
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var isRecorderPrepared = false

    init {
        viewModelScope.launch {
            val id = userPreferences.ensureUserId()
            _localUserId.value = id
            meshRouter.setLocalUserId(id)
            userPreferences.userId.collect {
                if (it.isNotEmpty()) {
                    _localUserId.value = it
                    meshRouter.setLocalUserId(it)
                }
            }
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

    // ─── Connection Request Flow ──────────────────────────────────────────────

    fun sendConnectionRequest(peerId: String, peerName: String, peerColor: Int) {
        viewModelScope.launch {
            val userId = _localUserId.value
            val userName = userPreferences.userName.first() ?: "Unknown"

            // Save outgoing request locally
            val req = ConnectionRequest(
                peerId = peerId,
                peerName = peerName,
                peerAvatarColor = peerColor,
                peerDeviceAddress = nearbyUsers.value.find { it.id == peerId }?.deviceAddress ?: "",
                isIncoming = false,
                status = RequestStatus.PENDING
            )
            connectionRequestDao.insert(req)

            // Mark connection state as handshake sent
            val peer = nearbyUsers.value.find { it.id == peerId }
            peer?.deviceAddress?.let {
                bleManager.updateUserConnectionState(it, ConnectionState.HANDSHAKE_SENT)
            }

            // Send packet to peer
            val payload = "$userId|$userName|$peerColor".toByteArray()
            val packet = BlipPacket(
                type = PacketType.CONNECTION_REQUEST,
                senderId = userId,
                recipientId = peerId,
                payload = payload
            )
            meshRouter.sendToUser(peerId, packet)
        }
    }

    fun acceptConnectionRequest(peerId: String) {
        viewModelScope.launch {
            val userId = _localUserId.value
            connectionRequestDao.updateStatus(peerId, RequestStatus.ACCEPTED)

            val peer = nearbyUsers.value.find { it.id == peerId }
            peer?.deviceAddress?.let {
                bleManager.updateUserConnectionState(it, ConnectionState.ACCEPTED)
            }

            val packet = BlipPacket(
                type = PacketType.CONNECTION_ACCEPT,
                senderId = userId,
                recipientId = peerId,
                payload = ByteArray(0)
            )
            meshRouter.sendToUser(peerId, packet)
        }
    }

    fun rejectConnectionRequest(peerId: String) {
        viewModelScope.launch {
            val userId = _localUserId.value
            connectionRequestDao.updateStatus(peerId, RequestStatus.REJECTED)

            val peer = nearbyUsers.value.find { it.id == peerId }
            peer?.deviceAddress?.let {
                bleManager.updateUserConnectionState(it, ConnectionState.REJECTED)
            }

            val packet = BlipPacket(
                type = PacketType.CONNECTION_REJECT,
                senderId = userId,
                recipientId = peerId,
                payload = ByteArray(0)
            )
            meshRouter.sendToUser(peerId, packet)
        }
    }

    fun isAccepted(peerId: String): Boolean {
        return nearbyUsers.value.find { it.id == peerId }?.connectionState == ConnectionState.ACCEPTED
    }

    // ─── Messaging ────────────────────────────────────────────────────────────

    fun getMessages(chatId: String) = messageDao.getMessages(chatId)

    fun sendTextMessage(chatId: String, peerId: String, peerName: String, text: String) {
        viewModelScope.launch {
            val userId   = _localUserId.value
            val uName    = userPreferences.userName.first() ?: "Me"
            val msg = BlipMessage(
                chatId = chatId, senderId = userId, senderName = uName,
                type = MessageType.TEXT, content = text, status = MessageStatus.SENDING
            )
            messageDao.insertMessage(msg)
            updateConversationLastMessage(chatId, peerId, peerName, text)

            // Status stays SENDING until we get a MESSAGE_ACK back from the peer.
            // Store the message ID so the ACK handler can look it up.
            val packet = BlipPacket(
                type = PacketType.MESSAGE, senderId = userId,
                recipientId = peerId,
                // Embed message id as first 36 chars so receiver can echo it in ACK
                payload = (msg.id + "|" + text).toByteArray()
            )
            meshRouter.sendToUser(peerId, packet)
            // Optimistically mark SENT once the packet is handed to the BLE layer.
            // It will be upgraded to DELIVERED when ACK arrives.
            messageDao.updateStatus(msg.id, MessageStatus.SENT)
        }
    }

    fun sendLocationMessage(chatId: String, peerId: String, peerName: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            val userId = _localUserId.value
            val uName  = userPreferences.userName.first() ?: "Me"
            val locationJson = gson.toJson(mapOf("lat" to lat, "lng" to lng))
            val msg = BlipMessage(
                chatId = chatId, senderId = userId, senderName = uName,
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
        try {
            stopVoiceRecordingCleanup()
            currentRecordingFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentRecordingFile!!.absolutePath)
                prepare()
                isRecorderPrepared = true
                start()
            }
        } catch (e: Exception) {
            stopVoiceRecordingCleanup()
        }
    }

    private fun stopVoiceRecordingCleanup() {
        try {
            if (isRecorderPrepared) {
                mediaRecorder?.stop()
            }
        } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        isRecorderPrepared = false
    }

    fun stopVoiceRecording(chatId: String, peerId: String, peerName: String) {
        viewModelScope.launch {
            try {
                if (!isRecorderPrepared) return@launch
                // Capture the file reference BEFORE cleanup nulls currentRecordingFile
                val file = currentRecordingFile ?: run { stopVoiceRecordingCleanup(); return@launch }
                stopVoiceRecordingCleanup()
                if (!file.exists() || file.length() == 0L) return@launch

                val userId = _localUserId.value
                val uName  = userPreferences.userName.first() ?: "Me"
                val bytes  = file.readBytes()
                val chunkSize   = BleManager.MAX_PACKET_SIZE - 64
                val totalChunks = (bytes.size + chunkSize - 1) / chunkSize
                val transferId  = UUID.randomUUID().toString()

                val msg = BlipMessage(
                    chatId = chatId, senderId = userId, senderName = uName,
                    type = MessageType.VOICE, content = file.absolutePath,
                    totalChunks = totalChunks, transferId = transferId
                )
                messageDao.insertMessage(msg)
                updateConversationLastMessage(chatId, peerId, peerName, "🎙 Voice message")

                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end   = minOf(start + chunkSize, bytes.size)
                    val chunk = bytes.copyOfRange(start, end)
                    val meta  = "$transferId:$i:$totalChunks:".toByteArray()
                    val packet = BlipPacket(
                        type = PacketType.VOICE_CHUNK, senderId = userId,
                        recipientId = peerId, payload = meta + chunk
                    )
                    meshRouter.sendToUser(peerId, packet)
                }
            } catch (_: Exception) {}
        }
    }

    // ─── Incoming Packet Handler ──────────────────────────────────────────────

    private suspend fun handleIncomingPacket(packet: BlipPacket) {
        val userId = _localUserId.value
        // Only handle packets addressed to us — relay packets are handled by MeshRouter
        if (packet.recipientId != userId && packet.recipientId != "BROADCAST") return
        // Ignore packets we ourselves sent (can happen when broadcast reaches our own GATT server)
        if (packet.senderId == userId) return

        when (packet.type) {
            PacketType.CONNECTION_REQUEST -> {
                val parts = String(packet.payload).split("|")
                val senderName  = parts.getOrNull(1) ?: packet.senderId
                val senderColor = parts.getOrNull(2)?.toIntOrNull() ?: 0xFF2979FF.toInt()
                val peerAddr = nearbyUsers.value.find { it.id == packet.senderId }?.deviceAddress ?: ""
                val req = ConnectionRequest(
                    peerId = packet.senderId,
                    peerName = senderName,
                    peerAvatarColor = senderColor,
                    peerDeviceAddress = peerAddr,
                    isIncoming = true,
                    status = RequestStatus.PENDING
                )
                connectionRequestDao.insert(req)
                bleManager.updateUserConnectionState(peerAddr, ConnectionState.HANDSHAKE_SENT)
            }

            PacketType.CONNECTION_ACCEPT -> {
                connectionRequestDao.updateStatus(packet.senderId, RequestStatus.ACCEPTED)
                val peerAddr = nearbyUsers.value.find { it.id == packet.senderId }?.deviceAddress ?: ""
                bleManager.updateUserConnectionState(peerAddr, ConnectionState.ACCEPTED)
            }

            PacketType.CONNECTION_REJECT -> {
                connectionRequestDao.updateStatus(packet.senderId, RequestStatus.REJECTED)
                val peerAddr = nearbyUsers.value.find { it.id == packet.senderId }?.deviceAddress ?: ""
                bleManager.updateUserConnectionState(peerAddr, ConnectionState.REJECTED)
            }

            PacketType.MESSAGE -> {
                val raw    = String(packet.payload)
                // Payload format: "<msgId>|<text>" (sender embeds id for ACK round-trip)
                val pipeIdx = raw.indexOf('|')
                val senderMsgId = if (pipeIdx > 0) raw.substring(0, pipeIdx) else null
                val text   = if (pipeIdx > 0) raw.substring(pipeIdx + 1) else raw
                val chatId = packet.senderId
                val senderName = nearbyUsers.value.find { it.id == packet.senderId }?.name ?: packet.senderId
                val msg = BlipMessage(
                    chatId = chatId, senderId = packet.senderId, senderName = senderName,
                    type = MessageType.TEXT, content = text,
                    status = MessageStatus.DELIVERED, isIncoming = true
                )
                messageDao.insertMessage(msg)
                updateConversationLastMessage(chatId, packet.senderId, senderName, text)

                // Echo the sender's original message id so they can mark it DELIVERED
                val ackPayload = (senderMsgId ?: msg.id).toByteArray()
                val ack = BlipPacket(
                    type = PacketType.MESSAGE_ACK, senderId = userId,
                    recipientId = packet.senderId, payload = ackPayload
                )
                meshRouter.sendToUser(packet.senderId, ack)
            }

            PacketType.MESSAGE_ACK -> {
                val msgId = String(packet.payload)
                messageDao.updateStatus(msgId, MessageStatus.DELIVERED)
            }

            PacketType.LOCATION -> {
                val locationJson = String(packet.payload)
                val senderName = nearbyUsers.value.find { it.id == packet.senderId }?.name ?: "Unknown"
                val msg = BlipMessage(
                    chatId = packet.senderId, senderId = packet.senderId, senderName = senderName,
                    type = MessageType.LOCATION, content = locationJson,
                    status = MessageStatus.DELIVERED, isIncoming = true
                )
                messageDao.insertMessage(msg)
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

    fun updateName(name: String)         = viewModelScope.launch { userPreferences.saveUserName(name) }
    fun updateAvatarColor(color: Int)    = viewModelScope.launch { userPreferences.saveAvatarColor(color) }
    fun toggleStealthMode(on: Boolean) = viewModelScope.launch {
        userPreferences.setStealthMode(on)
        // Apply immediately: restart advertising based on new state
        val userId   = userPreferences.userId.first()
        val userName = userPreferences.userName.first() ?: return@launch
        val color    = userPreferences.avatarColor.first()
        if (on) {
            // Stealth ON → stop advertising so we are invisible to new scanners
            bleManager.stopAdvertising()
        } else {
            // Stealth OFF → resume advertising
            val localUser = com.blip.app.data.model.BlipUser(
                id = userId, name = userName, avatarColor = color,
                deviceAddress = userId, isStealthMode = false
            )
            bleManager.startAdvertising(localUser)
        }
    }
    fun getMeshStats()                   = meshRouter.getMeshStats()
}
