package com.example.remembranceagent

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import com.example.remembranceagent.retrieval.Indexer
import com.example.remembranceagent.retrieval.IndexingScheduler
import com.example.remembranceagent.ui.DOCUMENTS_PATH_STRING_KEY
import com.example.remembranceagent.ui.GOOGLE_CLOUD_API_KEY
import com.example.remembranceagent.ui.INDEX_PATH_STRING_KEY
import com.example.remembranceagent.ui.MainScreen
import java.io.File
import java.nio.file.Paths

class MainActivity : ComponentActivity() {
    val TAG = "MainActivity"
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

    private fun scheduleIndexing(context: Context, indexPathString: String, documentsPathString: String) {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
            return
        }

        IndexingScheduler().scheduleWork(context, indexPathString, documentsPathString)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)

        setContent{
            MaterialTheme() {
                MainScreen(
                    initialApiKey = getPreference(GOOGLE_CLOUD_API_KEY, "AIzaSyDM2w_vhjkl36iOOd9Vr-1A7C7-1mb4j7A"),
                    initialDocumentsPath = getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "documents").toPath().toString()),
                    initialIndexPath = getPreference(INDEX_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString()),
                    savePreference = ::setPreference,
                    saveSettings = ::saveSettings,
                    indexDocuments = ::indexDocuments,
                    startRemembranceAgent = ::startRemembranceAgent,
                    stopRemembranceAgent = ::stopRemembranceAgent
                )
            }
        }
        val indexPathString = getPreference(INDEX_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())
        val documentsPathString = getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())

        scheduleIndexing(applicationContext, indexPathString, documentsPathString)
    }

    fun saveSettings() {
        val indexPathString = getPreference(INDEX_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())
        val documentsPathString = getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())

        scheduleIndexing(applicationContext, indexPathString, documentsPathString)
    }

    fun setPreference(key: String, value: String) {
        Log.w(TAG, "Set KV: " + key + " : " + value)
        preferences.edit().putString(key, value).commit()
    }

    fun getPreference(key: String, default: String): String {
        return try{
            preferences.getString(key, default) as String
        } catch (e: java.lang.Exception) {
            default
        }
    }

    fun indexDocuments() {
        val indexPathString = getPreference(INDEX_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())
        val documentPathString = getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "documents").toPath().toString())
        val indexer = Indexer(Paths.get(indexPathString), Paths.get(documentPathString))
        indexer.indexDocuments()
        indexer.close()
    }

    fun startRemembranceAgent() {
        // Todo: Request recording permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(this, RemembranceAgentService::class.java)
                intent.putExtra(GOOGLE_CLOUD_API_KEY, getPreference(GOOGLE_CLOUD_API_KEY, "AIzaSyDM2w_vhjkl36iOOd9Vr-1A7C7-1mb4j7A"))
                intent.putExtra(DOCUMENTS_PATH_STRING_KEY, getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "documents").toPath().toString()))
                intent.putExtra(INDEX_PATH_STRING_KEY, getPreference(INDEX_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString()),)

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
