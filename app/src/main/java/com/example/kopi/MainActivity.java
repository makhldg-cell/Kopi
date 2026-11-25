package com.example.kopi;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private GoalAdapter adapter;
    private AppDatabase db;
    
    private SharedPreferences prefs;
    private static final String PREF_NAME = "KopilkaPrefs";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notif_enabled";
    private static final String KEY_NOTIFICATION_HOUR = "notif_hour";
    private static final String KEY_NOTIFICATION_MINUTE = "notif_minute";
    private static final String KEY_CHECK_COUNT = "check_count"; // 1, 2, 3, 4

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        }

        scheduleDailyNotification();

        db = AppDatabase.getInstance(this);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new GoalAdapter(db.goalDao().getAllGoals());
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnAddGoal).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AddGoalActivity.class));
        });

        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsMenu());
    }

    private void showSettingsMenu() {
        boolean isEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
        int hour = prefs.getInt(KEY_NOTIFICATION_HOUR, 12);
        int minute = prefs.getInt(KEY_NOTIFICATION_MINUTE, 0);
        int checkCount = prefs.getInt(KEY_CHECK_COUNT, 1); // По умолчанию 1 раз

        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        Switch switchEnabled = dialogView.findViewById(R.id.switchNotifications);
        TextView tvTime = dialogView.findViewById(R.id.tvTimeValue);
        View btnPickTime = dialogView.findViewById(R.id.btnPickTime);
        TextView tvCheckFrequency = dialogView.findViewById(R.id.tvCheckFrequency); // Новое поле

        switchEnabled.setChecked(isEnabled);
        tvTime.setText(timeStr);
        
        // Отображаем текущую частоту
        String[] checkLabels = {"1 раз (в 12:00)", "2 раза (12:00, 24:00)", "3 раза (8:00, 16:00, 24:00)", "4 раза (6:00, 12:00, 18:00, 24:00)"};
        tvCheckFrequency.setText(checkLabels[checkCount - 1]);

        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, isChecked).apply();
            scheduleDailyNotification();
        });

        btnPickTime.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                    (view, hourOfDay, minute1) -> {
                        prefs.edit()
                                .putInt(KEY_NOTIFICATION_HOUR, hourOfDay)
                                .putInt(KEY_NOTIFICATION_MINUTE, minute1)
                                .apply();
                        tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute1));
                        scheduleDailyNotification();
                    }, hour, minute, true);
            timePickerDialog.show();
        });

        // Обработка выбора частоты
        dialogView.findViewById(R.id.btnPickFrequency).setOnClickListener(v -> {
            String[] options = {
                "1 раз в день (по умолчанию)",
                "2 раза в день",
                "3 раза в день",
                "4 раза в день (тратит батарею)"
            };
            new AlertDialog.Builder(this)
                .setTitle("Как часто проверять обновление цели?")
                .setItems(options, (d, which) -> {
                    prefs.edit().putInt(KEY_CHECK_COUNT, which + 1).apply();
                    tvCheckFrequency.setText(checkLabels[which]);
                    scheduleDailyNotification();
                })
                .show();
        });

        new AlertDialog.Builder(this)
                .setTitle("Настройки уведомлений")
                .setView(dialogView)
                .setPositiveButton("Тест сейчас", (dialog, which) -> {
                    OneTimeWorkRequest testRequest = new OneTimeWorkRequest.Builder(NotificationWorker.class).build();
                    WorkManager.getInstance(this).enqueue(testRequest);
                    Toast.makeText(this, "Тест уведомления запущен!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    // Планировщик с учетом частоты
    private void scheduleDailyNotification() {
        boolean isEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
        if (!isEnabled) {
            WorkManager.getInstance(this).cancelUniqueWork("DailyPaymentCheck");
            return;
        }

        int hour = prefs.getInt(KEY_NOTIFICATION_HOUR, 12);
        int minute = prefs.getInt(KEY_NOTIFICATION_MINUTE, 0);
        int checkCount = prefs.getInt(KEY_CHECK_COUNT, 1);

        // Если частота 1 — как раньше, один раз в сутки
        if (checkCount == 1) {
            scheduleOneTime(hour, minute, 24); // 24 часа
        } else {
            // Для остальных частот мы просто ставим интервал в часах
            // Логика: 24 / checkCount = интервал в часах
            int intervalHours = 24 / checkCount;
            // Ставим периодическую задачу каждые intervalHours часов
            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    NotificationWorker.class, intervalHours, TimeUnit.HOURS)
                    .build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "DailyPaymentCheck",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
            );
        }
    }

    // Вспомогательный метод для одиночного запуска раз в сутки
    private void scheduleOneTime(int hour, int minute, int hours) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }

        long delay = target.getTimeInMillis() - now.getTimeInMillis();
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                NotificationWorker.class, 1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DailyPaymentCheck",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.updateData(db.goalDao().getAllGoals());
    }

    private class GoalAdapter extends RecyclerView.Adapter<GoalAdapter.GoalViewHolder> {
        private List<Goal> goals;
        public GoalAdapter(List<Goal> goals) { this.goals = goals; }
        public void updateData(List<Goal> newGoals) { this.goals = newGoals; notifyDataSetChanged(); }

        @NonNull
        @Override
        public GoalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goal, parent, false);
            return new GoalViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GoalViewHolder holder, int position) {
            Goal goal = goals.get(position);
            String nameText = goal.name;
            if (goal.isFrozen) {
                nameText = "❄️ " + nameText;
                holder.itemView.setAlpha(0.6f);
            } else {
                holder.itemView.setAlpha(1.0f);
            }
            
            holder.tvGoalName.setText(nameText);
            holder.tvGoalAmount.setText(DateUtils.formatMoney(goal.currentAmount) + " / " + DateUtils.formatMoney(goal.targetAmount) + " " + goal.currency);
            holder.tvGoalDate.setText("К дате: " + goal.endDate);
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, GoalDetailActivity.class);
                intent.putExtra("goal_id", goal.id);
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                showGoalOptions(goal);
                return true;
            });
        }

        @Override
        public int getItemCount() { return goals.size(); }

        class GoalViewHolder extends RecyclerView.ViewHolder {
            TextView tvGoalName, tvGoalAmount, tvGoalDate;
            public GoalViewHolder(@NonNull View itemView) {
                super(itemView);
                tvGoalName = itemView.findViewById(R.id.tvGoalName);
                tvGoalAmount = itemView.findViewById(R.id.tvGoalAmount);
                tvGoalDate = itemView.findViewById(R.id.tvGoalDate);
            }
        }
    }

    private void showGoalOptions(Goal goal) {
        String freezeText = goal.isFrozen ? "Разморозить цель" : "Заморозить цель";
        new AlertDialog.Builder(this)
            .setTitle(goal.name)
            .setItems(new String[]{freezeText, "Удалить цель"}, (dialog, which) -> {
                if (which == 0) {
                    goal.isFrozen = !goal.isFrozen;
                    db.goalDao().update(goal);
                    adapter.updateData(db.goalDao().getAllGoals());
                } else if (which == 1) {
                    new AlertDialog.Builder(this)
                        .setTitle("Удалить цель?")
                        .setMessage("Все данные будут удалены.")
                        .setPositiveButton("Удалить", (d, w) -> {
                            db.goalDao().deletePaymentsForGoal(goal.id);
                            db.goalDao().deleteGoal(goal.id);
                            adapter.updateData(db.goalDao().getAllGoals());
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
                }
            })
            .show();
    }
}
