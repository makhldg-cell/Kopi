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

    // ФУНКЦИЯ ДЛЯ ВАРИАНТА "УЧЕСТЬ В ПЛАТЕЖАХ"
    public static void shortenSchedule(Goal goal, AppDatabase db, double extraAmount) {
        // 1. Получаем все платежи
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
        Collections.sort(payments, (p1, p2) -> parseDate(p1.date).compareTo(parseDate(p2.date)));

        double moneyToUse = extraAmount;

        // 2. Проходимся по платежам и закрываем их, пока хватает денег
        for (Payment p : payments) {
            if (moneyToUse <= 0) break;
            
            // Если это уже оплаченный платеж — пропускаем
            if (p.status.equals("paid")) continue; 

            if (moneyToUse >= p.amount) {
                // Если денег хватает на весь платеж — закрываем его
                p.status = "paid";
                moneyToUse -= p.amount;
                db.goalDao().updatePayment(p);
            } else {
                // Если денег не хватает — делаем частичный платеж
                p.status = "partial";
                moneyToUse = 0; // Остаток денег ушел сюда
                db.goalDao().updatePayment(p);
                break;
            }
        }

        // 3. Если сумма внесена, и деньги остались, а платежи кончились — просто игнорируем остаток (он пойдет в общий прогресс)
    }

    // Старая функция (для "пересчитать график")
    public static void recalculateSchedule(Goal goal, AppDatabase db) {
        db.goalDao().deletePaymentsForGoal(goal.id);
        double remaining = goal.targetAmount - goal.currentAmount;
        if (remaining <= 0) return;

        int paymentsNeeded = (int) Math.ceil(remaining / goal.paymentAmount);
        List<Payment> newSchedule = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        if (goal.frequency.equals("Раз в неделю")) {
            cal.set(Calendar.DAY_OF_WEEK, goal.dayOfWeek);
            if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.WEEK_OF_YEAR, 1);
            for (int i = 0; i < paymentsNeeded; i++) {
                newSchedule.add(createPayment(goal, cal.getTime()));
                cal.add(Calendar.WEEK_OF_YEAR, 1);
            }
        } else if (goal.frequency.equals("Раз в месяц")) {
            cal.set(Calendar.DAY_OF_MONTH, goal.dayOfMonth1);
            if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.MONTH, 1);
            for (int i = 0; i < paymentsNeeded; i++) {
                newSchedule.add(createPayment(goal, cal.getTime()));
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
                newSchedule.add(createPayment(goal, temp.getTime()));
                if (temp.get(Calendar.DAY_OF_MONTH) == d1) {
                    temp.set(Calendar.DAY_OF_MONTH, d2);
                } else {
                    temp.add(Calendar.MONTH, 1);
                    temp.set(Calendar.DAY_OF_MONTH, d1);
                }
            }
        }

        Collections.sort(newSchedule, (p1, p2) -> parseDate(p1.date).compareTo(parseDate(p2.date)));
        for (Payment p : newSchedule) {
            db.goalDao().insertPayment(p);
        }
    }

    private static Payment createPayment(Goal goal, Date date) {
        Payment p = new Payment();
        p.goalId = goal.id;
        p.amount = goal.paymentAmount;
        p.status = "pending";
        p.date = formatDate(date);
        return p;
    }
}
