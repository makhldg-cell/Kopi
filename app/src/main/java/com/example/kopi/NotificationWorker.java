package com.example.kopi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
                    showNotification(context, goal.name, p.amount, goal.currency);
                    break; 
                }
            }
        }
        return Result.success();
    }

    private void showNotification(Context context, String goalName, double amount, String currency) {
        String channelId = "kopilka_channel";
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        String text = "Сегодня необходимо отложить " + DateUtils.formatMoney(amount) + " " + currency + " на цель «" + goalName + "»";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_rocket)
            .setContentTitle("Копилка")
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text)) // ТУТ БЫЛА ОШИБКА, ТЕПЕРЬ ИСПРАВЛЕНО
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        manager.notify(1, builder.build());
    }
}
