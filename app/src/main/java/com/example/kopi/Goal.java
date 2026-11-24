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

    // Настройки графика
    public String frequency; 
    public int dayOfWeek; 
    public int dayOfMonth1; 
    public int dayOfMonth2; 
    public double paymentAmount; 
    public String endDate; 

    // Новое поле для заморозки
    public boolean isFrozen = false;
}
