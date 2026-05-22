package com.scheduler.whatsapp.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scheduler.whatsapp.utils.Storage

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra("message_id") ?: return
        Log.d("AlarmReceiver", "Alarm triggered for message: $messageId")

        val messages = Storage.loadMessages(context)
        val message = messages.find { it.id == messageId } ?: return

        if (!message.isActive) return

        // Check if accessibility service is running
        if (WhatsAppAccessibilityService.isRunning) {
            WhatsAppAccessibilityService.instance?.startSendingMessage(messageId)
        } else {
            Log.w("AlarmReceiver", "Accessibility service not running!")
        }

        // Update lastSentAt
        val updated = message.copy(lastSentAt = System.currentTimeMillis())
        Storage.updateMessage(context, updated)

        // Schedule next alarm
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        SchedulerService.scheduleNext(context, am, updated)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot complete - rescheduling alarms")
            SchedulerService.rescheduleAll(context)
        }
    }
}
