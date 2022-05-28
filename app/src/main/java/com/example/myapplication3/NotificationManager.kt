package com.example.myapplication3


import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class NotificationManager : NotificationListenerService() {
    var context: Context? = null
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }



    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val notification = sbn.notification
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        val smallIcon = notification.smallIcon
        val largeIcon = notification.getLargeIcon()
        val msgrcv = Intent("Msg")
        msgrcv.putExtra("package", sbn.packageName)
        msgrcv.putExtra("subText", subText)
        msgrcv.putExtra("title", title)
        msgrcv.putExtra("text", text)
        if (sbn.packageName == "com.kakao.talk" && !TextUtils.isEmpty(title)) {
            Log.d(TAG, "<$subText> $title : $text")
            LocalBroadcastManager.getInstance(context!!).sendBroadcast(msgrcv)
        }
    }


    companion object {
        const val TAG = "MyNotificationListener"
    }
}