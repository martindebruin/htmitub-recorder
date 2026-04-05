package com.htmitub.recorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.htmitub.recorder.db.Run
import com.htmitub.recorder.db.RunDatabase
import com.htmitub.recorder.db.TrackPoint
import com.htmitub.recorder.util.PolylineEncoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import kotlin.math.*

data class Split(
    val split: Int,
    val distance: Int,
    val movingTime: Int,
    val averageSpeed: Double,
    val elevationDifference: Int,
)

data class RecordingState(
    val distanceM: Double = 0.0,
    val currentPaceSecKm: Int? = null,
    val avgPaceSecKm: Int? = null,
    val movingTimeMs: Long = 0L,
    val isPaused: Boolean = false,
)

class RecordingService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> get() = _state

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Tracking state
    private var runId: String = ""
    private var startTimeMs: Long = 0L
    private var lastResumeMs: Long = 0L
    private var accumulatedMovingMs: Long = 0L
    private var isPaused: Boolean = false

    private var totalDistanceM: Double = 0.0
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastAlt: Double = 0.0

    private val trackPoints = mutableListOf<TrackPoint>()

    // Rolling pace window (30 seconds)
    private data class PacePoint(val ts: Long, val distM: Double)
    private val paceWindow = ArrayDeque<PacePoint>()

    // 1km split tracking
    private var nextSplitKm = 1
    private var splitStartMovingMs: Long = 0L
    private var splitStartAlt: Double = 0.0
    private val completedSplits = mutableListOf<Split>()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    fun startRun() {
        runId = UUID.randomUUID().toString()
        startTimeMs = System.currentTimeMillis()
        lastResumeMs = startTimeMs
        accumulatedMovingMs = 0L
        totalDistanceM = 0.0
        lastLat = null
        lastLng = null
        trackPoints.clear()
        paceWindow.clear()
        completedSplits.clear()
        nextSplitKm = 1
        splitStartMovingMs = 0L
        isPaused = false

        startForeground(NOTIFICATION_ID, buildNotification("0.00 km"))
        startLocationUpdates()
    }

    fun pauseRun() {
        if (isPaused) return
        isPaused = true
        accumulatedMovingMs += System.currentTimeMillis() - lastResumeMs
        _state.value = _state.value.copy(isPaused = true, currentPaceSecKm = null)
    }

    fun resumeRun() {
        if (!isPaused) return
        isPaused = false
        lastResumeMs = System.currentTimeMillis()
        _state.value = _state.value.copy(isPaused = false)
    }

    fun stopRun() {
        stopLocationUpdates()
        if (!isPaused) accumulatedMovingMs += System.currentTimeMillis() - lastResumeMs
        val elapsedMs = System.currentTimeMillis() - startTimeMs

        scope.launch {
            val polyline = PolylineEncoder.encode(trackPoints.map { Pair(it.lat, it.lng) })
            val avgSpeedMs = if (accumulatedMovingMs > 0) totalDistanceM / (accumulatedMovingMs / 1000.0) else 0.0
            val splitsJson = buildSplitsJson()

            val run = Run(
                id = runId,
                startedAt = startTimeMs,
                distanceM = totalDistanceM,
                movingTimeS = (accumulatedMovingMs / 1000).toInt(),
                elapsedTimeS = (elapsedMs / 1000).toInt(),
                avgSpeedMs = avgSpeedMs,
                startLat = trackPoints.firstOrNull()?.lat ?: 0.0,
                startLng = trackPoints.firstOrNull()?.lng ?: 0.0,
                summaryPolyline = polyline,
                splitsJson = splitsJson,
                syncStatus = "pending",
            )

            val db = RunDatabase.getInstance(applicationContext)
            db.runDao().insert(run)
            db.runDao().insertTrackPoints(trackPoints.toList())

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    if (loc.accuracy > 10f) return
                    onLocation(loc.latitude, loc.longitude, loc.altitude, loc.time, loc.accuracy)
                }
            }
        }
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {}
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun onLocation(lat: Double, lng: Double, alt: Double, ts: Long, accuracy: Float) {
        // Track points are added regardless of pause state so the polyline has correct coordinates
        // at resume. Paused segments appear as gaps; no moving-time or pace calculations are done.
        val point = TrackPoint(runId = runId, lat = lat, lng = lng, alt = alt, ts = ts, accuracy = accuracy)
        trackPoints.add(point)

        if (!isPaused) {
            val prev = Pair(lastLat, lastLng)
            if (prev.first != null && prev.second != null) {
                val d = haversineMeters(prev.first!!, prev.second!!, lat, lng)
                totalDistanceM += d

                val movingMs = accumulatedMovingMs + (System.currentTimeMillis() - lastResumeMs)

                // Rolling pace window
                paceWindow.addLast(PacePoint(ts = System.currentTimeMillis(), distM = totalDistanceM))
                paceWindow.removeAll { System.currentTimeMillis() - it.ts > PACE_WINDOW_MS }
                val currentPace: Int? = if (paceWindow.size >= 2) {
                    val first = paceWindow.first()
                    val last = paceWindow.last()
                    val dt = (last.ts - first.ts) / 1000.0
                    val dd = last.distM - first.distM
                    if (dd > 0) (1000.0 * dt / dd).toInt() else null
                } else null

                val avgPace: Int? = if (movingMs > 0 && totalDistanceM > 0)
                    (1000.0 * (movingMs / 1000.0) / totalDistanceM).toInt() else null

                // Check 1km splits
                if (totalDistanceM >= nextSplitKm * 1000.0) {
                    val splitMovingMs = movingMs - splitStartMovingMs
                    completedSplits.add(Split(
                        split = nextSplitKm,
                        distance = 1000,
                        movingTime = (splitMovingMs / 1000).toInt(),
                        averageSpeed = 1000.0 / (splitMovingMs / 1000.0),
                        elevationDifference = (alt - splitStartAlt).toInt(),
                    ))
                    nextSplitKm++
                    splitStartMovingMs = movingMs
                    splitStartAlt = alt
                }

                _state.value = RecordingState(
                    distanceM = totalDistanceM,
                    currentPaceSecKm = currentPace,
                    avgPaceSecKm = avgPace,
                    movingTimeMs = movingMs,
                    isPaused = false,
                )

                val distKm = "%.2f km".format(totalDistanceM / 1000)
                startForeground(NOTIFICATION_ID, buildNotification(distKm))
            }
        }

        lastLat = lat
        lastLng = lng
        lastAlt = alt
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }

    private fun buildSplitsJson(): String {
        if (completedSplits.isEmpty()) return "[]"
        return completedSplits.joinToString(prefix = "[", postfix = "]") { s ->
            "{\"split\":${s.split},\"distance\":${s.distance},\"moving_time\":${s.movingTime}," +
            "\"average_speed\":${s.averageSpeed},\"elevation_difference\":${s.elevationDifference}}"
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_desc) })
    }

    private fun buildNotification(distanceText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(distanceText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopLocationUpdates()
    }

    companion object {
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1
        const val PACE_WINDOW_MS = 30_000L
    }
}
