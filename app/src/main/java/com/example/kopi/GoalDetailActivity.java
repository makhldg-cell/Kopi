package com.example.kopi;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class GoalDetailActivity extends AppCompatActivity {
    private Goal currentGoal;
    private TextView tvProgress, tvCalculation;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal_detail);

        db = AppDatabase.getInstance(this);
        int goalId = getIntent().getIntExtra("goal_id", -1);
        currentGoal = db.goalDao().getGoalById(goalId);

        tvProgress = findViewById(R.id.tvDetailProgress);
        tvCalculation = findViewById(R.id.tvCalculation);
        
        updateUI();

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

    private void updateUI() {
        ((TextView)findViewById(R.id.tvDetailName)).setText(currentGoal.name);
        tvProgress.setText("Собрано: " + currentGoal.currentAmount + " из " + currentGoal.targetAmount + " " + currentGoal.currency);
        
        // Логика расчетов
        double remaining = currentGoal.targetAmount - currentGoal.currentAmount;
        String calcText = "";

        if (remaining <= 0) {
            calcText = "🎉 Цель достигнута!";
        } else if (currentGoal.plannedPayment > 0) {
            int paymentsNeeded = (int) Math.ceil(remaining / currentGoal.plannedPayment);
            calcText = "Осталось: " + remaining + " " + currentGoal.currency + "\n" +
                       "При взносе по " + currentGoal.plannedPayment + " нужно сделать: " + paymentsNeeded + " платежей.";
        } else {
            calcText = "Осталось: " + remaining + " " + currentGoal.currency + "\n" +
                       "Частота: " + currentGoal.paymentFrequency + "\n" +
                       "Укажите сумму взноса для расчета графика.";
        }

        tvCalculation.setText(calcText);
        ((TextView)findViewById(R.id.tvDetailLink)).setText("Ссылка: " + (currentGoal.link.isEmpty() ? "нет" : currentGoal.link));
        ((TextView)findViewById(R.id.tvDetailComment)).setText("Комментарий: " + (currentGoal.comment.isEmpty() ? "нет" : currentGoal.comment));
    }
}
