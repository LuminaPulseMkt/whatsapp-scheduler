package com.scheduler.whatsapp.model

import java.util.UUID

data class ScheduledMessage(
    val id: String = UUID.randomUUID().toString(),
    val title: String,           // Label para identificar no app
    val message: String,         // Texto da mensagem
    val groups: List<String>,    // Nomes exatos dos grupos/comunidades
    val intervalHours: Int,      // Intervalo em horas
    val startHour: Int,          // Hora de início (0-23)
    val startMinute: Int,        // Minuto de início (0-59)
    val isActive: Boolean = true,
    val lastSentAt: Long = 0L,
    val nextSendAt: Long = 0L
)

data class AppSettings(
    val delayBetweenGroupsMs: Long = 8000L,   // Delay entre grupos (ms)
    val typingDelayMs: Long = 1500L,           // Simular digitação (ms)
    val randomDelayMaxMs: Long = 3000L         // Delay aleatório extra (ms)
)
