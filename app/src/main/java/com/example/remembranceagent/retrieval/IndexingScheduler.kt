package com.example.remembranceagent.retrieval

import android.content.Context
import androidx.work.*
import androidx.work.PeriodicWorkRequest
import com.example.remembranceagent.ui.DOCUMENTS_PATH_STRING_KEY
import com.example.remembranceagent.ui.INDEX_PATH_STRING_KEY
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

val workName = "Remembrance Agent Document Indexing"

class IndexingScheduler() {
    fun scheduleWork(context: Context, indexPathString: String, documentsPathString: String) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .setRequiresStorageNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(INDEX_PATH_STRING_KEY, indexPathString)
            .putString(DOCUMENTS_PATH_STRING_KEY, documentsPathString)
            .build()

        val indexWorkRequest: PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<IndexingWorker>(repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("Remembrance Agent Indexing")
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, indexWorkRequest)
    }
}

class IndexingWorker(appContext: Context, workerParameters: WorkerParameters):
    Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val indexPathString = inputData.getString(INDEX_PATH_STRING_KEY)
        val indexPath = Paths.get(indexPathString)

        val documentsPathString = inputData.getString(DOCUMENTS_PATH_STRING_KEY)
        val documentsPath = Paths.get(documentsPathString)

        val indexer = Indexer(indexPath, documentsPath)
        indexer.indexDocuments()
        indexer.close()
        return Result.success()
    }
}