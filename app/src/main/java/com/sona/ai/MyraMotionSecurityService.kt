package com.sona.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opt-in anti-tamper helper. It detects sudden movement only while explicitly armed
 * and shows a persistent notification so monitoring is visible to the phone owner.
 * Android may stop the service or limit sensors; movement is not proof of a person.
 */
class MyraMotionSecurityService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastAlertAt = 0L
    private var baselineReady = false
    private var baseline = 9.8f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("hi", "IN")
                tts?.setSpeechRate(0.92f)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISARM) {
            getSharedPreferences("myra_security", MODE_PRIVATE).edit().putBoolean("armed", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("Armed • movement monitoring active"))
        getSharedPreferences("myra_security", MODE_PRIVATE).edit().putBoolean("armed", true).apply()
        val sensor = accelerometer
        if (sensor == null) {
            saveEvent("Motion sensor unavailable")
            stopSelf()
            return START_NOT_STICKY
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 3) return
        val magnitude = kotlin.math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        if (!baselineReady) {
            baseline = magnitude
            baselineReady = true
            return
        }
        baseline = baseline * 0.92f + magnitude * 0.08f
        val movement = kotlin.math.abs(magnitude - baseline)
        val now = SystemClock.elapsedRealtime()
        val securityPrefs = getSharedPreferences("myra_security", MODE_PRIVATE)
        val movementThreshold = securityPrefs.getFloat("sensitivity", DEFAULT_SENSITIVITY)
        val cooldownMs = securityPrefs.getLong("cooldown_ms", DEFAULT_COOLDOWN_MS)
        if (movement > movementThreshold && now - lastAlertAt > cooldownMs) {
            lastAlertAt = now
            saveEvent("Movement detected")
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification("Movement detected • check your phone"))
            if (ttsReady) {
                tts?.speak("Please phone ko wapas rakh do. MYRA security alert.", TextToSpeech.QUEUE_FLUSH, null, "myra-motion-alert")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun saveEvent(message: String) {
        val time = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date())
        val prefs = getSharedPreferences("myra_security", MODE_PRIVATE)
        val old = prefs.getString("events", "").orEmpty()
        prefs.edit().putString("events", ("$time • $message\n" + old).lines().take(30).joinToString("\n")).apply()
    }

    private fun buildNotification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("MYRA AI • Phone Security")
            .setContentText(message)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "MYRA Phone Security", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Visible notification while phone movement monitoring is armed"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        getSharedPreferences("myra_security", MODE_PRIVATE).edit().putBoolean("armed", false).apply()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_ARM = "com.sona.ai.security.ARM"
        const val ACTION_DISARM = "com.sona.ai.security.DISARM"
        private const val CHANNEL_ID = "myra_phone_security"
        private const val NOTIFICATION_ID = 7401
        const val DEFAULT_SENSITIVITY = 3.2f
        const val DEFAULT_COOLDOWN_MS = 15_000L
    }
}
