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

/**
 * Notificações locais do app (nada passa por servidor): dois canais, um para
 * avisos de lançamentos (receita/despesa registrada, editada ou excluída) e
 * outro, mais importante, para alertas de saldo baixo/negativo. No Android 8+
 * o usuário pode ligar/desligar cada canal separadamente nas configurações.
 */
object NotificationHelper {

    // Canal silencioso. O ID mudou de "transactions" para "transactions_silent"
    // porque o Android não deixa alterar o som/importância de um canal depois de
    // criado — só criando outro. O antigo é apagado em createChannels.
    const val CHANNEL_TRANSACTIONS = "transactions_silent"
    private const val OLD_CHANNEL_TRANSACTIONS = "transactions"
    const val CHANNEL_BALANCE = "balance"

    // IDs fixos para o alerta de saldo (um novo substitui o anterior em vez de
    // empilhar); lançamentos usam um ID por transação.
    private const val ID_BALANCE = 1

    /** Cria os canais. Chamado uma vez em App.onCreate (repetir não tem efeito). */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_TRANSACTIONS)
        // IMPORTANCE_LOW: aparece na barra de notificações, mas sem som nem vibração.
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
        // Android 13+: sem a permissão concedida, não mostra nada (e não quebra).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Tocar na notificação abre o app (pela tela inicial normal).
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
            // Antes do Android 8 não existem canais: o som é controlado aqui.
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
