package com.example.kopi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppDatabase db = AppDatabase.getInstance(context);
        String today = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());

        List<Goal> goals = db.goalDao().getAllGoals();
        for (Goal goal : goals) {
            if (goal.isFrozen) continue;

            List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
            for (Payment p : payments) {
                // Если сегодня есть платеж
                if (p.date.equals(today) && p.status.equals("pending")) {
                    // Сумма к оплате - это остаток (remainingAmount)
                    double amountToPay = p.remainingAmount > 0 ? p.remainingAmount : p.amount;
                    showNotification(context, "Сегодня нужно внести " + DateUtils.formatMoney(amountToPay) + " " + goal.currency, goal.id);
                    break;
                }
                // Логика для завтрашнего платежа (на всякий случай)
                // ... (если у тебя была логика на завтра, она должна использовать p.remainingAmount)
            }
        }
    }

    private void showNotification(Context context, String text, int goalId) {
        String channelId = "kopilka_channel";
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, GoalDetailActivity.class);
        intent.putExtra("goal_id", goalId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

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
