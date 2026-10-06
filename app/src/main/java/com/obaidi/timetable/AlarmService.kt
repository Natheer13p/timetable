package com.obaidi.timetable

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.Vibrator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.PendingIntent
import java.util.Locale
import java.util.concurrent.TimeUnit

class AlarmService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var text = ""
    private val h = Handler(Looper.getMainLooper())
    private var notificationId = 1

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        text = i?.getStringExtra("t") ?: "حان وقت الحصة"
        val nm = getSystemService(NotificationManager::class.java)

        // إنشاء قناة تنبيه بصوت افتراضي
        val ch = NotificationChannel("a", "تنبيه الحصص", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            setDescription("تنبيهات حصص جدول العبيدي")
        }
        nm.createNotificationChannel(ch)

        // نية فتح التطبيق عند الضغط على التنبيه
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        // نية إغلاق التنبيه (إيقاف الخدمة)
        val dismiss = PendingIntent.getService(this, 0, Intent(this, AlarmService::class.java).setAction("DISMISS"), PendingIntent.FLAG_UPDATE_CURRENT).apply {
            flags = PendingIntent.FLAG_IMMUTABLE
        }

        // اهتزاز قصير
        val vib = getSystemService(Vibrator::class.java) ?: run { return START_NOT_STICKY }
        vib.vibrate(longArrayOf(200, 1000, 200, 1000))

        // صوت تنبيه
        try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900)
        } catch (e: Exception) {
            Log.e("AlarmSvc", "Tone error", e)
        }

        // كلام صوتي
        try {
            tts = TextToSpeech(this, this)
            h.postDelayed({ tts = TextToSpeech(this, this) }, 1200)
        } catch (e: Exception) {}

        //.Builder التنبيه
        val builder = Notification.Builder(this, "a").apply {
            setContentTitle("⏰ تنبيه حصة")
            setContentText(text)
            setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            setStyle(Notification.BigTextStyle().bigText(text))
            setContentIntent(open)
            setDeleteIntent(dismiss)
            setVisibility(VISIBILITY_PUBLIC)       // يظهر على شاشة القفل
            setOngoing(true)                      // يبقى في منطقة الحالة
            setAutoCancel(false)                  // لا يختفي بالضغط خارجه
        }

        // البقاء في foreground service 5 دقائق ثم يتوقف (يمكنك التعديل)
        h.postDelayed({ stopForeground(Service.STOP_RETAIN_STATUS); stopSelf() }, 5 * 60 * 1000)

        if (Build.VERSION.SDK_INT >= 29) startForeground(notificationId, builder, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(notificationId, builder)

        return START_STICKY
    }

    override fun onInit(s: Int) {
        val t = tts ?: run { if (s != TextToSpeech.SUCCESS) { stopSelf(); return } }
        t.language = Locale("ar")
        t.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onError(u: String?) { stopSelf() }
            override fun onDone(u: String?) { if (u == "2") stopSelf() }
        })
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "1")
        t.speak(text, TextToSpeech.QUEUE_ADD, null, "2")
    }

    private fun stop() { h.post { stopForeground(Service.STOP_FOREGROUND_DETACH); stopSelf() } }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    // معالج الإجراءDismiss من notification
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "DISMISS") {
            stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }
}