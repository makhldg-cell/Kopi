package com.example.kopi;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
    private Spinner spinnerFrequency;
    private EditText etDayOfWeek, etDayOfMonth1, etDayOfMonth2, etTargetAmount, etPaymentAmount;
    private TextView tvCalculatedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_goal);

        EditText etName = findViewById(R.id.etName);
        etTargetAmount = findViewById(R.id.etTargetAmount);
        etPaymentAmount = findViewById(R.id.etPaymentAmount);
        spinnerFrequency = findViewById(R.id.spinnerFrequency);
        etDayOfWeek = findViewById(R.id.etDayOfWeek);
        etDayOfMonth1 = findViewById(R.id.etDayOfMonth1);
        etDayOfMonth2 = findViewById(R.id.etDayOfMonth2);
        EditText etLink = findViewById(R.id.etLink);
        EditText etComment = findViewById(R.id.etComment);
        Spinner spinnerCurrency = findViewById(R.id.spinnerCurrency);
        Button btnSave = findViewById(R.id.btnSave);
        tvCalculatedDate = findViewById(R.id.tvCalculatedDate);

        String[] currencies = {"RUB", "USD", "EUR"};
        ArrayAdapter<String> adapterCur = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapterCur.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrency.setAdapter(adapterCur);

        String[] frequencies = {"Раз в неделю", "Два раза в месяц", "Раз в месяц"};
        ArrayAdapter<String> adapterFreq = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, frequencies);
        adapterFreq.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequency.setAdapter(adapterFreq);

        spinnerFrequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                etDayOfWeek.setVisibility(View.GONE);
                etDayOfMonth1.setVisibility(View.GONE);
                etDayOfMonth2.setVisibility(View.GONE);

                if (position == 0) etDayOfWeek.setVisibility(View.VISIBLE);
                else if (position == 1) { etDayOfMonth1.setVisibility(View.VISIBLE); etDayOfMonth2.setVisibility(View.VISIBLE); }
                else if (position == 2) etDayOfMonth1.setVisibility(View.VISIBLE);
                
                updateCalculatedDate();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Слушатель для мгновенного расчета
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { updateCalculatedDate(); }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        etPaymentAmount.addTextChangedListener(watcher);
        etTargetAmount.addTextChangedListener(watcher);

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
                    goal.dayOfWeek = Integer.parseInt(etDayOfWeek.getText().toString());
                } else if (goal.frequency.equals("Раз в месяц")) {
                    goal.dayOfMonth1 = Integer.parseInt(etDayOfMonth1.getText().toString());
                } else if (goal.frequency.equals("Два раза в месяц")) {
                    goal.dayOfMonth1 = Integer.parseInt(etDayOfMonth1.getText().toString());
                    goal.dayOfMonth2 = Integer.parseInt(etDayOfMonth2.getText().toString());
                }

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
            tvCalculatedDate.setText("Прогноз завершения: " + endDate);
        } catch (Exception ignored) {}
    }
}
