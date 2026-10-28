package com.obaidi.timetable

import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var w: WebView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        w = WebView(this)
        w.setBackgroundColor(0xFF0A1416.toInt())
        setContentView(w)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.allowFileAccess = true
        w.settings.allowContentAccess = true
        w.addJavascriptInterface(Bridge(this, w), "Native")
        w.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        try {
            if (::w.isInitialized && w.progress >= 100) {
                w.evaluateJavascript("window.refreshStatus&&refreshStatus()", null)
            }
        } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != Bridge.REQ_XML) return

        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            js("window.onXmlError&&onXmlError('تم إلغاء الاستيراد')")
            return
        }
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                js("window.onXmlError&&onXmlError('الملف فارغ أو غير مقروء')")
                return
            }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            js("window.onXmlBegin&&onXmlBegin()")
            val chunk = 120000
            var i = 0
            while (i < b64.length) {
                val end = minOf(i + chunk, b64.length)
                js("window.onXmlPart&&onXmlPart('${b64.substring(i, end)}')")
                i = end
            }
            js("window.onXmlEnd&&onXmlEnd()")
        } catch (e: Exception) {
            js("window.onXmlError&&onXmlError('تعذر قراءة الملف')")
        }
    }

    private fun js(code: String) {
        try {
            if (::w.isInitialized) w.evaluateJavascript(code, null)
        } catch (_: Exception) {}
    }
}

class Bridge(private val a: Activity, private val w: WebView) {
    companion object {
        const val REQ_XML = 1001
    }

    @JavascriptInterface
    fun schedule(j: String) {
        try { Sched.all(a.applicationContext, j) } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun pickXml() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            a.startActivityForResult(i, REQ_XML)
        } catch (e: Exception) {
            try {
                a.startActivityForResult(
                    Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" },
                    REQ_XML
                )
            } catch (e2: Exception) {
                toastJs("لا يوجد تطبيق لاختيار الملفات")
            }
        }
    }

    /* ---------- الشبكة والتحديثات ---------- */

    private fun runJs(code: String) {
        try { w.post { w.evaluateJavascript(code, null) } } catch (_: Exception) {}
    }

    private fun safeCb(cb: String): String = cb.replace(Regex("[^A-Za-z0-9_]"), "")

    @JavascriptInterface
    fun httpGet(url: String, cb: String) {
        val name = safeCb(cb)
        Thread {
            var ok = false
            var body = ""
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 12000
                c.readTimeout = 20000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Timetable/" + appVersionName())
                c.setRequestProperty("Cache-Control", "no-cache")
                val code = c.responseCode
                val s = if (code in 200..299) c.inputStream else c.errorStream
                body = s?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                ok = code in 200..299
                c.disconnect()
            } catch (e: Exception) {
                body = e.message ?: "network"
            }
            runJs("window.$name&&window.$name($ok, ${JSONObject.quote(body)})")
        }.start()
    }

    private fun sendSchool(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            runJs("window.onSchoolError&&onSchoolError('الملف فارغ')")
            return
        }
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        runJs("window.onSchoolBegin&&onSchoolBegin()")
        val chunk = 120000
        var i = 0
        while (i < b64.length) {
            val end = minOf(i + chunk, b64.length)
            runJs("window.onSchoolPart&&onSchoolPart('${b64.substring(i, end)}')")
            i = end
        }
        runJs("window.onSchoolEnd&&onSchoolEnd()")
    }

    @JavascriptInterface
    fun embeddedSchool() {
        Thread {
            try {
                val bytes = a.assets.open("school.xml").use { it.readBytes() }
                sendSchool(bytes)
            } catch (e: Exception) {
                runJs("window.onSchoolError&&onSchoolError('لا توجد قاعدة مدمجة')")
            }
        }.start()
    }

    @JavascriptInterface
    fun downloadSchool(url: String) {
        toastJs("جاري تحديث بيانات المدرسة...")
        Thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 12000
                c.readTimeout = 30000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Timetable/" + appVersionName())
                if (c.responseCode !in 200..299) {
                    runJs("window.onSchoolError&&onSchoolError('تعذر تنزيل قاعدة المدرسة')")
                    return@Thread
                }
                val bytes = c.inputStream.use { it.readBytes() }
                c.disconnect()
                sendSchool(bytes)
            } catch (e: Exception) {
                runJs("window.onSchoolError&&onSchoolError('تعذر الاتصال بالإنترنت')")
            }
        }.start()
    }

    @JavascriptInterface
    fun downloadApk(url: String) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
                openInstallSettings()
                toastJs("اسمح بـ«تثبيت التطبيقات» ثم اضغط تحديث مرة أخرى")
                return
            }
            val dm = a.getSystemService(DownloadManager::class.java)
            if (dm == null) { toastJs("التنزيل غير متاح"); return }
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("تحديث جدول الحصص")
                setDescription("جاري تنزيل الإصدار الجديد...")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(a, null, "update.apk")
            }
            dm.enqueue(req)
            toastJs("جاري التنزيل... سيبدأ التثبيت تلقائياً")
        } catch (e: Exception) {
            toastJs("تعذر تنزيل التحديث")
        }
    }

    @JavascriptInterface
    fun appVersion(): Int =
        try { a.packageManager.getPackageInfo(a.packageName, 0).versionCode } catch (e: Exception) { 0 }

    @JavascriptInterface
    fun appVersionName(): String =
        try { a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    @JavascriptInterface
    fun openInstallSettings() {
        try {
            a.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))
            )
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun test() = testAlert("تجربة: الدرس الأول بعد خمس دقائق، الصف الرابع ب", "warn")
    @JavascriptInterface
    fun testAlert(t: String, k: String) {
        try {
            a.startForegroundService(
                Intent(a, AlarmService::class.java)
                    .putExtra("t", t)
                    .putExtra("k", k)
            )
        } catch (e: Exception) {
            try {
                val nm = a.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(AlarmReceiver.channel(a))
                val open = android.app.PendingIntent.getActivity(
                    a, 0, Intent(a, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val n = android.app.Notification.Builder(a, AlarmReceiver.CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(AlarmReceiver.titleFor(k))
                    .setContentText(t)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                nm.notify(1000, n)
                AlarmReceiver.vibrate(a)
            } catch (_: Exception) {}
        }
    }

    @JavascriptInterface
    fun notifAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            a.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    @JavascriptInterface
    fun askNotif() {
        try {
            a.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName)
            )
        } catch (e: Exception) {
            try {
                a.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}"))
                )
            } catch (_: Exception) {}
        }
    }

    @JavascriptInterface
    fun batteryOptimized(): Boolean =
        try {
            a.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(a.packageName)
        } catch (e: Exception) { false }

    @JavascriptInterface
    fun askBattery() {
        try {
            a.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${a.packageName}")
                )
            )
        } catch (e: Exception) {
            try {
                a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    @JavascriptInterface
    fun openSettings() {
        try {
            a.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}"))
            )
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun autoStart() {
        val targets = listOf(
            arrayOf("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            arrayOf("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            arrayOf("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            arrayOf("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            arrayOf("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            arrayOf("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            arrayOf("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            arrayOf("com.asus.mobilemanager", "com.asus.mobilemanager.entry.StartupActivity"),
            arrayOf("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
        )
        for (t in targets) {
            try {
                a.startActivity(
                    Intent().apply {
                        component = android.content.ComponentName(t[0], t[1])
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return
            } catch (_: Exception) {}
        }
        openSettings()
    }

    @JavascriptInterface
    fun rescheduleNow() {
        val json = a.getSharedPreferences("p", 0).getString("j", null)
        if (!json.isNullOrEmpty()) Sched.all(a.applicationContext, json)
    }

    @JavascriptInterface
    fun alarmCount(): Int =
        try { a.getSharedPreferences("p", 0).getStringSet("codes", emptySet())!!.size } catch (e: Exception) { 0 }

    @JavascriptInterface
    fun setSpeech(on: Boolean) {
        try { a.getSharedPreferences("p", 0).edit().putBoolean("speech", on).apply() } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun speechOn(): Boolean =
        try { a.getSharedPreferences("p", 0).getBoolean("speech", true) } catch (e: Exception) { true }

    private fun toastJs(msg: String) {
        try {
            w.evaluateJavascript("window.toast&&toast('$msg')", null)
        } catch (_: Exception) {}
    }
}
