package com.example.kopi;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import java.util.List;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int goalId = intent.getIntExtra("goal_id", -1);
        int paymentId = intent.getIntExtra("payment_id", -1);
        
        if (goalId != -1 && paymentId != -1) {
            AppDatabase db = AppDatabase.getInstance(context);
            Goal goal = db.goalDao().getGoalById(goalId);
            
            if (goal != null) {
                List<Payment> payments = db.goalDao().getPaymentsForGoal(goalId);
                for (Payment p : payments) {
                    if (p.id == paymentId) {
                        // 1. Помечаем как оплаченный
                        p.status = "paid";
                        p.remainingAmount = 0;
                        db.goalDao().updatePayment(p);
                        
                        // 2. Прибавляем сумму напрямую к цели (вместо удаленного метода)
                        goal.currentAmount += p.amount;
                        db.goalDao().update(goal);
                        
                        // 3. Пересчитываем будущий график
                        DateUtils.recalculateFutureSchedule(goal, db);
                        
                        Toast.makeText(context, "Взнос по цели «" + goal.name + "» учтен!", Toast.LENGTH_LONG).show();
                        break;
                    }
                }
            }
            // Закрываем уведомление
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(goalId);
        }
    }
}
