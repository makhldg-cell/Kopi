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

    // Железобетонная функция для поиска следующей даты по календарю
    private static Date getNextDate(Goal goal, Date lastDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(lastDate);
        
        // Начинаем искать строго со следующего дня после последней даты
        cal.add(Calendar.DAY_OF_MONTH, 1); 

        if (goal.frequency.equals("Раз в неделю")) {
            while (cal.get(Calendar.DAY_OF_WEEK) != goal.dayOfWeek) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return cal.getTime();
        } else if (goal.frequency.equals("Раз в месяц")) {
            int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int targetDay = Math.min(goal.dayOfMonth1, maxDay);
            
            // Если в этом месяце целевой день уже прошел, перескакиваем на следующий
            if (cal.get(Calendar.DAY_OF_MONTH) > targetDay) {
                cal.add(Calendar.MONTH, 1);
                maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                targetDay = Math.min(goal.dayOfMonth1, maxDay);
            }
            cal.set(Calendar.DAY_OF_MONTH, targetDay);
            return cal.getTime();
            
        } else if (goal.frequency.equals("Два раза в месяц")) {
            int d1 = Math.min(goal.dayOfMonth1, goal.dayOfMonth2);
            int d2 = Math.max(goal.dayOfMonth1, goal.dayOfMonth2);
            
            int currentDay = cal.get(Calendar.DAY_OF_MONTH);
            int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int targetDay1 = Math.min(d1, maxDay);
            int targetDay2 = Math.min(d2, maxDay);
            
            if (currentDay <= targetDay1) {
                cal.set(Calendar.DAY_OF_MONTH, targetDay1);
            } else if (currentDay <= targetDay2) {
                cal.set(Calendar.DAY_OF_MONTH, targetDay2);
            } else {
                cal.add(Calendar.MONTH, 1);
                maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                targetDay1 = Math.min(d1, maxDay);
                cal.set(Calendar.DAY_OF_MONTH, targetDay1);
            }
            return cal.getTime();
        }
        return cal.getTime();
    }

    public static void recalculateFutureSchedule(Goal goal, AppDatabase db) {
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
        
        // 1. Ищем последнюю дату среди уже оплаченных или ПРОПУЩЕННЫХ (это критически важно!)
        Calendar cal = Calendar.getInstance();
        Date lastHandledDate = new Date(); 
        boolean hasHandled = false;
        
        for (Payment p : payments) {
            if (p.status.equals("paid") || p.status.equals("missed")) {
                Date d = parseDate(p.date);
                if (!hasHandled || d.after(lastHandledDate)) {
                    lastHandledDate = d;
                    hasHandled = true;
                }
            }
        }

        // 2. Удаляем только те платежи, которые еще ждут своей очереди
        for (Payment p : payments) {
            if (p.status.equals("pending") || p.status.equals("partial")) {
                db.goalDao().deletePayment(p.id);
            }
        }

        // 3. Считаем, сколько всего взносов нужно с учетом внесенной суммы
        double remaining = goal.targetAmount - goal.currentAmount;
        if (remaining <= 0) return;

        int paymentsNeeded = (int) Math.ceil(remaining / goal.paymentAmount);
        
        // 4. Генерируем новые даты, начиная строго после последней пропущенной/оплаченной
        Calendar iterCal = Calendar.getInstance();
        iterCal.setTime(lastHandledDate);

        for (int i = 0; i < paymentsNeeded; i++) {
            Date nextDate = getNextDate(goal, iterCal.getTime());
            iterCal.setTime(nextDate);
            
            Payment p = createPayment(goal, nextDate);
            db.goalDao().insertPayment(p);
        }
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
