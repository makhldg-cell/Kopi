package com.example.kopi;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private GoalAdapter adapter;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Разрешение на уведомления
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        }

        db = AppDatabase.getInstance(this);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new GoalAdapter(db.goalDao().getAllGoals());
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnAddGoal).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AddGoalActivity.class));
        });
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
            
            // Если заморожена, добавляем пометку
            String nameText = goal.name;
            if (goal.isFrozen) {
                nameText = "❄️ " + nameText;
                holder.itemView.setAlpha(0.6f); // Делаем полупрозрачным
            } else {
                holder.itemView.setAlpha(1.0f);
            }
            
            holder.tvGoalName.setText(nameText);
            holder.tvGoalAmount.setText(goal.currentAmount + " / " + goal.targetAmount + " " + goal.currency);
            holder.tvGoalDate.setText("К дате: " + goal.endDate);
            
            // Обычное нажатие - переход к деталям
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, GoalDetailActivity.class);
                intent.putExtra("goal_id", goal.id);
                startActivity(intent);
            });

            // Долгое нажатие - меню (Заморозить/Удалить)
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

    // Меню для заморозки и удаления
    private void showGoalOptions(Goal goal) {
        String freezeText = goal.isFrozen ? "Разморозить цель" : "Заморозить цель";
        
        new AlertDialog.Builder(this)
            .setTitle(goal.name)
            .setItems(new String[]{freezeText, "Удалить цель"}, (dialog, which) -> {
                if (which == 0) {
                    // Заморозка/Разморозка
                    goal.isFrozen = !goal.isFrozen;
                    db.goalDao().update(goal);
                    adapter.updateData(db.goalDao().getAllGoals());
                } else if (which == 1) {
                    // Удаление
                    new AlertDialog.Builder(this)
                        .setTitle("Удалить цель?")
                        .setMessage("Это действие нельзя отменить. Вся история платежей будет удалена.")
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
