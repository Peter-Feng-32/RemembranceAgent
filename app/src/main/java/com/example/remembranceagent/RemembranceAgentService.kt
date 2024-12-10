package com.example.remembranceagent

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.text.Spanned
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.remembranceagent.RetrieverService.Companion
import com.example.remembranceagent.ui.GOOGLE_CLOUD_API_KEY
import com.google.audio.NetworkConnectionChecker
import com.google.audio.asr.CloudSpeechSessionParams
import com.google.audio.asr.CloudSpeechStreamObserverParams
import com.google.audio.asr.RepeatingRecognitionSession
import com.google.audio.asr.SafeTranscriptionResultFormatter
import com.google.audio.asr.SpeechRecognitionModelOptions
import com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.DICTATION_DEFAULT
import com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.VIDEO
import com.google.audio.asr.TranscriptionResultFormatterOptions
import com.google.audio.asr.TranscriptionResultFormatterOptions.TranscriptColoringStyle.NO_COLORING
import com.google.audio.asr.TranscriptionResultUpdatePublisher
import com.google.audio.asr.TranscriptionResultUpdatePublisher.ResultSource
import com.google.audio.asr.TranscriptionResultUpdatePublisher.UpdateType
import com.google.audio.asr.cloud.CloudSpeechSessionFactory
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread


class RemembranceAgentService : Service() {

    companion object {
        const val TAG = "RemembranceAgentService"
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
    private var apiKey = ""

    lateinit var safeTranscriptionResultFormatter: SafeTranscriptionResultFormatter
    private var previousUpdateTimestamp: Long = 0
    private var queryGap: Long = 1000

    private val SERVER_HOST = "127.0.0.1"
    private val SERVER_PORT = 9999
    private lateinit var socket: Socket
    val CHANNEL_ID = "CAPTIONING_CHANNEL"

    fun createNotificationChannel() {
        val name = "Captioning Channel"
        val descriptionText = "Channel for notifications from Captioning"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
        mChannel.description = descriptionText
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).build()
        ServiceCompat.startForeground(this, 2, notification, foregroundServiceType)

        apiKey = intent?.getStringExtra(GOOGLE_CLOUD_API_KEY) ?: ""
        initLanguageLocale()
        constructRepeatingRecognitionSession()
        startRecording()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        // No binding is provided, so return null for started services
        return null
    }
    /** Captioning Library stuff  */
    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 1

    private val MIC_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO
    private val MIC_CHANNEL_ENCODING: Int = AudioFormat.ENCODING_PCM_16BIT
    private val MIC_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
    private val SAMPLE_RATE = 16000
    private val CHUNK_SIZE_SAMPLES = 1280
    private val BYTES_PER_SAMPLE = 2

    private var currentLanguageCodePosition = 0
    private var currentLanguageCode: String? = null

    private var audioRecord: AudioRecord? = null
    private val buffer = ByteArray(BYTES_PER_SAMPLE * CHUNK_SIZE_SAMPLES)

    // This class was intended to be used from a thread where timing is not critical (i.e. do not
    // call this in a system audio callback). Network calls will be made during all of the functions
    // that RepeatingRecognitionSession inherits from SampleProcessorInterface.
    private var recognizer: RepeatingRecognitionSession? = null
    private var networkChecker: NetworkConnectionChecker? = null
    private var factory: CloudSpeechSessionFactory? = null

    private lateinit var outputStream: OutputStream

    private val transcriptUpdater =
        TranscriptionResultUpdatePublisher { formattedTranscript: Spanned, updateType: UpdateType ->
            Log.w(TAG, "Transcript: $formattedTranscript")
            val currTimestamp = System.currentTimeMillis()
            if (System.currentTimeMillis() - previousUpdateTimestamp > queryGap) {
                handleTranscript(formattedTranscript.toString())
                previousUpdateTimestamp = currTimestamp
            }
            if (updateType == UpdateType.TRANSCRIPT_FINALIZED) {
                handleTranscript(formattedTranscript.toString())
                recognizer?.resetAndClearTranscript()
                previousUpdateTimestamp = currTimestamp
            }
        }

    private fun connectSocket() {
        socket = Socket(SERVER_HOST, SERVER_PORT)
    }

    private fun handleTranscript(transcript: String) {
        thread{
            try {
                outputStream = socket.getOutputStream()
                outputStream.write(("$transcript\n").toByteArray())
                outputStream.flush()
            } catch (e: Exception) {
                try {
                    connectSocket()
                    outputStream = socket.getOutputStream()
                    outputStream.write(("$transcript\n").toByteArray())
                    outputStream.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private val readMicData = Runnable {
        Log.w("CaptioningFragment", "readMicData")
        if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) {
            return@Runnable
        }
        recognizer!!.init(CHUNK_SIZE_SAMPLES)
        while (audioRecord!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord!!.read(buffer, 0, CHUNK_SIZE_SAMPLES * BYTES_PER_SAMPLE)
            recognizer!!.processAudioBytes(buffer)
        }
        recognizer!!.stop()
    }

    override fun onCreate() {
        isRunning = true
        super.onCreate()
    }

    override fun onDestroy() {
        Log.w(TAG, "Destroy")
        super.onDestroy()
        if (audioRecord != null) {
            audioRecord!!.stop()
        }
        if(this::socket.isInitialized && !socket.isClosed) {
            socket.close()
        }
        isRunning = false
    }

    /** Captioning Functions  */
    private fun initLanguageLocale() {
        // The default locale is en-US.
        currentLanguageCode = "en-US"
        currentLanguageCodePosition = 22
    }

    private fun constructRepeatingRecognitionSession() {
        val options = SpeechRecognitionModelOptions.newBuilder()
            .setLocale(currentLanguageCode) // As of 7/18/19, Cloud Speech's video model supports en-US only.
            .setModel(if (currentLanguageCode == "en-US") VIDEO else DICTATION_DEFAULT)
            .build()
        val cloudParams = CloudSpeechSessionParams.newBuilder()
            .setObserverParams(
                CloudSpeechStreamObserverParams.newBuilder().setRejectUnstableHypotheses(false)
            )
            .setFilterProfanity(true)
            .setEncoderParams(
                CloudSpeechSessionParams.EncoderParams.newBuilder()
                    .setEnableEncoder(false)
            )
            .build()
        networkChecker = NetworkConnectionChecker(this)
        networkChecker!!.registerNetworkCallback()
        // There are lots of options for formatting the text. These can be useful for debugging
        // and visualization, but it increases the effort of reading the transcripts.
        val formatterOptions = TranscriptionResultFormatterOptions.newBuilder()
            .setTranscriptColoringStyle(NO_COLORING)
            .build()
        factory = CloudSpeechSessionFactory(cloudParams, apiKey)
        safeTranscriptionResultFormatter = SafeTranscriptionResultFormatter(formatterOptions)
        val recognizerBuilder = RepeatingRecognitionSession.newBuilder()
            .setSpeechSessionFactory(factory)
            .setSampleRateHz(SAMPLE_RATE)
            .setTranscriptionResultFormatter(safeTranscriptionResultFormatter)
            .setSpeechRecognitionModelOptions(options)
            .setNetworkConnectionChecker(networkChecker)
        recognizer = recognizerBuilder.build()
        recognizer!!.registerCallback(transcriptUpdater, ResultSource.WHOLE_RESULT)
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (audioRecord == null) {
            audioRecord = AudioRecord(
                MIC_SOURCE,
                SAMPLE_RATE,
                MIC_CHANNELS,
                MIC_CHANNEL_ENCODING,
                CHUNK_SIZE_SAMPLES * BYTES_PER_SAMPLE
            )
        }
        audioRecord!!.startRecording()
        Thread(readMicData).start()
    }

}