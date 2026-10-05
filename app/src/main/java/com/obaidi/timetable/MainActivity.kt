package com.obaidi.timetable

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val w = WebView(this)
        setContentView(w)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.addJavascriptInterface(Bridge(this), "Native")
        w.loadUrl("file:///android_asset/index.html")
    }
}

class Bridge(private val c: Context) {
    @JavascriptInterface fun schedule(j: String) { Sched.all(c, j) }
    @JavascriptInterface fun test() {
        c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("t", "الدرس الأول بعد خمس دقائق، خامس علمي ب"))
    }
}
