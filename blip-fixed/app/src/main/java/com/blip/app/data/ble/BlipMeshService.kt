package com.blip.app.data.ble

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.blip.app.MainActivity
import com.blip.app.R
import com.blip.app.data.mesh.MeshRouter
import com.blip.app.data.model.BlipUser
import com.blip.app.data.storage.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class BlipMeshService : Service() {

    @Inject lateinit var bleManager: BleManager
    @Inject lateinit var meshRouter: MeshRouter
    @Inject lateinit var userPreferences: UserPreferences

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "blip_mesh_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.blip.START_MESH"
        const val ACTION_STOP  = "com.blip.STOP_MESH"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopMesh(); stopSelf(); return START_NOT_STICKY }
            else -> startMesh()
        }
        startForeground(NOTIF_ID, buildNotification("Blip mesh active — discovering nearby users"))
        return START_STICKY
    }

    private fun startMesh() {
        scope.launch {
            val userId    = userPreferences.userId.first()
            val userName  = userPreferences.userName.first() ?: return@launch
            val color     = userPreferences.avatarColor.first()
            val stealth   = userPreferences.stealthMode.first()

            meshRouter.setLocalUserId(userId)

            val localUser = BlipUser(
                id = userId,
                name = userName,
                avatarColor = color,
                deviceAddress = userId,
                isStealthMode = stealth
            )

            if (!stealth) {
                bleManager.startAdvertising(localUser)
            }
            bleManager.startScanning()
        }
    }

    private fun stopMesh() {
        bleManager.stopAdvertising()
        bleManager.stopScanning()
        scope.cancel()
    }

    override fun onDestroy() {
        stopMesh()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Blip Mesh Network",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Blip mesh network active in background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blip")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_blip_notif)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
