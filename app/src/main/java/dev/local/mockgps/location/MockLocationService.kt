package dev.local.mockgps.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import dev.local.mockgps.MainActivity
import dev.local.mockgps.R
import dev.local.mockgps.geo.Coordinates
import dev.local.mockgps.geo.CoordinateValidator
import dev.local.mockgps.geo.JitterConfig
import dev.local.mockgps.settings.SettingsRepository

class MockLocationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: SettingsRepository
    private var controller: MockLocationController? = null
    private var simulationActive = false
    private var baseCoordinates: Coordinates? = null
    private var jitterEnabled = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val publishLocation = object : Runnable {
        override fun run() {
            if (!simulationActive) return

            try {
                controller?.publishNext()
                handler.postDelayed(this, JitterConfig.UPDATE_INTERVAL_MILLIS)
            } catch (_: SecurityException) {
                stopSimulation(getString(R.string.error_mock_location))
            } catch (_: RuntimeException) {
                stopSimulation(getString(R.string.error_publish_location))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSimulation()
            ACTION_START -> startOrUpdateSimulation(intent)
            else -> stopSimulation(getString(R.string.error_service_command))
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(publishLocation)
        releaseWakeLock()
        if (simulationActive || controller != null) {
            val cleanupError = stopProvider()
            settings.setSimulationActive(false)
            simulationActive = false
            cleanupError?.let { settings.saveError(it) }
        }
        super.onDestroy()
    }

    private fun startOrUpdateSimulation(intent: Intent) {
        val coordinates = Coordinates(
            intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN),
            intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN),
        )
        if (!CoordinateValidator.isValid(coordinates)) {
            stopSimulation(getString(R.string.error_invalid_coordinates))
            return
        }

        val useJitter = intent.getBooleanExtra(EXTRA_JITTER_ENABLED, false)
        try {
            promoteToForeground(coordinates, useJitter)

            val locationController = controller ?: MockLocationController(this).also { controller = it }
            if (simulationActive) {
                locationController.updateBase(coordinates)
                locationController.setJitterEnabled(useJitter)
            } else {
                locationController.start(coordinates, useJitter)
            }

            baseCoordinates = coordinates
            jitterEnabled = useJitter
            simulationActive = true
            acquireWakeLock()
            handler.removeCallbacks(publishLocation)
            handler.postDelayed(publishLocation, JitterConfig.UPDATE_INTERVAL_MILLIS)
            settings.saveActiveSimulation(coordinates, useJitter)
            settings.saveError(null)
            updateNotification(coordinates, useJitter)
            sendStatus(STATUS_ACTIVE)
        } catch (_: SecurityException) {
            stopSimulation(getString(R.string.error_mock_location))
        } catch (_: RuntimeException) {
            stopSimulation(getString(R.string.error_start_location))
        }
    }

    private fun promoteToForeground(coordinates: Coordinates, useJitter: Boolean) {
        val notification = buildNotification(coordinates, useJitter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopSimulation(requestedError: String? = null) {
        handler.removeCallbacks(publishLocation)
        releaseWakeLock()
        val cleanupError = stopProvider()
        simulationActive = false
        baseCoordinates = null
        settings.setSimulationActive(false)
        val message = requestedError ?: cleanupError
        settings.saveError(message)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        sendStatus(if (message == null) STATUS_INACTIVE else STATUS_ERROR, message)
        stopSelf()
    }

    private fun stopProvider(): String? {
        return try {
            controller?.stop()
            controller = null
            null
        } catch (_: SecurityException) {
            getString(R.string.error_provider_cleanup)
        } catch (_: RuntimeException) {
            getString(R.string.error_provider_cleanup)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        val currentWakeLock = wakeLock ?: powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:LocationUpdates",
        ).apply {
            setReferenceCounted(false)
        }.also { wakeLock = it }
        if (!currentWakeLock.isHeld) currentWakeLock.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun updateNotification(coordinates: Coordinates, useJitter: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(coordinates, useJitter))
    }

    private fun buildNotification(coordinates: Coordinates, useJitter: Boolean): Notification {
        val stopIntent = Intent(this, MockLocationService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val coordinateText = getString(
            R.string.notification_coordinates,
            coordinates.latitude,
            coordinates.longitude,
            getString(if (useJitter) R.string.enabled else R.string.disabled),
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(coordinateText)
            .setContentIntent(openAppPendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.action_stop),
                stopPendingIntent,
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
            },
        )
    }

    private fun sendStatus(status: String, message: String? = null) {
        val intent = Intent(ACTION_STATUS)
            .setPackage(packageName)
            .putExtra(EXTRA_STATUS, status)
            .putExtra(EXTRA_JITTER_ENABLED, jitterEnabled)
        if (message != null) intent.putExtra(EXTRA_MESSAGE, message)
        baseCoordinates?.let { coordinates ->
            intent.putExtra(EXTRA_LATITUDE, coordinates.latitude)
            intent.putExtra(EXTRA_LONGITUDE, coordinates.longitude)
        }
        sendBroadcast(intent)
    }

    companion object {
        const val ACTION_START = "dev.local.mockgps.action.START"
        const val ACTION_STOP = "dev.local.mockgps.action.STOP"
        const val ACTION_STATUS = "dev.local.mockgps.action.STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_JITTER_ENABLED = "jitter_enabled"
        const val STATUS_ACTIVE = "active"
        const val STATUS_INACTIVE = "inactive"
        const val STATUS_STARTING = "starting"
        const val STATUS_STOPPING = "stopping"
        const val STATUS_PERMISSION_REQUIRED = "permission_required"
        const val STATUS_ERROR = "error"

        private const val CHANNEL_ID = "mock_location_service"
        private const val NOTIFICATION_ID = 4201
        private const val STOP_REQUEST_CODE = 4202
        private const val OPEN_APP_REQUEST_CODE = 4203
    }
}
