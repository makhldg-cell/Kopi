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

    // Безопасная установка дня месяца (если 31, а дней 30 - ставит 30)
    private static void setDayOfMonthSafe(Calendar cal, int day) {
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        cal.set(Calendar.DAY_OF_MONTH, Math.min(day, maxDay));
    }

    // Полный пересчет общей суммы на основе статусов платежей
    public static void recalculateGoalAmount(Goal goal, AppDatabase db) {
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
        double totalPaid = 0;
        for (Payment p : payments) {
            if (p.status.equals("paid")) {
                totalPaid += p.amount;
            } else if (p.status.equals("partial")) {
                totalPaid += (p.amount - p.remainingAmount);
            }
        }
        goal.currentAmount = totalPaid;
        db.goalDao().update(goal);
    }

    // Полный пересчет будущих платежей (с жесткой привязкой к датам)
    public static void recalculateFutureSchedule(Goal goal, AppDatabase db) {
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
        
        // Удаляем только те платежи, которые еще не оплачены (или частичные)
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
        cal.add(Calendar.DAY_OF_MONTH, 1); // Начинаем с завтра

        if (goal.frequency.equals("Раз в неделю")) {
            cal.set(Calendar.DAY_OF_WEEK, goal.dayOfWeek);
            if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.WEEK_OF_YEAR, 1);
            for (int i = 0; i < paymentsNeeded; i++) {
                newSchedule.add(createPayment(goal, cal.getTime()));
                cal.add(Calendar.WEEK_OF_YEAR, 1);
            }
        } else if (goal.frequency.equals("Раз в месяц")) {
            for (int i = 0; i < paymentsNeeded; i++) {
                setDayOfMonthSafe(cal, goal.dayOfMonth1);
                if (cal.getTimeInMillis() < System.currentTimeMillis()) {
                    cal.add(Calendar.MONTH, 1);
                    setDayOfMonthSafe(cal, goal.dayOfMonth1);
                }
                newSchedule.add(createPayment(goal, cal.getTime()));
                cal.add(Calendar.MONTH, 1);
            }
        } else if (goal.frequency.equals("Два раза в месяц")) {
            int d1 = Math.min(goal.dayOfMonth1, goal.dayOfMonth2);
            int d2 = Math.max(goal.dayOfMonth1, goal.dayOfMonth2);
            
            for (int i = 0; i < paymentsNeeded; i++) {
                if (i % 2 == 0) {
                    setDayOfMonthSafe(cal, d1);
                } else {
                    setDayOfMonthSafe(cal, d2);
                }
                
                if (cal.getTimeInMillis() < System.currentTimeMillis()) {
                    if (i % 2 == 0) {
                        setDayOfMonthSafe(cal, d2);
                    } else {
                        cal.add(Calendar.MONTH, 1);
                        setDayOfMonthSafe(cal, d1);
                    }
                }
                
                newSchedule.add(createPayment(goal, cal.getTime()));
                if (i % 2 != 0) {
                    cal.add(Calendar.MONTH, 1);
                }
            }
        }

        Collections.sort(newSchedule, (p1, p2) -> parseDate(p1.date).compareTo(parseDate(p2.date)));
        for (Payment p : newSchedule) {
            db.goalDao().insertPayment(p);
        }
    }

    // Учесть сумму в ближайших платежах
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
