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
                    // Берем сумму к оплате (если это частичный платеж, то остаток)
                    double amountToPay = p.remainingAmount > 0 ? p.remainingAmount : p.amount;
                    showNotification(context, goal.name, amountToPay, goal.currency, goal.id);
                    break; 
                }
            }
        }
        return Result.success();
    }

    private void showNotification(Context context, String goalName, double amount, String currency, int goalId) {
        // Уникальный ID канала, чтобы телефон применил новый звук
        String channelId = "kopilka_custom_sound_channel_v1"; 
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 1. Настраиваем параметры звука
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            // 2. Создаем канал уведомлений
            NotificationChannel channel = new NotificationChannel(channelId, "Напоминания", NotificationManager.IMPORTANCE_HIGH);

            // 3. Привязываем твой звук my_sound.mp3 из папки raw
            Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_sound);
            channel.setSound(soundUri, audioAttributes);
            
            manager.createNotificationChannel(channel);
        }

        // При нажатии на уведомление открываем экран этой цели
        Intent intent = new Intent(context, GoalDetailActivity.class);
        intent.putExtra("goal_id", goalId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, goalId, intent, PendingIntent.FLAG_IMMUTABLE);

        String text = "Сегодня необходимо отложить " + DateUtils.formatMoney(amount) + " " + currency + " на цель «" + goalName + "»";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_rocket)
            .setContentTitle("Копилка")
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        manager.notify(goalId, builder.build());
    }
}
