package com.obaidi.timetable

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class AlarmService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var text = ""
    private var kind = "start"
    private var speech = true
    private var tries = 0
    private var spoken = false
    private val h = Handler(Looper.getMainLooper())
    private var wl: PowerManager.WakeLock? = null
    private var afm: AudioManager? = null
    private var afr: AudioFocusRequest? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        text = i?.getStringExtra("t") ?: "حان وقت الحصة"
        kind = i?.getStringExtra("k") ?: "start"
        speech = getSharedPreferences("p", 0).getBoolean("speech", true)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(AlarmReceiver.channel(this))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, AlarmReceiver.CHANNEL).apply {
            setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            setContentTitle(AlarmReceiver.titleFor(kind))
            setContentText(text)
            setStyle(Notification.BigTextStyle().bigText(text))
            setContentIntent(open)
            setVisibility(Notification.VISIBILITY_PUBLIC)
            setCategory(Notification.CATEGORY_ALARM)
            setPriority(Notification.PRIORITY_HIGH)
            setAutoCancel(true)
        }.build()

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            try { startForeground(1, n) } catch (_: Exception) {}
        }

        try {
            wl = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tt:svc").apply { acquire(3 * 60 * 1000L) }
        } catch (_: Exception) {}

        afm = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            try { afm?.requestAudioFocus(afr!!) } catch (_: Exception) {}
        }

        AlarmReceiver.vibrate(this)
        try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100)
                .startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
        } catch (_: Exception) {}

        if (speech) {
            h.postDelayed({ tts = TextToSpeech(this, this) }, 800)
            h.postDelayed({ stopSelfSafely() }, 2 * 60 * 1000L)
        } else {
            h.postDelayed({ stopSelfSafely() }, 8 * 1000L)
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            if (++tries < 4) {
                h.postDelayed({
                    try { tts?.shutdown() } catch (_: Exception) {}
                    tts = TextToSpeech(this, this)
                }, 2000)
            } else {
                stopSelfSafely()
            }
            return
        }
        t.language = Locale.forLanguageTag("ar")
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(u: String?) { retryOrStop() }
            override fun onError(u: String?, errorCode: Int) { retryOrStop() }
            override fun onDone(u: String?) { stopSelfSafely() }
        })
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "b")
    }

    private fun retryOrStop() {
        if (!spoken) {
            spoken = true
            try { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "b") } catch (_: Exception) { stopSelfSafely() }
        } else {
            stopSelfSafely()
        }
    }

    private fun stopSelfSafely() {
        h.post {
            try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) afm?.abandonAudioFocusRequest(afr!!) } catch (_: Exception) {}
            try { stopForeground(STOP_FOREGROUND_DETACH) } catch (_: Exception) {}
            stopSelf()
        }
    }

    override fun onDestroy() {
        try { if (wl?.isHeld == true) wl?.release() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }
}
