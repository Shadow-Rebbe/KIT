package com.kit.prototype

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class NotificationWorker(ctx: Context, params: WorkerParameters): Worker(ctx, params) {
    override fun doWork(): Result {
        val store = KitStore(applicationContext)
        val deck = store.decks.filter { it.cardIds.isNotEmpty() }.randomOrNull() ?: return Result.success()
        val card = deck.cardIds.mapNotNull(store::card).randomOrNull() ?: return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel("kit_suggestions","KIT suggestions",NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java).apply { putExtra("openDeckId", deck.id); putExtra("openCardId",card.id); flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pi = PendingIntent.getActivity(applicationContext, card.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prompt = card.items.firstOrNull()?.text ?: "A card is ready."
        val n = NotificationCompat.Builder(applicationContext,"kit_suggestions")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(card.name)
            .setContentText(prompt)
            .setStyle(NotificationCompat.BigTextStyle().bigText(prompt))
            .setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(applicationContext).notify(card.id.hashCode(), n)
        return Result.success()
    }
}
