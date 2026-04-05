package com.htmitub.recorder.sync

import android.content.Context
import androidx.work.*
import com.htmitub.recorder.db.RunDatabase

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = RunDatabase.getInstance(applicationContext)
        val api = ApiClient()
        val runs = db.runDao().getPendingRuns() + db.runDao().getFailedRuns()
        var anyFailed = false
        for (run in runs) {
            try {
                api.uploadRun(run)
                db.runDao().markSynced(run.id)
                db.runDao().deleteTrackPoints(run.id)
            } catch (_: Exception) {
                db.runDao().markFailed(run.id)
                anyFailed = true
            }
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_runs",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
