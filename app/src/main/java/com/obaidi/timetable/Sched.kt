package com.obaidi.timetable

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.util.Calendar

object Sched {
    private const val MINUTES_BEFORE = 5 // التنبيه قبل الحصة بدقائق

    fun pi(c: Context, id: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(c, id, Intent(c, AlarmReceiver::class.java), flags or PendingIntent.FLAG_IMMUTABLE)

    fun all(c: Context, json: String) {
        c.getSharedPreferences("p", 0).edit().putString("j", json).apply()
        val am = c.getSystemService(AlarmManager::class.java)
        for (i in 0 until 60) pi(c, i, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
        val a = JSONArray(json)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            set(c, o.getInt("d"), o.getInt("c"), o.getInt("m"), o.getString("t"))
        }
    }

    fun set(c: Context, d: Int, l: Int, m: Int, t: String) {
        // وقت الحصة بالدقيقتين
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY + d)
            set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 7)

        // ID فريد لكل حصة (بما أننا نضبط ضابطين)
        val baseId = d * 10 + l

        // ضابط 1: وقت الحصة exactly
        val cal1 = cal.clone().apply {
            // لا تغيير، الساعة بالضبط
        }
        val i1 = Intent(c, AlarmReceiver::class.java).apply {
            action = "EXACT"
            putExtra("d", d).putExtra("l", l).putExtra("m", m).putExtra("t", t)
        }
        val p1 = PendingIntent.getBroadcast(c, baseId * 2, i1, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // ضابط 2: قبل الحصة بـ MINUTES_BEFORE دقيقة
        val cal2 = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY + d)
            // نطرح الدقائق
            val currentMinutes = cal[Calendar.MINUTE]
            val newMinutes = currentMinutes - MINUTES_BEFORE
            if (newMinutes < 0) {
                cal2.add(Calendar.HOUR_OF_DAY, -1)
                cal2.add(Calendar.MINUTE, 60 + newMinutes)
            } else {
                cal2.add(Calendar.MINUTE, newMinutes)
            }
            cal2[Calendar.SECOND] = 0; cal2[Calendar.MILLISECOND] = 0
        }
        val i2 = Intent(c, AlarmReceiver::class.java).apply {
            action = "BEFORE"
            putExtra("d", d).putExtra("l", l).putExtra("m", m).putExtra("t", t)
        }
        val p2 = PendingIntent.getBroadcast(c, baseId*2 + 1, i2, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // جدولة الضابطين
        val am = c.getSystemService(AlarmManager::class.java)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, p1), p1)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(cal2.timeInMillis, p2), p2)
    }
}