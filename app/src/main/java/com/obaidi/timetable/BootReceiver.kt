package com.obaidi.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val json = context.getSharedPreferences("p", 0).getString("j", null)
                if (!json.isNullOrEmpty()) {
                    Sched.all(context, json)
                }
            }
        }
    }
}
