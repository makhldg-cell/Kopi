package com.example.kopi;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "payments")
public class Payment {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int goalId;
    public String date;
    public double amount;
    public double remainingAmount; // Сколько осталось внести (для частичных)
    public String status; // "pending", "paid", "missed", "partial"
}
