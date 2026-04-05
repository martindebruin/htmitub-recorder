package com.htmitub.recorder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.htmitub.recorder.db.Run
import com.htmitub.recorder.db.RunDatabase
import com.htmitub.recorder.db.TrackPoint
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunDaoTest {

    private lateinit var db: RunDatabase

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, RunDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    private fun sampleRun(id: String = "run-1") = Run(
        id = id,
        startedAt = 1712220720000L,
        distanceM = 8420.0,
        movingTimeS = 2640,
        elapsedTimeS = 2780,
        avgSpeedMs = 3.19,
        startLat = 59.334,
        startLng = 18.063,
        summaryPolyline = "_p~iF~ps|U",
        splitsJson = "[]",
        syncStatus = "pending",
    )

    @Test fun `insert and retrieve run`() = runBlocking {
        db.runDao().insert(sampleRun())
        val runs = db.runDao().getAllRuns()
        assertEquals(1, runs.size)
        assertEquals("run-1", runs[0].id)
    }

    @Test fun `getPendingRuns returns only pending`() = runBlocking {
        db.runDao().insert(sampleRun("a").copy(syncStatus = "pending"))
        db.runDao().insert(sampleRun("b").copy(syncStatus = "synced"))
        db.runDao().insert(sampleRun("c").copy(syncStatus = "failed"))
        val pending = db.runDao().getPendingRuns()
        assertEquals(1, pending.size)
        assertEquals("a", pending[0].id)
    }

    @Test fun `markSynced updates status`() = runBlocking {
        db.runDao().insert(sampleRun())
        db.runDao().markSynced("run-1")
        assertEquals("synced", db.runDao().getAllRuns()[0].syncStatus)
    }

    @Test fun `markFailed updates status`() = runBlocking {
        db.runDao().insert(sampleRun())
        db.runDao().markFailed("run-1")
        assertEquals("failed", db.runDao().getAllRuns()[0].syncStatus)
    }

    @Test fun `insert and delete track points`() = runBlocking {
        db.runDao().insert(sampleRun())
        db.runDao().insertTrackPoint(TrackPoint(runId = "run-1", lat = 59.334, lng = 18.063, alt = 10.0, ts = 1000L, accuracy = 5f))
        db.runDao().insertTrackPoint(TrackPoint(runId = "run-1", lat = 59.335, lng = 18.064, alt = 11.0, ts = 2000L, accuracy = 4f))
        assertEquals(2, db.runDao().getTrackPoints("run-1").size)
        db.runDao().deleteTrackPoints("run-1")
        assertEquals(0, db.runDao().getTrackPoints("run-1").size)
    }
}
