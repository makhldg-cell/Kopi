package com.example.kopi;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "goals")
public class Goal {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public double targetAmount;
    public double currentAmount;
    public String currency;
    public String link;
    public String comment;

    // Поля для расчетов и дат
    public String paymentFrequency; // "weekly", "twice_month", "monthly", "custom"
    public String startDate; // Формат "dd.MM.yyyy"
    public String targetDate; // Формат "dd.MM.yyyy"
    public double plannedPayment; // Сколько планируется вносить за раз
}
