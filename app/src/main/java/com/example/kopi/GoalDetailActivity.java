package com.example.kopi;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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

public class GoalDetailActivity extends AppCompatActivity {
    private Goal currentGoal;
    private TextView tvProgress, tvCalculation;
    private AppDatabase db;
    private PaymentAdapter adapter;
    private int goalId;
    private RecyclerView recyclerView;

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
                currentGoal.currentAmount += addSum;
                db.goalDao().update(currentGoal);
                etAddAmount.setText("");
                updateUI();
                Toast.makeText(this, "Сумма добавлена!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Введите сумму", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentGoal = db.goalDao().getGoalById(goalId);
        if (currentGoal == null) { finish(); return; }
        
        updateUI();
        
        adapter = new PaymentAdapter(db.goalDao().getPaymentsForGoal(goalId));
        recyclerView.setAdapter(adapter);
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
                       "При взносе по " + DateUtils.formatMoney(currentGoal.paymentAmount) + " нужно сделать: " + paymentsNeeded + " взносов.";
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
            holder.tvPayAmount.setText(DateUtils.formatMoney(p.amount) + " " + currentGoal.currency);

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
        String[] options = {"Отметить как Внесен", "Отметить как Пропущен", "Внести частично"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Платеж на " + p.date);
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                p.status = "paid";
                // Логика предупреждения о несоответствии
                if (currentGoal.paymentAmount != p.amount) {
                    showWarningDialog("Сумма взноса не соответствует прогрессу. Вы уверены, что хотите учесть платеж?", p);
                } else {
                    savePaymentAndRefresh(p);
                }
            } else if (which == 1) {
                p.status = "missed";
                savePaymentAndRefresh(p);
            } else if (which == 2) {
                p.status = "partial";
                savePaymentAndRefresh(p);
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showWarningDialog(String message, Payment p) {
        new AlertDialog.Builder(this)
            .setTitle("Внимание")
            .setMessage(message)
            .setPositiveButton("Да", (dialog, which) -> {
                savePaymentAndRefresh(p);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void savePaymentAndRefresh(Payment p) {
        // 1. Сохраняем в базу
        AppDatabase.getInstance(this).goalDao().insertPayment(p);
        
        // 2. Принудительно перезапускаем активити, чтобы всё перерисовалось без вылетов
        recreate(); 
    }
}
