package com.htmitub.recorder

import com.htmitub.recorder.db.Run
import com.htmitub.recorder.sync.buildRunPayload
import org.junit.Assert.*
import org.junit.Test

class ApiClientPayloadTest {

    private val run = Run(
        id = "550e8400-e29b-41d4-a716-446655440000",
        startedAt = 1712214720000L,  // 2024-04-04T07:12:00Z
        distanceM = 8420.0,
        movingTimeS = 2640,
        elapsedTimeS = 2780,
        avgSpeedMs = 3.19,
        startLat = 59.334,
        startLng = 18.063,
        summaryPolyline = "_p~iF~ps|U",
        splitsJson = "[{\"split\":1}]",
        syncStatus = "pending",
    )

    @Test fun `payload contains app_run_id`() {
        assertTrue(buildRunPayload(run).contains("\"app_run_id\":\"550e8400-e29b-41d4-a716-446655440000\""))
    }

    @Test fun `payload contains started_at as ISO string`() {
        assertTrue(buildRunPayload(run).contains("\"started_at\":\"2024-04-04T07:12:00Z\""))
    }

    @Test fun `payload contains distance_m`() {
        assertTrue(buildRunPayload(run).contains("\"distance_m\":8420.0"))
    }

    @Test fun `payload contains splits array`() {
        assertTrue(buildRunPayload(run).contains("\"splits\":[{\"split\":1}]"))
    }
}
