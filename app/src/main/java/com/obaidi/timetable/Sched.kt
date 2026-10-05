package com.obaidi.timetable

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.util.Calendar

object Sched {
    private fun pi(c: Context, id: Int, flags: Int): PendingIntent? =
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
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY + d)
            set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 7)
        val id = d * 10 + l
        val i = Intent(c, AlarmReceiver::class.java).putExtra("d", d).putExtra("l", l).putExtra("m", m).putExtra("t", t)
        val p = PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val show = PendingIntent.getActivity(c, 100, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), p)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val t = i.getStringExtra("t") ?: "حان وقت الحصة"
        Sched.set(c, i.getIntExtra("d", 0), i.getIntExtra("l", 0), i.getIntExtra("m", 0), t)
        c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("t", t))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        c.getSharedPreferences("p", 0).getString("j", null)?.let { Sched.all(c, it) }
    }
}
