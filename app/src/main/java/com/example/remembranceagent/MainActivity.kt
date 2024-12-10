package com.example.remembranceagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.startForeground
import androidx.core.content.ContextCompat
import com.example.remembranceagent.retrieval.Indexer
import com.example.remembranceagent.retrieval.IndexingScheduler
import com.example.remembranceagent.ui.DOCUMENTS_PATH_STRING_KEY
import com.example.remembranceagent.ui.GOOGLE_CLOUD_API_KEY
import com.example.remembranceagent.ui.INDEX_PATH_STRING_KEY
import com.example.remembranceagent.ui.MainScreen
import com.example.remembranceagent.ui.PREFERENCES_NAME
import java.io.File
import java.nio.file.Paths
import kotlin.concurrent.thread

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
        preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        setContent{
            MaterialTheme() {
                MainScreen(
                    initialApiKey = getPreference(GOOGLE_CLOUD_API_KEY, ""),
                    initialDocumentsPath = getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "documents").toPath().toString()),
                    getIndexPath = ::getIndexPath,
                    savePreference = ::setPreference,
                    saveSettings = ::saveSettings,
                    indexDocuments = ::indexDocuments,
                    startRemembranceAgent = ::startRemembranceAgent,
                    stopRemembranceAgent = ::stopRemembranceAgent,
                    startRetriever = ::startRetriever,
                    stopRetriever = ::stopRetriever,
                )
            }
        }
        val indexPathString = getPreference(INDEX_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())
        val documentsPathString = getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())

        scheduleIndexing(applicationContext, indexPathString, documentsPathString)
    }

    fun getIndexPath(): String {
        return getPreference(INDEX_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index_0").toPath().toString())
    }

    fun saveSettings() {
        val indexPathString = getPreference(INDEX_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())
        val documentsPathString = getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "index").toPath().toString())

        scheduleIndexing(applicationContext, indexPathString, documentsPathString)
    }

    fun setPreference(key: String, value: String) {
        preferences.edit().putString(key, value).commit()
    }

    private fun getPreference(key: String, default: String): String {
        return try{
            preferences.getString(key, default) as String
        } catch (e: java.lang.Exception) {
            default
        }
    }

    fun indexDocuments() {
        val documentPathString = getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "documents").toPath().toString())
        val indexer = Indexer(preferences, Paths.get(documentPathString))
        thread {
            indexer.indexDocuments()
        }
    }

    fun createNotification(channelId: String, notificationTitle: String, notificationTicker: String, notificationContent: String) : Notification{
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val  mChannelName = getString(R.string.app_name)
            val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notificationChannel = NotificationChannel(
                channelId,
                mChannelName,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(notificationChannel)
            NotificationCompat.Builder(this, notificationChannel.id)
        } else {
            NotificationCompat.Builder(this)
        }

        val myIntent = Intent(this, MainActivity::class.java)

        myIntent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            myIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        builder
            .setContentTitle(notificationTitle)
            .setTicker(notificationTicker)      //It will show text on status bar, even without having user to "pull down" the incoming notification.
            .setContentText(notificationContent)
            .setContentIntent(pendingIntent)

        val notification = builder.build()
        notification.flags = notification.flags or NotificationCompat.FLAG_ONGOING_EVENT or NotificationCompat.FLAG_NO_CLEAR
        return notification
    }

    fun startRemembranceAgent() {
        // Todo: Request recording permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(this, RemembranceAgentService::class.java)
                intent.putExtra(GOOGLE_CLOUD_API_KEY, getPreference(GOOGLE_CLOUD_API_KEY, ""))
            startForegroundService(intent)
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRemembranceAgent() {
        val intent = Intent(this, RemembranceAgentService::class.java)
        stopService(intent)
    }

    fun startRetriever() {
        val intent = Intent(this, RetrieverService::class.java)
        intent.putExtra(DOCUMENTS_PATH_STRING_KEY, getPreference(DOCUMENTS_PATH_STRING_KEY, File(Environment.getExternalStorageDirectory().path, "documents").toPath().toString()))
        intent.putExtra(INDEX_PATH_STRING_KEY, getPreference(INDEX_PATH_STRING_KEY, ""),)
        startForegroundService(intent)
    }

    fun stopRetriever() {
        val intent = Intent(this, RetrieverService::class.java)
        stopService(intent)
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "Recording permission is required to use this feature.", Toast.LENGTH_SHORT).show()
    }
}
