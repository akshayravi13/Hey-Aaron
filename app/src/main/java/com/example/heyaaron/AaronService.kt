package com.example.heyaaron
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class AaronService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var textToSpeech: TextToSpeech
    private var isTtsInitialized = false
    private lateinit var audioManager: AudioManager

    // Porcupine Variables
    private var porcupineManager: PorcupineManager? = null
    // Replace with your actual key if needed, but this is the one you pasted
    private val accessKey = com.example.heyaaron.BuildConfig.PICOVOICE_ACCESS_KEY

    private lateinit var audioFocusRequest: AudioFocusRequest

    // The Callback: What happens when "Hey Aaron" is heard
    private val porcupineCallback = PorcupineManagerCallback { keywordIndex ->
        Log.d("AaronService", "WAKE WORD DETECTED! Index: $keywordIndex")
        startSpeakingProcess()
    }

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            // Optional: Stop speaking if we lose focus entirely
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Setup System Services
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        textToSpeech = TextToSpeech(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 2. Setup Notification Channel
        createNotificationChannel()

        // 3. Setup Audio Focus (The "Ducking" logic)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(afChangeListener)
            .build()

        // 4. Initialize the Wake Word Engine
        initializePorcupine()
    }

    private fun initializePorcupine() {
        if (porcupineManager != null) {
            Log.d("AaronService", "Porcupine already initialized, skipping")
            return
        }

        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath("hey_aaron.ppn")
                .setSensitivity(0.7f)
                .build(this, porcupineCallback)

            Log.d("AaronService", "Porcupine initialized successfully")

        } catch (e: PorcupineException) {
            Log.e("AaronService", "Failed to initialize Porcupine", e)
        }
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "ACTION_TEST_VOICE") {
            Log.d("AaronService", "Test Voice Button Clicked")
            startSpeakingProcess()
        }

        // Start Listening for the Wake Word
        try {
            porcupineManager?.start()
            Log.d("AaronService", "Porcupine started listening...")
        } catch (e: PorcupineException) {
            Log.e("AaronService", "Porcupine failed to start", e)
        }

        // Create the Notification to keep the service alive
        val notification = createNotification()

        // --- CRITICAL FIX FOR ANDROID 14 MICROPHONE ACCESS ---
        if (Build.VERSION.SDK_INT >= 34) {
            // We combine MEDIA_PLAYBACK and MICROPHONE flags using 'or'
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(1, notification, serviceType)
        } else {
            startForeground(1, notification)
        }
        // -----------------------------------------------------

        return START_STICKY
    }

    private fun startSpeakingProcess() {
        // 1. Duck the Audio (Lower Music Volume)
        val result = audioManager.requestAudioFocus(audioFocusRequest)

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // 2. Check Song and Speak
            checkSongAndSpeak()
        }
    }

    private fun checkSongAndSpeak() {
        val componentName = ComponentName(this, AaronService::class.java)
        try {
            val activeMediaControllers = mediaSessionManager.getActiveSessions(componentName)

            if (activeMediaControllers.isEmpty()) {
                speak("I don't see any music playing, yo.")
                return
            }

            for (controller in activeMediaControllers) {
                val metadata = controller.metadata
                if (metadata != null) {
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

                    if (!title.isNullOrEmpty() && !artist.isNullOrEmpty()) {
                        speak("You are listening to $title by $artist")
                        return
                    }
                }
            }
            speak("I see the app, but no song info.")

        } catch (e: SecurityException) {
            Log.e("AaronService", "Permission missing!", e)
            speak("Yo, I need permission to read notifications!")
        }
    }

    private fun speak(text: String) {
        if (isTtsInitialized) {

            // Set up a listener to know EXACTLY when the voice finishes
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}

                override fun onDone(utteranceId: String) {
                    // VOICE FINISHED -> RESTORE MUSIC VOLUME
                    Log.d("AaronService", "Speech done, restoring audio.")
                    audioManager.abandonAudioFocusRequest(audioFocusRequest)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest)
                }
            })

            // Actually Speak
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AaronID")
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            "AARON_SERVICE_CHANNEL",
            "Aaron Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "AARON_SERVICE_CHANNEL")
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Hey Aaron")
            .setContentText("Listening for music...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("AaronService", "English language not installed!")
            } else {
                isTtsInitialized = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        porcupineManager?.stop()
        porcupineManager?.delete()
        textToSpeech.shutdown()
    }
}