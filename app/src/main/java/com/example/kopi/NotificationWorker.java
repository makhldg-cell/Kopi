package com.example.kopi;

import android.content.Context;
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
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        List<Goal> goals = db.goalDao().getAllGoals();

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        
        // Получаем сегодняшнюю дату
        String today = sdf.format(new Date());
        
        // Получаем завтрашнюю дату
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrow = sdf.format(cal.getTime());

        for (Goal goal : goals) {
            if (goal.isFrozen) continue; // Пропускаем замороженные

            List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
            for (Payment p : payments) {
                if (p.status.equals("pending")) {
                    if (p.date.equals(today)) {
                        NotificationUtils.showNotification(getApplicationContext(),
                                "Взнос по цели: " + goal.name,
                                "Сегодня нужно внести " + DateUtils.formatMoney(p.amount) + " " + goal.currency);
                    } else if (p.date.equals(tomorrow)) {
                        NotificationUtils.showNotification(getApplicationContext(),
                                "Напоминание по цели: " + goal.name,
                                "Завтра плановый взнос " + DateUtils.formatMoney(p.amount) + " " + goal.currency);
                    }
                }
            }
        }
        return Result.success();
    }
}
