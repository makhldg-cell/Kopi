package com.example.kopi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationWorker extends Worker {
    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);
        String today = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());

        List<Goal> goals = db.goalDao().getAllGoals();
        for (Goal goal : goals) {
            if (goal.isFrozen) continue;
            List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
            for (Payment p : payments) {
                if (p.date.equals(today) && p.status.equals("pending")) {
                    double amountToPay = p.remainingAmount > 0 ? p.remainingAmount : p.amount;
                    showNotification(context, goal, p, amountToPay);
                    break; 
                }
            }
        }
        return Result.success();
    }

    private void showNotification(Context context, Goal goal, Payment p, double amount) {
        String channelId = "kopilka_custom_sound_channel_v1"; 
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
            NotificationChannel channel = new NotificationChannel(channelId, "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_sound);
            channel.setSound(soundUri, audioAttributes);
            manager.createNotificationChannel(channel);
        }

        // Открыть приложение
        Intent openIntent = new Intent(context, GoalDetailActivity.class);
        openIntent.putExtra("goal_id", goal.id);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openPending = PendingIntent.getActivity(context, goal.id, openIntent, PendingIntent.FLAG_IMMUTABLE);

        // Кнопка "Внести" (будет обработана Receiver'ом)
        Intent actionIntent = new Intent(context, NotificationActionReceiver.class);
        actionIntent.putExtra("goal_id", goal.id);
        actionIntent.putExtra("payment_id", p.id);
        PendingIntent actionPending = PendingIntent.getBroadcast(context, p.id, actionIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String text = "Сегодня необходимо отложить " + DateUtils.formatMoney(amount) + " " + goal.currency;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_rocket)
            .setContentTitle("Копилка: " + goal.name)
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.checkbox_on_background, "Внести", actionPending) // Кнопка!
            .setAutoCancel(true);

        manager.notify(goal.id, builder.build());
    }
}
