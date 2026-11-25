package com.example.kopi;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackgroundCheckWorker extends Worker {
    public BackgroundCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        String today = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());

        List<Goal> goals = db.goalDao().getAllGoals();
        for (Goal goal : goals) {
            if (goal.isFrozen) continue;

            List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
            for (Payment p : payments) {
                // Если дата платежа прошла, а статус "в ожидании" - помечаем как пропущенный
                if (p.status.equals("pending") && DateUtils.parseDate(p.date).before(new Date())) {
                    p.status = "missed";
                    db.goalDao().updatePayment(p);
                }
            }
        }
        return Result.success();
    }
}
