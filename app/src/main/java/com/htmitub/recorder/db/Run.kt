package com.htmitub.recorder.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class Run(
    @PrimaryKey val id: String,
    val startedAt: Long,       // Unix ms
    val distanceM: Double,
    val movingTimeS: Int,
    val elapsedTimeS: Int,
    val avgSpeedMs: Double,
    val startLat: Double,
    val startLng: Double,
    val summaryPolyline: String,
    val splitsJson: String,    // JSON array string
    val syncStatus: String,    // "pending" | "synced" | "failed"
)
