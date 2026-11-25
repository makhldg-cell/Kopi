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
    private TextView tvProgress, tvCalculation;
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
        recyclerView = findViewById(R.id.recyclerViewSchedule);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        EditText etAddAmount = findViewById(R.id.etAddAmount);
        Button btnAddAmount = findViewById(R.id.btnAddAmount);

        btnAddAmount.setOnClickListener(v -> {
            try {
                double addSum = Double.parseDouble(etAddAmount.getText().toString());
                if (addSum > 0) {
                    // Проверяем, больше ли сумма, чем плановый взнос
                    if (addSum >= currentGoal.paymentAmount) {
                        showDistributionDialog(addSum);
                    } else {
                        // Если меньше — просто прибавляем в копилку
                        applyAmount(addSum, false);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Введите сумму", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDistributionDialog(double amount) {
        // Ищем ближайший неоплаченный платеж и считаем дни до него
        long daysUntilNext = DateUtils.daysUntilNextPayment(goalId, db);

        String[] options;
        if (daysUntilNext > 2) {
            // Если до платежа больше 2 дней
            options = new String[]{
                "Учесть в ближайшем платеже",
                "Добавить в копилку и пересчитать график"
            };
        } else {
            // Если платеж уже близко
            options = new String[]{
                "Учесть в текущем платеже (остаток перенести на следующий)",
                "Добавить в копилку и пересчитать график"
            };
        }

        new AlertDialog.Builder(this)
            .setTitle("Куда учесть " + DateUtils.formatMoney(amount) + " " + currentGoal.currency + "?")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Учесть в платежах (с переносом остатка)
                    applyAmount(amount, true);
                } else {
                    // Пересчитать график (добавить в общий котел)
                    applyAmount(amount, false);
                }
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
            
            List<Payment> payments = db.goalDao().getPaymentsForGoal(goalId);
            
            mainHandler.post(() -> {
                ((EditText)findViewById(R.id.etAddAmount)).setText("");
                updateUI();
                adapter = new PaymentAdapter(payments);
                recyclerView.setAdapter(adapter);
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
                updateUI();
                adapter = new PaymentAdapter(payments);
                recyclerView.setAdapter(adapter);
            });
        });
    }

    private void updateUI() {
        ((TextView)findViewById(R.id.tvDetailName)).setText(currentGoal.name);
        tvProgress.setText("Собрано: " + DateUtils.formatMoney(currentGoal.currentAmount) + " из " + DateUtils.formatMoney(currentGoal.targetAmount) + " " + currentGoal.currency);
        
        double remaining = currentGoal.targetAmount - currentGoal.currentAmount;
        String calcText = "";
        if (remaining <= 0) {
            calcText = "🎉 Цель достигнута!";
        } else if (currentGoal.paymentAmount > 0) {
            int paymentsNeeded = (int) Math.ceil(remaining / currentGoal.paymentAmount);
            calcText = "Осталось: " + DateUtils.formatMoney(remaining) + " " + currentGoal.currency + "\n" +
                       "Осталось сделать взносов: " + paymentsNeeded;
        }
        tvCalculation.setText(calcText);
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
            
            // Логика отображения частичного взноса
            if (p.status.equals("partial")) {
                holder.tvPayAmount.setText("Внесено: " + DateUtils.formatMoney(p.amount - p.remainingAmount) + 
                                           "\nОсталось: " + DateUtils.formatMoney(p.remainingAmount));
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
        String[] options = {"Внести остаток по этому платежу", "Отметить как Пропущен"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Платеж на " + p.date);
        builder.setItems(options, (dialog, which) -> {
            dialog.dismiss(); 
            if (which == 0) {
                double amountToPay = p.status.equals("partial") ? p.remainingAmount : p.amount;
                applyAmount(amountToPay, true);
            } else if (which == 1) {
                p.status = "missed";
                savePaymentStatusInBackground(p);
            }
        });
        builder.show();
    }

    private void savePaymentStatusInBackground(Payment p) {
        executor.execute(() -> {
            AppDatabase.getInstance(this).goalDao().updatePayment(p);
            List<Payment> updatedPayments = db.goalDao().getPaymentsForGoal(goalId);
            
            mainHandler.post(() -> {
                adapter = new PaymentAdapter(updatedPayments);
                recyclerView.setAdapter(adapter);
                Toast.makeText(this, "Платеж пропущен", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
