package com.example.kopi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        checkAndNotify(context);
        // Планируем следующий будильник на завтра
        NotificationUtils.scheduleAlarm(context);
    }

    private void checkAndNotify(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
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
                        NotificationUtils.showNotification(context,
                                "Взнос по цели: " + goal.name,
                                "Сегодня нужно внести " + DateUtils.formatMoney(p.amount - p.paidAmount) + " " + goal.currency);
                    } else if (p.date.equals(tomorrow)) {
                        NotificationUtils.showNotification(context,
                                "Напоминание по цели: " + goal.name,
                                "Завтра плановый взнос " + DateUtils.formatMoney(p.amount - p.paidAmount) + " " + goal.currency);
                    }
                }
            }
        }
    }
}
