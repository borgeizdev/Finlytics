package com.borgeiz.meutcc2026.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.borgeiz.meutcc2026.R

object NotificationHelper {

    const val CHANNEL_TRANSACTIONS = "transactions_silent"
    private const val OLD_CHANNEL_TRANSACTIONS = "transactions"
    const val CHANNEL_BALANCE = "balance"

    private const val ID_BALANCE = 1

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_TRANSACTIONS)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRANSACTIONS, "Lançamentos", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Avisos quando uma receita ou despesa é registrada, editada ou excluída" }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BALANCE, "Saldo baixo", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alerta quando o saldo da conta fica perto de zero ou negativo" }
        )
    }

    fun notifyTransaction(context: Context, id: Int, title: String, text: String) =
        post(context, CHANNEL_TRANSACTIONS, id, title, text)

    fun notifyBalance(context: Context, title: String, text: String) =
        post(context, CHANNEL_BALANCE, ID_BALANCE, title, text)

    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setPriority(
                if (channel == CHANNEL_BALANCE) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_LOW
            )
            .setSilent(channel == CHANNEL_TRANSACTIONS)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
