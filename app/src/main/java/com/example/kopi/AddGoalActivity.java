package com.example.kopi;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AddGoalActivity extends AppCompatActivity {
    private Spinner spinnerFrequency, spinnerDayOfWeek, spinnerDayOfMonth1, spinnerDayOfMonth2;
    private EditText etTargetAmount, etPaymentAmount;
    private TextView tvCalculatedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_goal);

        EditText etName = findViewById(R.id.etName);
        etTargetAmount = findViewById(R.id.etTargetAmount);
        etPaymentAmount = findViewById(R.id.etPaymentAmount);
        spinnerFrequency = findViewById(R.id.spinnerFrequency);
        spinnerDayOfWeek = findViewById(R.id.spinnerDayOfWeek);
        spinnerDayOfMonth1 = findViewById(R.id.spinnerDayOfMonth1);
        spinnerDayOfMonth2 = findViewById(R.id.spinnerDayOfMonth2);
        EditText etLink = findViewById(R.id.etLink);
        EditText etComment = findViewById(R.id.etComment);
        Spinner spinnerCurrency = findViewById(R.id.spinnerCurrency);
        Button btnSave = findViewById(R.id.btnSave);
        tvCalculatedDate = findViewById(R.id.tvCalculatedDate);

        // Настройка валют
        String[] currencies = {"RUB", "USD", "EUR"};
        ArrayAdapter<String> adapterCur = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapterCur.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrency.setAdapter(adapterCur);

        // Настройка частоты
        String[] frequencies = {"Раз в неделю", "Два раза в месяц", "Раз в месяц"};
        ArrayAdapter<String> adapterFreq = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, frequencies);
        adapterFreq.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequency.setAdapter(adapterFreq);

        // Настройка дней недели
        String[] daysOfWeek = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
        ArrayAdapter<String> adapterDayWeek = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, daysOfWeek);
        spinnerDayOfWeek.setAdapter(adapterDayWeek);

        // Настройка чисел месяца
        String[] daysOfMonth = new String[31];
        for (int i = 0; i < 31; i++) daysOfMonth[i] = String.valueOf(i + 1);
        ArrayAdapter<String> adapterDayMonth = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, daysOfMonth);
        spinnerDayOfMonth1.setAdapter(adapterDayMonth);
        spinnerDayOfMonth2.setAdapter(adapterDayMonth);

        // Умный показ полей в зависимости от выбора
        spinnerFrequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                spinnerDayOfWeek.setVisibility(View.GONE);
                spinnerDayOfMonth1.setVisibility(View.GONE);
                spinnerDayOfMonth2.setVisibility(View.GONE);

                if (position == 0) { // Раз в неделю
                    spinnerDayOfWeek.setVisibility(View.VISIBLE);
                } else if (position == 1) { // Два раза в месяц
                    spinnerDayOfMonth1.setVisibility(View.VISIBLE);
                    spinnerDayOfMonth2.setVisibility(View.VISIBLE);
                } else if (position == 2) { // Раз в месяц
                    spinnerDayOfMonth1.setVisibility(View.VISIBLE);
                }
                updateCalculatedDate();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Обновляем дату окончания при вводе суммы
        etPaymentAmount.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) updateCalculatedDate();
        });

        btnSave.setOnClickListener(v -> {
            try {
                Goal goal = new Goal();
                goal.name = etName.getText().toString();
                goal.targetAmount = Double.parseDouble(etTargetAmount.getText().toString());
                goal.currentAmount = 0;
                goal.currency = spinnerCurrency.getSelectedItem().toString();
                goal.link = etLink.getText().toString();
                goal.comment = etComment.getText().toString();
                goal.frequency = spinnerFrequency.getSelectedItem().toString();
                goal.paymentAmount = Double.parseDouble(etPaymentAmount.getText().toString());

                if (goal.frequency.equals("Раз в неделю")) {
                    goal.dayOfWeek = spinnerDayOfWeek.getSelectedItemPosition() + 1;
                } else if (goal.frequency.equals("Раз в месяц")) {
                    goal.dayOfMonth1 = spinnerDayOfMonth1.getSelectedItemPosition() + 1;
                } else if (goal.frequency.equals("Два раза в месяц")) {
                    goal.dayOfMonth1 = spinnerDayOfMonth1.getSelectedItemPosition() + 1;
                    goal.dayOfMonth2 = spinnerDayOfMonth2.getSelectedItemPosition() + 1;
                }

                // Рассчитываем конечную дату автоматически
                goal.endDate = DateUtils.calculateEndDate(goal.targetAmount, goal.currentAmount, goal.paymentAmount, goal.frequency, goal.dayOfMonth1, goal.dayOfMonth2);

                long id = AppDatabase.getInstance(this).goalDao().insert(goal);
                goal.id = (int) id;
                DateUtils.generateSchedule(goal, AppDatabase.getInstance(this));

                finish();
            } catch (Exception e) {
                Toast.makeText(this, "Проверьте заполнение полей", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCalculatedDate() {
        try {
            double target = Double.parseDouble(etTargetAmount.getText().toString());
            double payment = Double.parseDouble(etPaymentAmount.getText().toString());
            String freq = spinnerFrequency.getSelectedItem().toString();
            String endDate = DateUtils.calculateEndDate(target, 0, payment, freq, 0, 0);
            tvCalculatedDate.setText("Примерная дата завершения: " + endDate);
        } catch (Exception ignored) {}
    }
}
