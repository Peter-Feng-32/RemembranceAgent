package com.example.remembranceagent

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import com.example.remembranceagent.retrieval.Indexer
import com.example.remembranceagent.retrieval.IndexingScheduler
import com.example.remembranceagent.ui.DOCUMENTS_PATH_STRING
import com.example.remembranceagent.ui.GOOGLE_CLOUD_API_KEY
import com.example.remembranceagent.ui.INDEX_PATH_STRING
import com.example.remembranceagent.ui.MainScreen

class MainActivity : ComponentActivity() {
    lateinit var preferences: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted; start recording
            startRemembranceAgent()
        } else {
            // Permission denied; handle accordingly
            showPermissionDeniedMessage()
        }
    }

    private fun scheduleIndexing(context: Context) {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
            return
        }

        IndexingScheduler().scheduleWork(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)

        setContent{
            MaterialTheme() {
                MainScreen(
                    initialApiKey = getPreference(GOOGLE_CLOUD_API_KEY),
                    initialDocumentsPath = getPreference(DOCUMENTS_PATH_STRING),
                    initialIndexPath = getPreference(INDEX_PATH_STRING),
                    savePreference = ::setPreference,
                    indexDocuments = ::indexDocuments,
                    startRemembranceAgent = ::startRemembranceAgent,
                    stopRemembranceAgent = ::stopRemembranceAgent
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

    fun startRemembranceAgent() {
        // Todo: Request recording permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(this, RemembranceAgentService::class.java)
                startService(intent)
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRemembranceAgent() {
        val intent = Intent(this, RemembranceAgentService::class.java)
        stopService(intent)
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "Recording permission is required to use this feature.", Toast.LENGTH_SHORT).show()
    }
}
