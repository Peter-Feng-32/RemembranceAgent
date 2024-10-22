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
import com.example.remembranceagent.retrieval.Retriever
import com.example.remembranceagent.ui.GOOGLE_CLOUD_API_KEY
import com.google.audio.CodecAndBitrate
import com.google.audio.NetworkConnectionChecker
import com.google.audio.asr.*
import com.google.audio.asr.TranscriptionResultUpdatePublisher.ResultSource
import com.google.audio.asr.TranscriptionResultUpdatePublisher.UpdateType
import com.google.audio.asr.cloud.CloudSpeechSessionFactory
import com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.DICTATION_DEFAULT
import com.google.audio.asr.SpeechRecognitionModelOptions.SpecificModel.VIDEO
import com.google.audio.asr.TranscriptionResultFormatterOptions.TranscriptColoringStyle.NO_COLORING
import org.apache.lucene.search.ScoreDoc
import javax.inject.Inject


class RemembranceAgentService : Service() {

    companion object {
        const val TAG = "RemembranceAgentService"
    }
    var apiKey = ""

    @RequiresApi(api = Build.VERSION_CODES.P)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initLanguageLocale()
        constructRepeatingRecognitionSession()
        startRecording()

        apiKey = intent?.getStringExtra(GOOGLE_CLOUD_API_KEY) ?: ""
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


    @Inject
    lateinit var retriever: Retriever  // Inject the repository

    private val transcriptUpdater =
        TranscriptionResultUpdatePublisher { formattedTranscript: Spanned, updateType: UpdateType ->
            Log.w("Handle", "Transcript")
            if (updateType == UpdateType.TRANSCRIPT_FINALIZED) {
                handleTranscript(formattedTranscript.toString())
            }
        }

    private fun handleTranscript(transcript: String) {
        val retrievedDoc: ScoreDoc? = retriever.query(transcript)
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

    override fun onDestroy() {
        super.onDestroy()
        if (audioRecord != null) {
            audioRecord!!.stop()
        }
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
                    .setEnableEncoder(true)
                    .setAllowVbr(true)
                    .setCodec(CodecAndBitrate.UNDEFINED)
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