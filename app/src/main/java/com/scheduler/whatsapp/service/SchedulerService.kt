package com.scheduler.whatsapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.scheduler.whatsapp.R
import com.scheduler.whatsapp.model.ScheduledMessage
import com.scheduler.whatsapp.utils.Storage
import java.util.*

class SchedulerService : Service() {

    companion object {
        const val TAG = "SchedulerService"
        const val ACTION_SENDING_COMPLETE = "com.scheduler.whatsapp.SENDING_COMPLETE"
        const val CHANNEL_ID = "scheduler_channel"
        const val NOTIF_ID = 1001

        fun rescheduleAll(context: Context) {
            val messages = Storage.loadMessages(context)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            messages.filter { it.isActive }.forEach { msg ->
                scheduleNext(context, am, msg)
            }
        }

        fun scheduleNext(context: Context, am: AlarmManager, msg: ScheduledMessage) {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("message_id", msg.id)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                msg.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextTime = calculateNextTime(msg)
            Log.d(TAG, "Scheduling ${msg.title} at ${Date(nextTime)}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pi)
            }

            // Update nextSendAt in storage
            val updated = msg.copy(nextSendAt = nextTime)
            Storage.updateMessage(context, updated)
        }

        fun cancelSchedule(context: Context, msg: ScheduledMessage) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                msg.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
        }

        private fun calculateNextTime(msg: ScheduledMessage): Long {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, msg.startHour)
                set(Calendar.MINUTE, msg.startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If time already passed today, calculate next occurrence based on interval
            if (cal.timeInMillis <= now) {
                val intervalMs = msg.intervalHours * 60 * 60 * 1000L
                val diff = now - cal.timeInMillis
                val cycles = (diff / intervalMs) + 1
                cal.timeInMillis += cycles * intervalMs
            }

            return cal.timeInMillis
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SENDING_COMPLETE -> {
                Log.d(TAG, "Sending complete, idle now")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agendador WhatsApp",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificações do agendador de mensagens"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Agendador WhatsApp")
            .setContentText("Serviço ativo")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
