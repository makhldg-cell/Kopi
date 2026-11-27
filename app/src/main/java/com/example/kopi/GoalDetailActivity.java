package com.example.kopi;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoalDetailActivity extends AppCompatActivity {
    private Goal currentGoal;
    private TextView tvProgress, tvCalculation, tvUnallocatedFunds;
    private AppDatabase db;
    private PaymentAdapter adapter;
    private int goalId;
    private RecyclerView recyclerView;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal_detail);

        db = AppDatabase.getInstance(this);
        goalId = getIntent().getIntExtra("goal_id", -1);
        
        findViewById(R.id.btnEditGoal).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddGoalActivity.class);
            intent.putExtra("edit_goal_id", goalId);
            startActivity(intent);
        });

        tvProgress = findViewById(R.id.tvDetailProgress);
        tvCalculation = findViewById(R.id.tvCalculation);
        tvUnallocatedFunds = findViewById(R.id.tvUnallocatedFunds);
        recyclerView = findViewById(R.id.recyclerViewSchedule);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        EditText etAddAmount = findViewById(R.id.etAddAmount);
        Button btnAddAmount = findViewById(R.id.btnAddAmount);

        btnAddAmount.setOnClickListener(v -> {
            try {
                double addSum = Double.parseDouble(etAddAmount.getText().toString());
                if (addSum > 0) {
                    if (addSum >= currentGoal.paymentAmount || DateUtils.daysUntilNextPayment(goalId, db) > 2) {
                        showDistributionDialog(addSum);
                    } else {
                        applyAmount(addSum, true);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Введите сумму", Toast.LENGTH_SHORT).show();
            }
        });

        tvUnallocatedFunds.setOnClickListener(v -> {
            double unallocated = getUnallocatedFunds();
            if (unallocated <= 0) {
                Toast.makeText(this, "Нет свободных средств", Toast.LENGTH_SHORT).show();
                return;
            }
            EditText input = new EditText(this);
            input.setText(String.valueOf(unallocated));
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            
            new AlertDialog.Builder(this)
                .setTitle("Свободные средства")
                .setMessage("Измените сумму (это пересчитает график):")
                .setView(input)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    try {
                        double newAmount = Double.parseDouble(input.getText().toString());
                        double diff = newAmount - unallocated;
                        currentGoal.currentAmount += diff;
                        db.goalDao().update(currentGoal);
                        DateUtils.recalculateFutureSchedule(currentGoal, db);
                        loadGoalAndPayments();
                    } catch (Exception e) { }
                })
                .setNegativeButton("Отмена", null)
                .show();
        });
    }

    private double getUnallocatedFunds() {
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goalId);
        double allocated = 0;
        for (Payment p : payments) {
            if (p.status.equals("paid")) allocated += p.amount;
            else if (p.status.equals("partial")) allocated += (p.amount - p.remainingAmount);
        }
        return currentGoal.currentAmount - allocated;
    }

    private void showDistributionDialog(double amount) {
        String[] options = {
            "Учесть в ближайшем платеже",
            "Добавить в копилку (свободные средства)"
        };

        new AlertDialog.Builder(this)
            .setTitle("Куда учесть " + DateUtils.formatMoney(amount) + " " + currentGoal.currency + "?")
            .setItems(options, (dialog, which) -> {
                if (which == 0) applyAmount(amount, true);
                else applyAmount(amount, false);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void applyAmount(double amount, boolean applyToPayments) {
        executor.execute(() -> {
            if (applyToPayments) {
                DateUtils.applyToNearestPayments(currentGoal, db, amount);
            } else {
                currentGoal.currentAmount += amount;
                db.goalDao().update(currentGoal);
                DateUtils.recalculateFutureSchedule(currentGoal, db);
            }
            mainHandler.post(() -> {
                ((EditText)findViewById(R.id.etAddAmount)).setText("");
                loadGoalAndPayments();
                Toast.makeText(this, "Сумма учтена!", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGoalAndPayments();
    }

    private void loadGoalAndPayments() {
        executor.execute(() -> {
            currentGoal = db.goalDao().getGoalById(goalId);
            List<Payment> payments = db.goalDao().getPaymentsForGoal(goalId);

            mainHandler.post(() -> {
                if (currentGoal == null) { finish(); return; }
                updateUI(payments);
                adapter = new PaymentAdapter(payments);
                recyclerView.setAdapter(adapter);
            });
        });
    }

    private void updateUI(List<Payment> payments) {
        ((TextView)findViewById(R.id.tvDetailName)).setText(currentGoal.name);
        tvProgress.setText("Собрано: " + DateUtils.formatMoney(currentGoal.currentAmount) + " из " + DateUtils.formatMoney(currentGoal.targetAmount) + " " + currentGoal.currency);
        
        double remaining = currentGoal.targetAmount - currentGoal.currentAmount;
        if (remaining <= 0) {
            tvCalculation.setText("🎉 Цель достигнута!");
        } else {
            tvCalculation.setText("Осталось: " + DateUtils.formatMoney(remaining) + " " + currentGoal.currency);
        }

        double unallocated = getUnallocatedFunds();
        if (unallocated > 0) {
            tvUnallocatedFunds.setVisibility(View.VISIBLE);
            // ИСПОЛЬЗУЕМ formatMoney ДЛЯ КРАСИВОГО ВЫВОДА
            tvUnallocatedFunds.setText("Свободные средства (вне графика): " + DateUtils.formatMoney(unallocated) + " " + currentGoal.currency + " (нажмите для изменения)");
        } else {
            tvUnallocatedFunds.setVisibility(View.GONE);
        }
        
        ((TextView)findViewById(R.id.tvDetailLink)).setText("Ссылка: " + (currentGoal.link.isEmpty() ? "нет" : currentGoal.link));
        ((TextView)findViewById(R.id.tvDetailComment)).setText("Комментарий: " + (currentGoal.comment.isEmpty() ? "нет" : currentGoal.comment));
    }

    private class PaymentAdapter extends RecyclerView.Adapter<PaymentAdapter.PayViewHolder> {
        private List<Payment> payments;
        public PaymentAdapter(List<Payment> payments) { this.payments = payments; }

        @NonNull
        @Override
        public PayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment, parent, false);
            return new PayViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PayViewHolder holder, int position) {
            Payment p = payments.get(position);
            holder.tvPayDate.setText(p.date);
            
            if (p.status.equals("partial")) {
                holder.tvPayAmount.setText("Внесено: " + DateUtils.formatMoney(p.amount - p.remainingAmount) + "\nОсталось: " + DateUtils.formatMoney(p.remainingAmount));
            } else {
                holder.tvPayAmount.setText(DateUtils.formatMoney(p.amount) + " " + currentGoal.currency);
            }

            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.tvPayAmount.setTypeface(null, android.graphics.Typeface.NORMAL);

            if (p.status.equals("paid")) {
                holder.itemView.setBackgroundColor(Color.parseColor("#C8E6C9"));
                holder.tvPayStatus.setText("✅");
            } else if (p.status.equals("missed")) {
                holder.itemView.setBackgroundColor(Color.parseColor("#FFCDD2"));
                holder.tvPayStatus.setText("❌");
            } else if (p.status.equals("partial")) {
                holder.itemView.setBackgroundColor(Color.parseColor("#FFE0B2"));
                holder.tvPayAmount.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.tvPayStatus.setText("⚠️");
            } else {
                holder.tvPayStatus.setText("⏳");
            }

            holder.itemView.setOnClickListener(v -> showEditPaymentDialog(p));
        }

        @Override
        public int getItemCount() { return payments.size(); }

        class PayViewHolder extends RecyclerView.ViewHolder {
            TextView tvPayDate, tvPayAmount, tvPayStatus;
            public PayViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPayDate = itemView.findViewById(R.id.tvPayDate);
                tvPayAmount = itemView.findViewById(R.id.tvPayAmount);
                tvPayStatus = itemView.findViewById(R.id.tvPayStatus);
            }
        }
    }

    private void showEditPaymentDialog(Payment p) {
        if (currentGoal.isStrictMode) {
            long days = DateUtils.daysUntilNextPayment(goalId, db);
            if (days > 0 && DateUtils.parseDate(p.date).after(new java.util.Date())) {
                new AlertDialog.Builder(this)
                    .setTitle("Строгий режим")
                    .setMessage("Управление этим платежом будет доступно только в дату взноса (" + p.date + ").")
                    .setPositiveButton("Понятно", null)
                    .show();
                return;
            }
        }

        List<String> optionsList = new java.util.ArrayList<>();
        
        if (p.status.equals("pending") || p.status.equals("partial")) {
            optionsList.add("Внести (Учесть сумму)");
            optionsList.add("Отметить как Пропущен");
        } else if (p.status.equals("missed")) {
            optionsList.add("Внести (Учесть сумму)");
            optionsList.add("Отменить пропуск (Вернуть в ожидание)");
        } else if (p.status.equals("paid")) {
            optionsList.add("Отменить внесение (Вернуть в ожидание)");
            optionsList.add("Отметить как Пропущен (С отменой оплаты)");
        }

        String[] options = optionsList.toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Платеж на " + p.date);
        builder.setItems(options, (dialog, which) -> {
            dialog.dismiss();
            String selected = options[which];

            if (selected.startsWith("Внести")) {
                double amountToPay = p.status.equals("partial") ? p.remainingAmount : p.amount;
                applyAmount(amountToPay, true);
            } else if (selected.startsWith("Отметить как Пропущен")) {
                if (p.status.equals("paid")) {
                    // ВАЖНО: Если отменяем оплаченный, вычитаем его из общего прогресса
                    currentGoal.currentAmount -= p.amount;
                    db.goalDao().update(currentGoal);
                }
                p.status = "missed";
                p.remainingAmount = p.amount;
                saveAndRecalculate(p);
            } else if (selected.equals("Отменить пропуск (Вернуть в ожидание)")) {
                p.status = "pending";
                p.remainingAmount = p.amount;
                saveAndRecalculate(p);
            } else if (selected.equals("Отменить внесение (Вернуть в ожидание)")) {
                // ВАЖНО: Вычитаем сумму платежа, но НЕ ТРОГАЕМ свободные средства
                currentGoal.currentAmount -= p.amount;
                db.goalDao().update(currentGoal);
                p.status = "pending";
                p.remainingAmount = p.amount;
                saveAndRecalculate(p);
            }
        });
        builder.show();
    }

    private void saveAndRecalculate(Payment p) {
        executor.execute(() -> {
            AppDatabase.getInstance(this).goalDao().updatePayment(p);
            // НЕ ВЫЗЫВАЕМ recalculateGoalAmount, чтобы не затереть свободные средства.
            // Сумма уже скорректирована в currentGoal.currentAmount.
            DateUtils.recalculateFutureSchedule(currentGoal, db);

            mainHandler.post(() -> {
                loadGoalAndPayments();
                Toast.makeText(this, "Данные обновлены", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
