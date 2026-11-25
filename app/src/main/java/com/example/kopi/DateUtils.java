package com.example.kopi;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DateUtils {
    public static final String DATE_FORMAT = "dd.MM.yyyy";

    public static String formatMoney(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0.##");
        return df.format(amount).replace(',', ' ');
    }

    public static String formatDate(Date date) {
        return new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(date);
    }

    public static Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).parse(dateStr);
        } catch (ParseException e) {
            return new Date();
        }
    }

    // Считаем дни до ближайшего платежа
    public static long daysUntilNextPayment(int goalId, AppDatabase db) {
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goalId);
        Collections.sort(payments, (p1, p2) -> parseDate(p1.date).compareTo(parseDate(p2.date)));
        
        long today = System.currentTimeMillis();
        for (Payment p : payments) {
            if (!p.status.equals("paid") && !p.status.equals("missed")) {
                long diff = parseDate(p.date).getTime() - today;
                return diff / (1000 * 60 * 60 * 24); // в днях
            }
        }
        return 999; // Если платежей нет
    }

    // Логика: Учесть в ближайших платежах (с переносом остатка)
    public static void applyToNearestPayments(Goal goal, AppDatabase db, double amount) {
        goal.currentAmount += amount;
        db.goalDao().update(goal);

        List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
        Collections.sort(payments, (p1, p2) -> parseDate(p1.date).compareTo(parseDate(p2.date)));

        double moneyLeft = amount;

        for (Payment p : payments) {
            if (moneyLeft <= 0) break;
            if (p.status.equals("paid") || p.status.equals("missed")) continue;

            double needed = p.remainingAmount;
            
            if (moneyLeft >= needed) {
                // Хватает на полное закрытие
                p.status = "paid";
                p.remainingAmount = 0;
                moneyLeft -= needed;
            } else {
                // Частичное закрытие
                p.status = "partial";
                p.remainingAmount = needed - moneyLeft;
                moneyLeft = 0;
            }
            db.goalDao().updatePayment(p);
        }
    }

    // Логика: Пересчитать график (сохраняя старые оплаты)
    public static void recalculateFutureSchedule(Goal goal, AppDatabase db) {
        // Удаляем только будущие платежи, которые не оплачены
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
        for (Payment p : payments) {
            if (p.status.equals("pending") || p.status.equals("partial")) {
                db.goalDao().deletePayment(p.id);
            }
        }

        double remaining = goal.targetAmount - goal.currentAmount;
        if (remaining <= 0) return;

        int paymentsNeeded = (int) Math.ceil(remaining / goal.paymentAmount);
        List<Payment> newSchedule = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        // Логика генерации дат (упрощенная)
        cal.add(Calendar.MONTH, 1); // Начинаем со следующего месяца

        for (int i = 0; i < paymentsNeeded; i++) {
            Payment p = new Payment();
            p.goalId = goal.id;
            p.amount = goal.paymentAmount;
            p.remainingAmount = goal.paymentAmount;
            p.status = "pending";
            
            if (goal.frequency.equals("Раз в неделю")) {
                cal.add(Calendar.WEEK_OF_YEAR, 1);
            } else if (goal.frequency.equals("Раз в месяц")) {
                cal.add(Calendar.MONTH, 1);
            } else {
                cal.add(Calendar.DAY_OF_YEAR, 15);
            }
            
            p.date = formatDate(cal.getTime());
            newSchedule.add(p);
        }

        for (Payment p : newSchedule) {
            db.goalDao().insertPayment(p);
        }
    }
}
