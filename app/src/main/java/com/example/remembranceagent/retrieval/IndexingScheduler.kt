package com.example.remembranceagent.retrieval

import android.content.Context
import android.util.Log
import androidx.work.*
import androidx.work.PeriodicWorkRequest
import com.example.remembranceagent.ui.DOCUMENTS_PATH_STRING_KEY
import com.example.remembranceagent.ui.INDEX_PATH_STRING_KEY
import com.example.remembranceagent.ui.PREFERENCES_NAME
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

val workName = "Remembrance Agent Document Indexing"

class IndexingScheduler() {
    fun scheduleWork(context: Context, indexPathString: String, documentsPathString: String) {
        val constraints = Constraints.Builder()
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
        val documentsPathString = inputData.getString(DOCUMENTS_PATH_STRING_KEY)
        val documentsPath = Paths.get(documentsPathString)
        val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        val indexer = Indexer(preferences = preferences, documentsPath)
        indexer.indexDocuments()
        return Result.success()
    }
}