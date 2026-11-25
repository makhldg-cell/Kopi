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

    // ТА САМАЯ ФУНКЦИЯ ПЕРЕСЧЕТА ГРАФИКА
    public static void updateScheduleBasedOnProgress(Goal goal, AppDatabase db) {
        // 1. Берем все платежи из базы
        List<Payment> payments = db.goalDao().getPaymentsForGoal(goal.id);
        
        // Сортируем их по дате (чтобы шли строго по календарю)
        Collections.sort(payments, (p1, p2) -> parseDate(p1.date).compareTo(parseDate(p2.date)));

        // 2. Начинаем "раздавать" внесенные деньги
        double moneyToDistribute = goal.currentAmount;
        int paymentIndex = 0;

        while (moneyToDistribute > 0 && paymentIndex < payments.size()) {
            Payment p = payments.get(paymentIndex);
            
            if (moneyToDistribute >= p.amount) {
                // Если денег хватает на весь платеж — закрываем его
                p.status = "paid";
                moneyToDistribute -= p.amount;
            } else {
                // Если денег меньше, чем платеж — делаем его частичным (оранжевым)
                p.status = "partial";
                moneyToDistribute = 0; // Деньги кончились
            }
            
            // Сохраняем изменения статуса в базе
            db.goalDao().updatePayment(p);
            paymentIndex++;
        }

        // 3. Если остались платежи после внесенных (их не коснулись) — они остаются "pending" (в ожидании).
        // Но если цель уже достигнута (внесли больше, чем надо), мы можем удалить оставшиеся платежи.
        if (goal.currentAmount >= goal.targetAmount) {
            // Цель достигнута, удаляем все оставшиеся "pending" платежи
            for (int i = paymentIndex; i < payments.size(); i++) {
                db.goalDao().deletePayment(payments.get(i));
            }
        }
    }

    // Начальная генерация графика (используется при создании цели)
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
        p.status = "pending";
        p.date = formatDate(date);
        return p;
    }
}
