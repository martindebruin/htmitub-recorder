package com.htmitub.recorder.sync

import com.htmitub.recorder.BuildConfig
import com.htmitub.recorder.db.Run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

// Pure function — extracted for testability
fun buildRunPayload(run: Run): String = buildString {
    append("{")
    append("\"app_run_id\":\"${run.id}\",")
    append("\"started_at\":\"${Instant.ofEpochMilli(run.startedAt)}\",")
    append("\"distance_m\":${run.distanceM},")
    append("\"moving_time_s\":${run.movingTimeS},")
    append("\"elapsed_time_s\":${run.elapsedTimeS},")
    append("\"avg_speed_ms\":${run.avgSpeedMs},")
    append("\"start_lat\":${run.startLat},")
    append("\"start_lng\":${run.startLng},")
    append("\"summary_polyline\":\"${run.summaryPolyline}\",")
    append("\"splits\":${run.splitsJson}")
    append("}")
}

class ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun uploadRun(run: Run) = withContext(Dispatchers.IO) {
        val body = buildRunPayload(run)
        val request = Request.Builder()
            .url("${BuildConfig.SERVER_URL}/api/run")
            .addHeader("Authorization", "Bearer ${BuildConfig.BEARER_TOKEN}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upload failed: HTTP ${response.code}")
            }
        }
    }
}
