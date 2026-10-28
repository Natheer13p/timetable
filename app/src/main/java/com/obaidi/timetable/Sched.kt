package com.obaidi.timetable

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.util.Calendar

object Sched {
    private const val WINDOW_START = 7 * 60 + 40   // 7:40 صباحاً
    private const val WINDOW_END = 13 * 60 + 30    // 1:30 ظهراً
    private const val DAYS_AHEAD = 30
    private const val ACTION = "com.obaidi.timetable.ALARM"

    fun all(c: Context, json: String) {
        val sp = c.getSharedPreferences("p", 0)
        sp.edit().putString("j", json).putLong("lastSched", System.currentTimeMillis()).apply()
        cancelAll(c)

        val a = try { JSONArray(json) } catch (e: Exception) { JSONArray() }
        if (a.length() == 0) return

        val today = Calendar.getInstance()
        for (offset in 0 until DAYS_AHEAD) {
            val day = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dIdx = day.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            if (dIdx !in 0..4) continue
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optInt("d", -1) != dIdx) continue
                val at = o.optInt("at", -1)
                val text = o.optString("text", "")
                val kind = o.optString("kind", "start")
                if (at < WINDOW_START || at > WINDOW_END || text.isEmpty()) continue
                val code = (dIdx + 1) * 100000 + offset * 1000 + i
                setAt(c, day, at, code, kind, text)
            }
        }
    }

    private fun setAt(c: Context, day: Calendar, minutes: Int, code: Int, kind: String, text: String) {
        val at = (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (at.timeInMillis <= System.currentTimeMillis() + 2000) return

        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            c, code,
            Intent(c, AlarmReceiver::class.java).apply {
                action = ACTION
                putExtra("t", text)
                putExtra("k", kind)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at.timeInMillis, pi), pi)
            remember(c, code)
        } catch (_: Exception) {}
    }

    private fun remember(c: Context, code: Int) {
        val sp = c.getSharedPreferences("p", 0)
        val set = sp.getStringSet("codes", mutableSetOf())!!.toMutableSet()
        set.add(code.toString())
        sp.edit().putStringSet("codes", set).apply()
    }

    fun cancelAll(c: Context) {
        val sp = c.getSharedPreferences("p", 0)
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        for (s in sp.getStringSet("codes", emptySet())!!) {
            val id = s.toIntOrNull() ?: continue
            val pi = PendingIntent.getBroadcast(
                c, id,
                Intent(c, AlarmReceiver::class.java).apply { action = ACTION },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: continue
            try { am.cancel(pi) } catch (_: Exception) {}
            pi.cancel()
        }
        sp.edit().remove("codes").apply()
    }
}
