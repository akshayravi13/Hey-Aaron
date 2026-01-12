package com.example.heyaaron

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.service.notification.NotificationListenerService
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class AaronService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest

    private var isTtsInitialized = false
    private var porcupineManager: PorcupineManager? = null
    private var mediaPlayer: MediaPlayer? = null

    private val client = OkHttpClient()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY

    private val porcupineCallback = PorcupineManagerCallback {
        startSpeakingProcess()
    }

    override fun onCreate() {
        super.onCreate()

        mediaSessionManager =
            getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        textToSpeech = TextToSpeech(this, this)

        createNotificationChannel()
        setupAudioFocus()
        initializePorcupine()
    }

    private fun setupAudioFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
    }

    private fun initializePorcupine() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath("hey_aaron.ppn")
                .setSensitivity(0.7f)
                .build(this, porcupineCallback)
        } catch (e: PorcupineException) {
            Log.e("AaronService", "Porcupine init failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        porcupineManager?.start()

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1, notification)
        }
        return START_STICKY
    }

    private fun startSpeakingProcess() {
        if (audioManager.requestAudioFocus(audioFocusRequest)
            == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            checkSongAndSpeak()
        }
    }

    private fun checkSongAndSpeak() {
        val component = ComponentName(this, AaronService::class.java)
        val controllers = mediaSessionManager.getActiveSessions(component)

        if (controllers.isEmpty()) {
            speak("Yo, you're listening to nothing.")
            return
        }

        for (controller in controllers) {
            val metadata = controller.metadata ?: continue
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

            if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                speak("Yo, you're listening to $title by $artist")
                return
            }
        }

        speak("Yo, something's playing, but I can't tell what.")
    }

    private fun playAssetAudio(assetFileName: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                val afd = assets.openFd(assetFileName)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                start()
                setOnCompletionListener {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest)
                }
            }
        } catch (e: Exception) {
            Log.e("AaronService", "Failed to play asset audio", e)
            playDefaultTts("Something went wrong.")
        }
    }

    private fun speak(text: String) {
        val prefs = getSharedPreferences("AaronPrefs", MODE_PRIVATE)
        val useAaronVoice = prefs.getBoolean("isAaronVoiceEnabled", true)

        if (!useAaronVoice) {
            playDefaultTts(text)
            return
        }

        when (text) {
            "Yo, you're listening to nothing." -> {
                playAssetAudio("aaron_nothing.mp3")
            }

            "Yo, something's playing, but I can't tell what." -> {
                playAssetAudio("aaron_unknown.mp3")
            }

            else -> {
                // For normal song announcements:
                // OPTION A: Use Fish Audio
                if (isNetworkAvailable()) {
                    val safeName = text.replace(Regex("[^a-zA-Z0-9]"), "_")
                    val file = File(cacheDir, "$safeName.mp3")

                    if (file.exists()) {
                        playAudio(file)
                    } else {
                        serviceScope.launch {
                            fetchAndPlayFishAudio(text, file)
                        }
                    }
                } else {
                    playDefaultTts(text)
                }

                // OPTION B (simpler): always use default TTS instead
                // playDefaultTts(text)
            }
        }
    }

    private suspend fun fetchAndPlayFishAudio(text: String, file: File) {
        withContext(Dispatchers.IO) {
            try {
                val json = """
                    {
                      "text": "$text",
                      "reference_id": "${BuildConfig.FISH_AUDIO_VOICE_ID}",
                      "format": "mp3",
                      "latency": "balanced"
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://api.fish.audio/v1/tts")
                    .header("Authorization", "Bearer ${BuildConfig.FISH_AUDIO_API_KEY}")
                    .header("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) { playAudio(file) }
                } else {
                    withContext(Dispatchers.Main) { playDefaultTts(text) }
                }
            } catch (_: IOException) {
                withContext(Dispatchers.Main) { playDefaultTts(text) }
            }
        }
    }

    private fun playAudio(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            }
        }
    }

    private fun playDefaultTts(text: String) {
        if (!isTtsInitialized) return

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            }

            override fun onError(utteranceId: String) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            }
        })
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AaronID")
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "AARON_SERVICE_CHANNEL",
            "Aaron Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, "AARON_SERVICE_CHANNEL")
            .setContentTitle("Hey Aaron")
            .setContentText("Listening for music…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.setLanguage(Locale.US)
            isTtsInitialized = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        porcupineManager?.stop()
        porcupineManager?.delete()
        textToSpeech.shutdown()
        mediaPlayer?.release()
        serviceScope.cancel()
    }
}
