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
        return 999;
    }

    // МЕТОД 1: Учесть в ближайших платежах (с переносом остатка на след. месяц)
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

    // МЕТОД 2: Пересчитать график (сохраняя старые оплаты)
    public static void recalculateFutureSchedule(Goal goal, AppDatabase db) {
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

        // Начинаем со следующего месяца (или недели)
        cal.add(Calendar.MONTH, 1); 

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

    // ВОЗВРАЩАЕМ МЕТОДЫ ДЛЯ ЭКРАНА СОЗДАНИЯ ЦЕЛИ
    public static String calculateEndDate(double targetAmount, double currentAmount, double paymentAmount, String frequency, int day1, int day2) {
        double remaining = targetAmount - currentAmount;
        if (remaining <= 0) return "Цель достигнута";
        if (paymentAmount <= 0) return "Укажите сумму взноса";

        int paymentsNeeded = (int) Math.ceil(remaining / paymentAmount);
        Calendar cal = Calendar.getInstance();
        
        if (frequency.equals("Два раза в месяц")) {
            cal.add(Calendar.MONTH, paymentsNeeded / 2);
            cal.add(Calendar.DAY_OF_YEAR, (paymentsNeeded % 2) * 15);
        } else if (frequency.equals("Раз в неделю")) {
            cal.add(Calendar.WEEK_OF_YEAR, paymentsNeeded);
        } else {
            cal.add(Calendar.MONTH, paymentsNeeded);
        }
        return formatDate(cal.getTime());
    }

    public static void generateSchedule(Goal goal, AppDatabase db) {
        List<Payment> schedule = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        double remaining = goal.targetAmount - goal.currentAmount;
        int paymentsNeeded = (int) Math.ceil(remaining / goal.paymentAmount);

        if (goal.frequency.equals("Раз в неделю")) {
            cal.set(Calendar.DAY_OF_WEEK, goal.dayOfWeek);
            if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.WEEK_OF_YEAR, 1);
            for (int i = 0; i < paymentsNeeded; i++) {
                schedule.add(createPayment(goal, cal.getTime()));
                cal.add(Calendar.WEEK_OF_YEAR, 1);
            }
        } else if (goal.frequency.equals("Раз в месяц")) {
            cal.set(Calendar.DAY_OF_MONTH, goal.dayOfMonth1);
            if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.MONTH, 1);
            for (int i = 0; i < paymentsNeeded; i++) {
                schedule.add(createPayment(goal, cal.getTime()));
                cal.add(Calendar.MONTH, 1);
            }
        } else if (goal.frequency.equals("Два раза в месяц")) {
            int d1 = Math.min(goal.dayOfMonth1, goal.dayOfMonth2);
            int d2 = Math.max(goal.dayOfMonth1, goal.dayOfMonth2);
            
            Calendar temp = (Calendar) cal.clone();
            temp.set(Calendar.DAY_OF_MONTH, d1);
            if (temp.getTimeInMillis() < System.currentTimeMillis()) {
                temp.set(Calendar.DAY_OF_MONTH, d2);
                if (temp.getTimeInMillis() < System.currentTimeMillis()) {
                    temp.add(Calendar.MONTH, 1);
                    temp.set(Calendar.DAY_OF_MONTH, d1);
                }
            }
            for (int i = 0; i < paymentsNeeded; i++) {
                schedule.add(createPayment(goal, temp.getTime()));
                if (temp.get(Calendar.DAY_OF_MONTH) == d1) {
                    temp.set(Calendar.DAY_OF_MONTH, d2);
                } else {
                    temp.add(Calendar.MONTH, 1);
                    temp.set(Calendar.DAY_OF_MONTH, d1);
                }
            }
        }

        Collections.sort(schedule, (p1, p2) -> parseDate(p1.date).compareTo(parseDate(p2.date)));
        for (Payment p : schedule) {
            db.goalDao().insertPayment(p);
        }
    }

    private static Payment createPayment(Goal goal, Date date) {
        Payment p = new Payment();
        p.goalId = goal.id;
        p.amount = goal.paymentAmount;
        p.remainingAmount = goal.paymentAmount;
        p.status = "pending";
        p.date = formatDate(date);
        return p;
    }
}
