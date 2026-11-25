package com.example.kopi;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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
        // Проверяем настройки пользователя
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean("notif_enabled", true);
        if (!isEnabled) return Result.success();

        int savedHour = prefs.getInt("notif_hour", 9);
        int savedMinute = prefs.getInt("notif_minute", 0);

        // Проверяем текущее время
        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);

        // Если сейчас не то время, ничего не делаем (разрешаем погрешность в 15 минут, так как Worker запускается периодически)
        if (currentHour != savedHour || currentMinute < savedMinute || currentMinute > savedMinute + 14) {
            return Result.success();
        }

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        List<Goal> goals = db.goalDao().getAllGoals();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        
        String today = sdf.format(new Date());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrow = sdf.format(cal.getTime());

        for (Goal goal : goals) {
            if (goal.isFrozen) continue;

            List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
            for (Payment p : payments) {
                if (p.status.equals("pending") || p.status.equals("partial")) {
                    if (p.date.equals(today)) {
                        NotificationUtils.showNotification(getApplicationContext(),
                                "Взнос по цели: " + goal.name,
                                "Сегодня нужно внести " + DateUtils.formatMoney(p.amount - p.paidAmount) + " " + goal.currency);
                    } else if (p.date.equals(tomorrow)) {
                        NotificationUtils.showNotification(getApplicationContext(),
                                "Напоминание по цели: " + goal.name,
                                "Завтра плановый взнос " + DateUtils.formatMoney(p.amount - p.paidAmount) + " " + goal.currency);
                    }
                }
            }
        }
        return Result.success();
    }
}
