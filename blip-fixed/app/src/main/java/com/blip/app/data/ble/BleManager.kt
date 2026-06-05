package com.blip.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.blip.app.data.model.BlipPacket
import com.blip.app.data.model.BlipUser
import com.blip.app.data.model.PacketType
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@SuppressLint("MissingPermission")
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val BLIP_SERVICE_UUID: UUID = UUID.fromString("12345678-0000-1000-8000-00805f9b34fb")
        val BLIP_CHAR_UUID: UUID    = UUID.fromString("12345679-0000-1000-8000-00805f9b34fb")
        val BLIP_DESC_UUID: UUID    = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        const val MAX_PACKET_SIZE   = 512 // BLE 5.0 extended MTU
        const val ADVERTISE_INTERVAL = 100 // ms
    }

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private val bleAdvertiser: BluetoothLeAdvertiser? get() = bluetoothAdapter?.bluetoothLeAdvertiser

    // Connected GATT clients (we are the peripheral)
    private val connectedClients = mutableMapOf<String, BluetoothDevice>()

    // Discovered nearby users
    private val _nearbyUsers = MutableStateFlow<List<BlipUser>>(emptyList())
    val nearbyUsers: StateFlow<List<BlipUser>> = _nearbyUsers

    // Incoming packet bus
    private val _incomingPackets = MutableStateFlow<BlipPacket?>(null)
    val incomingPackets: StateFlow<BlipPacket?> = _incomingPackets

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val gson = Gson()
    private var gattServer: BluetoothGattServer? = null
    private var localProfile: BlipUser? = null

    // ─── Advertising ─────────────────────────────────────────────────────────

    fun startAdvertising(user: BlipUser) {
        localProfile = user
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Pack userId + name into service data (max 27 bytes for advertising payload)
        val nameBytes = user.name.take(10).toByteArray()
        val serviceData = ByteArray(1 + nameBytes.size).also {
            it[0] = user.avatarColor.toByte()
            nameBytes.copyInto(it, 1)
        }

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BLIP_SERVICE_UUID))
            .addServiceData(ParcelUuid(BLIP_SERVICE_UUID), serviceData)
            .build()

        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        startGattServer()
    }

    fun stopAdvertising() {
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        _isAdvertising.value = false
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
        }
        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
        }
    }

    // ─── GATT Server (we receive connections) ────────────────────────────────

    private fun startGattServer() {
        val service = BluetoothGattService(BLIP_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            BLIP_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val descriptor = BluetoothGattDescriptor(
            BLIP_DESC_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)
        service.addCharacteristic(characteristic)

        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedClients[device.address] = device
            } else {
                connectedClients.remove(device.address)
                removeUserByAddress(device.address)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (characteristic.uuid == BLIP_CHAR_UUID) {
                parseIncomingPacket(value, device.address)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    // ─── Scanning ─────────────────────────────────────────────────────────────

    fun startScanning() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BLIP_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        bleScanner?.startScan(listOf(filter), settings, scanCallback)
        _isScanning.value = true
    }

    fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processScanResult(it) }
        }
    }

    private fun processScanResult(result: ScanResult) {
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(BLIP_SERVICE_UUID)) ?: return
        if (serviceData.isEmpty()) return
        val avatarColor = serviceData[0].toInt()
        val name = if (serviceData.size > 1) String(serviceData.copyOfRange(1, serviceData.size)) else "Unknown"
        val user = BlipUser(
            id = result.device.address,
            name = name,
            avatarColor = avatarColor,
            deviceAddress = result.device.address,
            rssi = result.rssi,
            lastSeen = System.currentTimeMillis()
        )
        val current = _nearbyUsers.value.toMutableList()
        val idx = current.indexOfFirst { it.deviceAddress == user.deviceAddress }
        if (idx >= 0) current[idx] = user else current.add(user)
        _nearbyUsers.value = current
    }

    // ─── Send Packet ──────────────────────────────────────────────────────────

    fun sendPacket(targetAddress: String, packet: BlipPacket) {
        val device = connectedClients[targetAddress] ?: return
        val data = serializePacket(packet)
        notifyDevice(device, data)
    }

    fun broadcastPacket(packet: BlipPacket) {
        val data = serializePacket(packet)
        connectedClients.values.forEach { device -> notifyDevice(device, data) }
    }

    private fun notifyDevice(device: BluetoothDevice, data: ByteArray) {
        val characteristic = gattServer
            ?.getService(BLIP_SERVICE_UUID)
            ?.getCharacteristic(BLIP_CHAR_UUID) ?: return
        characteristic.value = data
        gattServer?.notifyCharacteristicChanged(device, characteristic, false)
    }

    // ─── Packet Serialization ─────────────────────────────────────────────────

    private fun serializePacket(packet: BlipPacket): ByteArray {
        // Format: [type:1][senderId:36][recipientId:36][ttl:1][timestamp:8][payload]
        val senderBytes = packet.senderId.toByteArray()
        val recipientBytes = packet.recipientId.toByteArray()
        return byteArrayOf(packet.type.code) +
                senderBytes + byteArrayOf(0x00) +
                recipientBytes + byteArrayOf(0x00) +
                byteArrayOf(packet.ttl.toByte()) +
                longToBytes(packet.timestamp) +
                packet.payload
    }

    private fun parseIncomingPacket(data: ByteArray, senderAddress: String) {
        try {
            if (data.isEmpty()) return
            val type = PacketType.fromCode(data[0]) ?: return
            var idx = 1
            val senderEnd = data.drop(idx).indexOfFirst { it == 0x00.toByte() }.let { if (it == -1) -1 else it + idx }
            val senderId = String(data.copyOfRange(idx, senderEnd))
            idx = senderEnd + 1
            val recipientEnd = data.drop(idx).indexOfFirst { it == 0x00.toByte() }.let { if (it == -1) -1 else it + idx }
            val recipientId = String(data.copyOfRange(idx, recipientEnd))
            idx = recipientEnd + 1
            val ttl = data[idx++].toInt()
            val timestamp = bytesToLong(data.copyOfRange(idx, idx + 8))
            idx += 8
            val payload = data.copyOfRange(idx, data.size)
            _incomingPackets.value = BlipPacket(type, senderId, recipientId, payload, timestamp, ttl)
        } catch (e: Exception) {
            // malformed packet — ignore
        }
    }

    private fun removeUserByAddress(address: String) {
        _nearbyUsers.value = _nearbyUsers.value.filter { it.deviceAddress != address }
    }

    private fun longToBytes(v: Long): ByteArray =
        ByteArray(8) { i -> ((v shr ((7 - i) * 8)) and 0xFF).toByte() }

    private fun bytesToLong(b: ByteArray): Long =
        b.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
}
