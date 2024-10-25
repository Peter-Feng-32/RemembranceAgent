package com.example.remembranceagent

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.text.Spanned
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.Typeface
import com.example.remembranceagent.retrieval.Retriever
import com.example.remembranceagent.ui.GOOGLE_CLOUD_API_KEY
import com.example.remembranceagent.ui.INDEX_PATH_STRING
import com.example.remembranceagent.z100.Z100Renderer
import com.google.audio.NetworkConnectionChecker
import com.google.audio.asr.*
import com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.DICTATION_DEFAULT
import com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.VIDEO
import com.google.audio.asr.TranscriptionResultFormatterOptions.TranscriptColoringStyle.NO_COLORING
import com.google.audio.asr.TranscriptionResultUpdatePublisher.ResultSource
import com.google.audio.asr.TranscriptionResultUpdatePublisher.UpdateType
import com.google.audio.asr.cloud.CloudSpeechSessionFactory
import com.termux.terminal.TerminalEmulator
import com.vuzix.ultralite.UltraliteSDK
import org.apache.lucene.document.Document
import kotlin.math.max

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
    var apiKey = ""
    var indexPath = ""
    lateinit var ultraliteSDK: UltraliteSDK
    lateinit var z100Renderer: Z100Renderer
    lateinit var terminalEmulator: TerminalEmulator

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        apiKey = intent?.getStringExtra(GOOGLE_CLOUD_API_KEY) ?: "AIzaSyAIkVt1c10eZ-A5DdKXH48jtfmRgDAPHsg"
        indexPath = intent?.getStringExtra(INDEX_PATH_STRING) ?: ""
        ultraliteSDK = UltraliteSDK.get(this)
        initTerminalEmulator()

        initLanguageLocale()
        constructRepeatingRecognitionSession()
        startRecording()


        // retriever = Retriever(indexPath)
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

    lateinit var retriever: Retriever

    private val transcriptUpdater =
        TranscriptionResultUpdatePublisher { formattedTranscript: Spanned, updateType: UpdateType ->
            val clearScreenSequence = "\u001B[2J\u001B[H"
            Log.w(TAG, "Transcript: " + formattedTranscript)
            terminalEmulator.append(clearScreenSequence.toByteArray(), clearScreenSequence.toByteArray().size)
            terminalEmulator.append(formattedTranscript.toString().toByteArray(), formattedTranscript.toString().toByteArray().size)
            z100Renderer.renderToZ100(terminalEmulator, 0)
            if (updateType == UpdateType.TRANSCRIPT_FINALIZED) {
                //handleTranscript(formattedTranscript.toString())
            }
        }

    private fun handleTranscript(transcript: String) : String{
        val retrievedDoc: Document? = retriever.query(transcript)
        Log.w(TAG, "Retrieved: " + retrievedDoc?.get("Title"))
        return retrievedDoc?.get("Title") ?: ""
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
        super.onDestroy()
        if (audioRecord != null) {
            audioRecord!!.stop()
        }
        isRunning = false
    }

    private fun initTerminalEmulator() {
        val viewWidth = UltraliteSDK.Canvas.WIDTH
        val viewHeight = UltraliteSDK.Canvas.HEIGHT
        z100Renderer = Z100Renderer(10, android.graphics.Typeface.MONOSPACE, ultraliteSDK)

        // Set to 80 and 24 if you want to enable vttest.
        val newColumns =
            max(4.0, ((viewWidth / z100Renderer.mFontWidth).toInt()).toDouble()).toInt()
        val newRows = Math.max(
            4,
            (viewHeight - z100Renderer.mFontLineSpacingAndAscent) / z100Renderer.mFontLineSpacing
        )

        terminalEmulator = TerminalEmulator(newColumns, newRows, 2000)
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
        val recognizerBuilder = RepeatingRecognitionSession.newBuilder()
            .setSpeechSessionFactory(factory)
            .setSampleRateHz(SAMPLE_RATE)
            .setTranscriptionResultFormatter(SafeTranscriptionResultFormatter(formatterOptions))
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