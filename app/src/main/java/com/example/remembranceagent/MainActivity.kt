package com.example.remembranceagent

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.example.remembranceagent.retrieval.IndexingScheduler
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.example.remembranceagent.retrieval.Indexer
import com.example.remembranceagent.ui.DOCUMENTS_PATH_STRING
import com.example.remembranceagent.ui.GOOGLE_CLOUD_API_KEY
import com.example.remembranceagent.ui.INDEX_PATH_STRING
import com.example.remembranceagent.ui.MainScreen


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    val sharedPreferenceKey = "MainActivity"
    var preferences: SharedPreferences = getSharedPreferences(sharedPreferenceKey, MODE_PRIVATE)

    private fun scheduleIndexing(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
                return
            }
            // Todo: Request recording permissions
            IndexingScheduler().scheduleWork(context)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent{
            MaterialTheme() {
                MainScreen(
                    initialApiKey = getPreference(GOOGLE_CLOUD_API_KEY),
                    initialDocumentsPath = getPreference(DOCUMENTS_PATH_STRING),
                    initialIndexPath = getPreference(INDEX_PATH_STRING),
                    savePreference = ::setPreference,
                    indexDocuments = ::indexDocuments
                )
            }
        }
        scheduleIndexing(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        scheduleIndexing(applicationContext)
    }

    fun setPreference(key: String, value: String) {
        preferences.edit().putString(key, value).commit()
    }

    fun getPreference(key: String): String {
        return try{
            preferences.getString(key, "") as String
        } catch (e: java.lang.Exception) {
            ""
        }
    }

    fun indexDocuments() {
        val indexer = Indexer()
        indexer.indexDocuments()
        indexer.close()
    }
}