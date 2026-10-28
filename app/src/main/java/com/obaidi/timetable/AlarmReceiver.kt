package com.obaidi.timetable

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val text = i.getStringExtra("t") ?: return
        val kind = i.getStringExtra("k") ?: "start"

        val wl = c.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tt:recv").apply { acquire(120 * 1000L) }

        try {
            val svc = Intent(c, AlarmService::class.java)
                .putExtra("t", text)
                .putExtra("k", kind)
            c.startForegroundService(svc)
        } catch (e: Exception) {
            inlineAlert(c, text, kind)
        } finally {
            try { if (wl.isHeld) wl.release() } catch (_: Exception) {}
        }

        // تجديد جدولة الأيام القادمة (نافذة متجددة) دون إثقال كل نبضة
        try {
            val sp = c.getSharedPreferences("p", 0)
            if (System.currentTimeMillis() - sp.getLong("lastSched", 0L) > 12 * 60 * 60 * 1000L) {
                val j = sp.getString("j", null)
                if (!j.isNullOrEmpty()) Sched.all(c, j)
            }
        } catch (_: Exception) {}
    }

    private fun inlineAlert(c: Context, text: String, kind: String) {
        try {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel(c))
            val open = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val title = titleFor(kind)
            val n = Notification.Builder(c, CHANNEL).apply {
                setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                setContentTitle(title)
                setContentText(text)
                setStyle(Notification.BigTextStyle().bigText(text))
                setContentIntent(open)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setCategory(Notification.CATEGORY_ALARM)
                setPriority(Notification.PRIORITY_HIGH)
                setAutoCancel(true)
            }.build()
            nm.notify(1000 + kind.hashCode().mod(90), n)
        } catch (_: Exception) {}

        vibrate(c)

        try {
            ToneGenerator(AudioManager.STREAM_ALARM, 100)
                .startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)
        } catch (_: Exception) {}

        if (!c.getSharedPreferences("p", 0).getBoolean("speech", true)) return

        try {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(c) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.forLanguageTag("ar")
                    tts?.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fb")
                }
            }
            Handler(Looper.getMainLooper()).postDelayed({
                try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
            }, 45_000)
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL = "alarms"

        fun titleFor(kind: String): String = when (kind) {
            "warn" -> "⏰ تنبيه قبل الحصة"
            "end" -> "✅ انتهت الفترة"
            else -> "⏰ بدأت الحصة"
        }

        fun channel(c: Context): NotificationChannel =
            NotificationChannel(CHANNEL, "تنبيه الحصص", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                enableVibration(true)
                vibrationPattern = longArrayOf(300, 700, 300, 700)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                description = "تنبيهات قبل الحصص وأثناءها"
            }

        fun vibrate(c: Context) {
            try {
                val pattern = longArrayOf(300, 700, 300, 700, 300)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    val v = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    @Suppress("DEPRECATION")
                    v?.vibrate(pattern, -1)
                }
            } catch (_: Exception) {}
        }
    }
}
