package com.obaidi.timetable

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
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
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        text = i?.getStringExtra("t") ?: "حان وقت الحصة"
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel("a", "تنبيه الحصص", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        nm.createNotificationChannel(ch)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "a").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ تنبيه حصة").setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tt:w").acquire(40000)
        try { ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) } catch (e: Exception) {}
        h.postDelayed({ tts = TextToSpeech(this, this) }, 1200)
        h.postDelayed({ stop() }, 30000)
        return START_NOT_STICKY
    }

    override fun onInit(s: Int) {
        val t = tts ?: return
        if (s != TextToSpeech.SUCCESS) { stop(); return }
        t.language = Locale("ar")
        t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onError(u: String?) { stop() }
            override fun onDone(u: String?) { if (u == "2") stop() }
        })
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "1")
        t.speak(text, TextToSpeech.QUEUE_ADD, null, "2")
    }

    private fun stop() { h.post { stopForeground(Service.STOP_FOREGROUND_DETACH); stopSelf() } }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
