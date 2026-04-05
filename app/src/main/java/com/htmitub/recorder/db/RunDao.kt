package com.htmitub.recorder.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: Run)

    @Query("SELECT * FROM runs ORDER BY startedAt DESC")
    suspend fun getAllRuns(): List<Run>

    @Query("SELECT * FROM runs WHERE syncStatus = 'pending' ORDER BY startedAt ASC")
    suspend fun getPendingRuns(): List<Run>

    @Query("SELECT * FROM runs WHERE syncStatus = 'failed' ORDER BY startedAt ASC")
    suspend fun getFailedRuns(): List<Run>

    @Query("UPDATE runs SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE runs SET syncStatus = 'failed' WHERE id = :id")
    suspend fun markFailed(id: String)

    @Insert
    suspend fun insertTrackPoint(point: TrackPoint)

    @Insert
    suspend fun insertTrackPoints(points: List<TrackPoint>)

    @Query("SELECT * FROM track_points WHERE runId = :runId ORDER BY ts ASC")
    suspend fun getTrackPoints(runId: String): List<TrackPoint>

    @Query("DELETE FROM track_points WHERE runId = :runId")
    suspend fun deleteTrackPoints(runId: String)
}
