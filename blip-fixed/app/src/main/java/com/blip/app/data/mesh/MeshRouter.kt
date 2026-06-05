package com.blip.app.data.mesh

import com.blip.app.data.ble.BleManager
import com.blip.app.data.model.BlipPacket
import com.blip.app.data.model.MeshNode
import com.blip.app.data.model.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshRouter @Inject constructor(
    private val bleManager: BleManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Routing table: destinationId -> nextHopAddress
    private val routingTable = mutableMapOf<String, String>()

    // Seen packet IDs (prevent loops)
    private val seenPacketIds = mutableSetOf<String>()
    private val MAX_SEEN_CACHE = 200

    private val _meshNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val meshNodes: StateFlow<List<MeshNode>> = _meshNodes

    private var localUserId: String = ""

    init {
        scope.launch {
            bleManager.incomingPackets.collect { packet ->
                packet?.let { handleIncomingPacket(it) }
            }
        }
        scope.launch {
            bleManager.nearbyUsers.collect { users ->
                updateMeshTopology(users.map { user ->
                    MeshNode(
                        userId = user.id,
                        userName = user.name,
                        avatarColor = user.avatarColor,
                        deviceAddress = user.deviceAddress,
                        rssi = user.rssi,
                        hopsFromSelf = user.meshHops
                    )
                })
            }
        }
    }

    fun setLocalUserId(id: String) { localUserId = id }

    // ─── Routing Logic ────────────────────────────────────────────────────────

    fun sendToUser(destinationId: String, packet: BlipPacket) {
        val nextHop = routingTable[destinationId]
        if (nextHop != null) {
            bleManager.sendPacket(nextHop, packet)
        } else {
            // Flood to all known direct peers
            bleManager.broadcastPacket(packet.copy(type = PacketType.MESH_RELAY))
        }
    }

    private fun handleIncomingPacket(packet: BlipPacket) {
        val packetKey = "${packet.senderId}:${packet.timestamp}"

        // Loop prevention
        if (seenPacketIds.contains(packetKey)) return
        seenPacketIds.add(packetKey)
        if (seenPacketIds.size > MAX_SEEN_CACHE) {
            seenPacketIds.remove(seenPacketIds.first())
        }

        // Learn the route: this sender is reachable via the device that sent us this
        // (The BLE manager provides the source address in the packet senderId)
        routingTable[packet.senderId] = packet.senderId

        if (packet.recipientId == localUserId || packet.recipientId == "BROADCAST") {
            // This is for us — deliver upward (BleManager already emits it)
            return
        }

        // Relay if TTL allows
        if (packet.ttl > 1) {
            val relayed = packet.copy(ttl = packet.ttl - 1)
            val nextHop = routingTable[packet.recipientId]
            if (nextHop != null) {
                bleManager.sendPacket(nextHop, relayed)
            } else {
                bleManager.broadcastPacket(relayed)
            }
        }
    }

    private fun updateMeshTopology(nodes: List<MeshNode>) {
        _meshNodes.value = nodes
        // Update routing table from direct neighbors
        nodes.forEach { node ->
            if (node.hopsFromSelf == 0) {
                routingTable[node.userId] = node.deviceAddress
            }
        }
    }

    fun getMeshStats(): MeshStats = MeshStats(
        totalNodes = _meshNodes.value.size,
        directPeers = _meshNodes.value.count { it.hopsFromSelf == 0 },
        relayedPeers = _meshNodes.value.count { it.hopsFromSelf > 0 },
        routeCount = routingTable.size
    )
}

data class MeshStats(
    val totalNodes: Int,
    val directPeers: Int,
    val relayedPeers: Int,
    val routeCount: Int
)
