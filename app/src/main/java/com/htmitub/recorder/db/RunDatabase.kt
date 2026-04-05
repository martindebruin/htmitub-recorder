package com.htmitub.recorder.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Run::class, TrackPoint::class], version = 1, exportSchema = false)
abstract class RunDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao

    companion object {
        @Volatile private var INSTANCE: RunDatabase? = null

        fun getInstance(context: Context): RunDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RunDatabase::class.java,
                    "runs.db",
                ).build().also { INSTANCE = it }
            }
    }
}
