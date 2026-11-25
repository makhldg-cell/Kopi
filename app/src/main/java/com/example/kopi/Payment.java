package com.example.kopi;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "payments")
public class Payment {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int goalId;
    public String date;
    public double amount; // Планируемая сумма взноса
    public double paidAmount = 0; // НОВОЕ ПОЛЕ: сколько уже внесено
    public String status; // "pending", "paid", "missed", "partial"
}
