package com.obaidi.timetable

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0L) return
        val dm = c.getSystemService(DownloadManager::class.java) ?: return
        var ok = false
        try {
            dm.query(DownloadManager.Query().setFilterById(id))?.use { cur ->
                if (cur.moveToFirst()) {
                    val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    ok = status == DownloadManager.STATUS_SUCCESSFUL
                }
            }
        } catch (_: Exception) {}
        if (!ok) {
            Toast.makeText(c, "تعذر تنزيل التحديث", Toast.LENGTH_LONG).show()
            return
        }
        val uri = try { dm.getUriForDownloadedFile(id) } catch (e: Exception) { null } ?: return
        try {
            c.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Toast.makeText(c, "افتح ملف التحديث من مجلد التنزيلات لتثبيته", Toast.LENGTH_LONG).show()
        }
    }
}
