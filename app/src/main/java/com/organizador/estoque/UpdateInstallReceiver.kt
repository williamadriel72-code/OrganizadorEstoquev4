package com.organizador.estoque

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmation == null) {
                    Toast.makeText(context, "Não foi possível abrir a confirmação da atualização.", Toast.LENGTH_LONG).show()
                    return
                }
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirmation)
                } catch (_: Throwable) {
                    showInstallNotification(context, confirmation)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Atualização instalada.", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(
                    context,
                    "Falha ao instalar atualização: ${message ?: "erro do instalador"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showInstallNotification(context: Context, confirmation: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                BackgroundUpdateWorker.CHANNEL_ID,
                "Atualizações do aplicativo",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val pending = PendingIntent.getActivity(
            context,
            9104,
            confirmation,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, BackgroundUpdateWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_inventory)
            .setContentTitle("Atualização pronta para instalar")
            .setContentText("Toque para confirmar a atualização do AWS Gestão de Estoque.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()
        try {
            manager.notify(BackgroundUpdateWorker.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
