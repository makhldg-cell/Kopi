package com.example.kopi;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    public static final String DATE_FORMAT = "dd.MM.yyyy";

    // Форматируем дату для отображения
    public static String formatDate(Date date) {
        return new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(date);
    }

    // Считаем дату окончания на основе суммы и взносов
    public static String calculateEndDate(double targetAmount, double currentAmount, double paymentAmount, String frequency, int day1, int day2) {
        double remaining = targetAmount - currentAmount;
        if (remaining <= 0) return "Цель достигнута";
        if (paymentAmount <= 0) return "Укажите сумму взноса";

        int paymentsNeeded = (int) Math.ceil(remaining / paymentAmount);
        
        Calendar cal = Calendar.getInstance();
        
        if (frequency.equals("Раз в неделю")) {
            cal.add(Calendar.WEEK_OF_YEAR, paymentsNeeded);
        } else if (frequency.equals("Два раза в месяц")) {
            cal.add(Calendar.MONTH, (int) Math.ceil(paymentsNeeded / 2.0));
        } else if (frequency.equals("Раз в месяц")) {
            cal.add(Calendar.MONTH, paymentsNeeded);
        } else {
            cal.add(Calendar.MONTH, paymentsNeeded);
        }
        
        return formatDate(cal.getTime());
    }

    // Генерируем список будущих платежей (график)
    public static void generateSchedule(Goal goal, AppDatabase db) {
        Calendar cal = Calendar.getInstance();
        double remaining = goal.targetAmount - goal.currentAmount;
        if (goal.paymentAmount <= 0) return;
        int payments = (int) Math.ceil(remaining / goal.paymentAmount);

        for (int i = 0; i < payments; i++) {
            Payment p = new Payment();
            p.goalId = goal.id;
            p.amount = goal.paymentAmount;
            p.status = "pending";

            if (goal.frequency.equals("Раз в неделю")) {
                cal.add(Calendar.WEEK_OF_YEAR, 1);
            } else if (goal.frequency.equals("Раз в месяц")) {
                cal.add(Calendar.MONTH, 1);
            } else {
                cal.add(Calendar.DAY_OF_YEAR, 15);
            }
            
            p.date = formatDate(cal.getTime());
            db.goalDao().insertPayment(p);
        }
    }
              }
