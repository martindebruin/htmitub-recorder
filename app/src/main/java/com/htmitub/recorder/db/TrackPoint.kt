package com.htmitub.recorder.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = Run::class,
        parentColumns = ["id"],
        childColumns = ["runId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("runId")],
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val lat: Double,
    val lng: Double,
    val alt: Double,
    val ts: Long,       // Unix ms
    val accuracy: Float,
)
