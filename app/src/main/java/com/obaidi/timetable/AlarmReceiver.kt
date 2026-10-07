package com.obaidi.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.BitmapFactory
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val t = i.getStringExtra("t") ?: "حان وقت الحصة"
        val action = i.action

        // إنشاء قناة تنبيه إذا لم تكن موجودة
        val nm = c.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel("a", "تنبيه الحصص", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(android.net.Uri.parse("content://settings/system/notification_sound"), null)
                enableVibration(true)
                vibrationPattern = longArrayOf(200, 1000, 200, 1000)
            })
        }

        // نية فتح التطبيق
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        // نية إغلاق التنبيه
        val dismiss = PendingIntent.getService(c, 0, Intent(c, AlarmService::class.java).setAction("DISMISS"), PendingIntent.FLAG_UPDATE_CURRENT).apply {
            flags = PendingIntent.FLAG_IMMUTABLE
        }

        when (action) {
            "BEFORE" -> {
                // تنبيه قبل 5 دقائق
                val textBefore = "تنبيه: الحصة قادمة بعد ${MINUTES_BEFORE} دقائق، ${t}"
                val builder = Notification.Builder(c, "a").apply {
                    setContentTitle("⏰ تنبيه حصة")
                    setContentText(textBefore)
                    setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    setStyle(Notification.BigTextStyle().bigText(textBefore))
                    setContentIntent(open)
                    setDeleteIntent(dismiss)
                    setVisibility(VISIBILITY_PUBLIC)
                    setOngoing(false)
                    setAutoCancel(true)
                    setPriority(Notification.PRIORITY_HIGH)
                }
                nm.notify(1, builder.build())

                // صوت وهزاز
                try { ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) } catch (e: Exception) {}
                val vib = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vib.vibrate(longArrayOf(200, 1000, 200, 1000))

                // نطق
                try {
                    val tts = TextToSpeech(c, object : TextToSpeech.OnInitListener {
                        override fun onInit(status: Int) {
                            if (status != TextToSpeech.SUCCESS) return
                            tts.language = java.util.Locale("ar")
                            tts.setAudioAttributes(android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            tts.speak(textBefore, TextToSpeech.QUEUE_FLUSH, null, "1")
                        }
                    })
                } catch (e: Exception) {}
            }
            "EXACT" -> {
                // وقت الحصة genau
                val textExact = "حان وقت الحصة، ${t}"
                val builder = Notification.Builder(c, "a").apply {
                    setContentTitle("⏰ بداية الحصة")
                    setContentText(textExact)
                    setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    setStyle(Notification.BigTextStyle().bigText(textExact))
                    setContentIntent(open)
                    setDeleteIntent(dismiss)
                    setVisibility(VISIBILITY_PUBLIC)
                    setOngoing(true)
                    setAutoCancel(false)
                    setPriority(Notification.PRIORITY_HIGH)
                }
                nm.notify(2, builder.build())

                // صوت وهزاز
                try { ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) } catch (e: Exception) {}
                val vib = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vib.vibrate(longArrayOf(500, 500, 500, 500))

                // نطق
                try {
                    val tts = TextToSpeech(c, object : TextToSpeech.OnInitListener {
                        override fun onInit(status: Int) {
                            if (status != TextToSpeech.SUCCESS) return
                            tts.language = java.util.Locale("ar")
                            tts.setAudioAttributes(android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            tts.speak(textExact, TextToSpeech.QUEUE_FLUSH, null, "1")
                        }
                    })
                } catch (e: Exception) {}
            }
        }
    }
}