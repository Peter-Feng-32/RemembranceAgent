package com.example.remembranceagent.retrieval

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

val workName = "Remembrance Agent Document Indexing"

class IndexingScheduler() {
    fun scheduleWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .setRequiresStorageNotLow(true)
            .build()

        val indexWorkRequest: PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<IndexingWorker>(repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS)
                .setConstraints(constraints)
                .addTag("Remembrance Agent Indexing")
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.KEEP, indexWorkRequest)
    }
}

class IndexingWorker(appContext: Context, workerParameters: WorkerParameters):
    Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val indexer = Indexer()
        indexer.indexDocuments()
        indexer.close()
        return Result.success()
    }
}