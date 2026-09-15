package com.organizador.scanner

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val FOLGA_PREFS = "bora_folga_reminders"
private const val FOLGA_CHANNEL_ID = "bora_michael_folgas_v1"
private const val FOLGA_ALARM_ACTION = "com.boramichael.hihi.FOLGA_REMINDER"
private const val FOLGA_ALARM_REQUEST = 27310
private const val FOLGA_NOTIFICATION_ID = 27311
private const val KEY_AVAILABLE = "has_availability"
private const val KEY_LAST_SLOT = "last_slot"
private const val KEY_UPDATED_AT = "availability_updated_at"
private const val EXTRA_SLOT = "slot"
private const val EXTRA_OPEN_FOLGAS = "open_folgas"

object FolgaReminderScheduler {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val slotFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")

    fun setAvailability(context: Context, available: Boolean) {
        context.getSharedPreferences(FOLGA_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AVAILABLE, available)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
        scheduleNext(context)
    }

    fun scheduleNext(context: Context) {
        val now = ZonedDateTime.now(zone).plusSeconds(10)
        val next = nextReminderAfter(now)
        val slot = next.format(slotFormatter)
        val intent = Intent(context, FolgaReminderReceiver::class.java).apply {
            action = FOLGA_ALARM_ACTION
            putExtra(EXTRA_SLOT, slot)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            FOLGA_ALARM_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = next.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            @Suppress("DEPRECATION")
            alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun nextReminderAfter(now: ZonedDateTime): ZonedDateTime {
        for (dayOffset in 0..8) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val times = if (weekend) {
                listOf(LocalTime.of(10, 0), LocalTime.of(15, 0), LocalTime.of(21, 30), LocalTime.of(23, 0))
            } else {
                listOf(LocalTime.of(15, 0), LocalTime.of(21, 30), LocalTime.of(23, 0))
            }
            for (time in times) {
                val candidate = ZonedDateTime.of(LocalDateTime.of(date, time), zone)
                if (candidate.isAfter(now)) return candidate
            }
        }
        return now.plusDays(1).withHour(15).withMinute(0).withSecond(0).withNano(0)
    }
}

class FolgaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        FolgaReminderScheduler.scheduleNext(context)

        val prefs = context.getSharedPreferences(FOLGA_PREFS, Context.MODE_PRIVATE)
        val available = prefs.getBoolean(KEY_AVAILABLE, false)
        if (!available) return

        val slot = intent?.getStringExtra(EXTRA_SLOT).orEmpty()
        if (slot.isNotBlank() && prefs.getString(KEY_LAST_SLOT, null) == slot) return

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        createChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_FOLGAS, true)
        }
        val openPending = PendingIntent.getActivity(
            context,
            27312,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Ainda dá tempo de folgar 😎"
        val text = "Tem certeza que você quer trabalhar no próximo turno? 😂 Se quiser descansar, é só agendar sua folga. Ainda tem data disponível!"

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, FOLGA_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
        }

        val notification = builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(android.app.Notification.Action.Builder(null, "AGENDAR FOLGA", openPending).build())
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(FOLGA_NOTIFICATION_ID, notification)

        prefs.edit().putString(KEY_LAST_SLOT, slot).apply()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            FOLGA_CHANNEL_ID,
            "Lembretes de folga",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos para lembrar quando ainda há data disponível para folga"
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}

class FolgaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        FolgaReminderScheduler.scheduleNext(context)
    }
}
