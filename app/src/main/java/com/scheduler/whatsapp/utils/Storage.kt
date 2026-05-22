package com.scheduler.whatsapp.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.scheduler.whatsapp.model.AppSettings
import com.scheduler.whatsapp.model.ScheduledMessage

object Storage {
    private const val PREFS_NAME = "whatsapp_scheduler"
    private const val KEY_MESSAGES = "messages"
    private const val KEY_SETTINGS = "settings"
    private val gson = Gson()

    fun saveMessages(context: Context, messages: List<ScheduledMessage>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MESSAGES, gson.toJson(messages)).apply()
    }

    fun loadMessages(context: Context): MutableList<ScheduledMessage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MESSAGES, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<ScheduledMessage>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun loadSettings(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            gson.fromJson(json, AppSettings::class.java)
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun updateMessage(context: Context, message: ScheduledMessage) {
        val messages = loadMessages(context)
        val index = messages.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            messages[index] = message
        } else {
            messages.add(message)
        }
        saveMessages(context, messages)
    }

    fun deleteMessage(context: Context, id: String) {
        val messages = loadMessages(context)
        messages.removeAll { it.id == id }
        saveMessages(context, messages)
    }
}
