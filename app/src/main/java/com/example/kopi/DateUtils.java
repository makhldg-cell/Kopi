package com.example.kopi;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    public static long daysUntilNextPayment(int goalId, AppDatabase db) {
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goalId);
        Collections.sort(payments, (p1, p2) -> parseDate(p1.date).compareTo(parseDate(p2.date)));
        long today = System.currentTimeMillis();
        for (Payment p : payments) {
            if (!p.status.equals("paid") && !p.status.equals("missed")) {
                long diff = parseDate(p.date).getTime() - today;
                return diff / (1000 * 60 * 60 * 24); 
            }
        }
        return 999;
    }

    public static void recalculateFutureSchedule(Goal goal, AppDatabase db) {
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
        
        // 1. Ищем последнюю дату среди уже оплаченных или ПРОПУЩЕННЫХ
        Date lastHandledDate = null; 
        for (Payment p : payments) {
            if (p.status.equals("paid") || p.status.equals("missed")) {
                Date d = parseDate(p.date);
                if (lastHandledDate == null || d.after(lastHandledDate)) {
                    lastHandledDate = d;
                }
            }
        }

        // 2. Удаляем все будущие неоплаченные платежи
        for (Payment p : payments) {
            if (p.status.equals("pending") || p.status.equals("partial")) {
                db.goalDao().deletePayment(p.id);
            }
        }

        // 3. Считаем, сколько нужно создать
        double remaining = goal.targetAmount - goal.currentAmount;
        if (remaining <= 0) return;
        int paymentsNeeded = (int) Math.ceil(remaining / goal.paymentAmount);

        // 4. Начинаем поиск дат
        Calendar cal = Calendar.getInstance();
        if (lastHandledDate != null) {
            cal.setTime(lastHandledDate);
            cal.add(Calendar.DAY_OF_MONTH, 1); // Ищем со следующего дня после последнего
        }
        // Если lastHandledDate == null, поиск начинается с СЕГОДНЯШНЕГО дня

        for (int i = 0; i < paymentsNeeded; i++) {
            cal = getNextMatchingDate(goal, cal);
            Payment p = createPayment(goal, cal.getTime());
            db.goalDao().insertPayment(p);
            
            // Сдвигаем на следующий день, чтобы искать следующий платеж
            cal.add(Calendar.DAY_OF_MONTH, 1); 
        }
    }

    // Перебор дней до совпадения (защита от любых сбоев календаря)
    private static Calendar getNextMatchingDate(Goal goal, Calendar cal) {
        int maxDays = 365 * 5; // Ограничитель на 5 лет вперед
        while (maxDays > 0) {
            if (matchesFrequency(goal, cal)) {
                return cal;
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
            maxDays--;
        }
        return cal;
    }

    private static boolean matchesFrequency(Goal goal, Calendar cal) {
        if (goal.frequency.equals("Раз в неделю")) {
            return cal.get(Calendar.DAY_OF_WEEK) == goal.dayOfWeek;
        } else if (goal.frequency.equals("Раз в месяц")) {
            int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int targetDay = Math.min(goal.dayOfMonth1, maxDay);
            return cal.get(Calendar.DAY_OF_MONTH) == targetDay;
        } else if (goal.frequency.equals("Два раза в месяц")) {
            int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int d1 = Math.min(goal.dayOfMonth1, maxDay);
            int d2 = Math.min(goal.dayOfMonth2, maxDay);
            int currentDay = cal.get(Calendar.DAY_OF_MONTH);
            return currentDay == d1 || currentDay == d2;
        }
        return false;
    }

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
                p.status = "paid";
                p.remainingAmount = 0;
                moneyLeft -= needed;
            } else {
                p.status = "partial";
                p.remainingAmount = needed - moneyLeft;
                moneyLeft = 0;
            }
            db.goalDao().updatePayment(p);
        }
    }

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
        recalculateFutureSchedule(goal, db);
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
