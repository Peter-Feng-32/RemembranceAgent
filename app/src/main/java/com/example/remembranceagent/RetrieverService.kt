package com.example.remembranceagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.remembranceagent.retrieval.DOCUMENTS_PATH
import com.example.remembranceagent.retrieval.Retriever
import com.example.remembranceagent.ui.DOCUMENTS_PATH_STRING_KEY
import com.example.remembranceagent.ui.INDEX_PATH_STRING_KEY
import com.example.remembranceagent.ui.PREFERENCES_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import kotlin.concurrent.thread

class RetrieverService : Service() {
    var indexPathString = ""
    var documentsPathString = ""
    private val SERVER_PORT = 9998
    private lateinit var serverSocket: ServerSocket
    private lateinit var retriever: Retriever
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val TAG = "RetrieverService"
        var isRunning: Boolean = false
            set(value) {
                field = value
                notifyListeners(value)
            }

        private val listeners = mutableListOf<(Boolean) -> Unit>()

        fun addListener(listener: (Boolean) -> Unit) {
            listeners.add(listener)
        }

        private fun notifyListeners(value: Boolean) {
            for (listener in listeners) {
                listener(value)
            }
        }

        fun removeListener(listener: (Boolean) -> Unit) {
            listeners.remove(listener)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    val CHANNEL_ID = "RETRIEVER_CHANNEL"

    fun createNotificationChannel() {
        val name = "Retriever Channel"
        val descriptionText = "Channel for notifications from Retriever"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ServiceCompat.startForeground(this, 2, notification, foregroundServiceType)
        } else {
            ServiceCompat.startForeground(this, 2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }

        indexPathString = intent?.getStringExtra(INDEX_PATH_STRING_KEY) ?: ""
        if (indexPathString == "") {
            stopSelf()
            return START_NOT_STICKY
        }

        documentsPathString = intent?.getStringExtra(DOCUMENTS_PATH_STRING_KEY) ?: DOCUMENTS_PATH.toString()
        Log.w(TAG, indexPathString)
        retriever = Retriever(indexPathString, documentsPathString)

        startServer()

        return super.onStartCommand(intent, flags, startId)
    }

    private fun startServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(SERVER_PORT)

                while (isRunning) {
                    // Accept a client connection
                    var clientSocket: Socket? = null
                    try {
                        clientSocket = serverSocket.accept()

                        // Handle client communication in a separate thread
                        thread {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in accepting client socket", e)
                        clientSocket?.close()
                    }

                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in server socket", e)
            } finally {
                serverSocket.close()
            }
        }
    }

    override fun onDestroy() {
        Log.w(TAG, "Destroy")
        super.onDestroy()
        isRunning = false
        serverSocket.close()
        job.cancel()
    }

    private fun handleClient(clientSocket: Socket) {

        try {
            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val output: OutputStream = clientSocket.getOutputStream()
            Log.d(TAG, "Handle Client")

            while (isRunning && !clientSocket.isClosed) {
                // Read data from the client
                val clientMessage = input.readLine()
                if (clientMessage == null) {
                    Log.d(TAG, "Client disconnected")
                    break
                }
                Log.d(TAG, "Received from client: $clientMessage")

                // Respond back to the client
                val retrievedResult = retriever.query(clientMessage)
                var response = ""
                if(retrievedResult == null) {
                    response = "{\"similarity_score\": 0.0," +
                            "\"document_title\": \"None\", " +
                            "\"file_path\": null" +
                            "}"
                } else {
                    response = "{\"similarity_score\": ${retrievedResult.score}," +
                            "\"document_title\": \"${retrievedResult.title}\", " +
                            "\"file_path\": \"${retrievedResult.filePath}\"" +
                            "}"
                }
                Log.w(TAG, "Writing to client: $response")

                output.write(response.toByteArray())
                output.flush()
            }
            Log.d(TAG, "Handled Client")


        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing client socket", e)
            }
        }
    }
}