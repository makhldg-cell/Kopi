package com.example.kopi;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AddGoalActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_goal);

        EditText etName = findViewById(R.id.etName);
        EditText etTargetAmount = findViewById(R.id.etTargetAmount);
        EditText etPlannedPayment = findViewById(R.id.etPlannedPayment);
        EditText etTargetDate = findViewById(R.id.etTargetDate);
        EditText etLink = findViewById(R.id.etLink);
        EditText etComment = findViewById(R.id.etComment);
        Spinner spinnerCurrency = findViewById(R.id.spinnerCurrency);
        Spinner spinnerFrequency = findViewById(R.id.spinnerFrequency);
        Button btnSave = findViewById(R.id.btnSave);

        String[] currencies = {"RUB", "USD", "EUR"};
        ArrayAdapter<String> adapterCur = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapterCur.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrency.setAdapter(adapterCur);

        String[] frequencies = {"Раз в неделю", "Два раза в месяц", "Раз в месяц", "Свой вариант"};
        ArrayAdapter<String> adapterFreq = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, frequencies);
        adapterFreq.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrequency.setAdapter(adapterFreq);

        btnSave.setOnClickListener(v -> {
            try {
                Goal goal = new Goal();
                goal.name = etName.getText().toString();
                goal.targetAmount = Double.parseDouble(etTargetAmount.getText().toString());
                goal.currentAmount = 0;
                goal.currency = spinnerCurrency.getSelectedItem().toString();
                goal.paymentFrequency = spinnerFrequency.getSelectedItem().toString();
                goal.targetDate = etTargetDate.getText().toString();
                goal.plannedPayment = etPlannedPayment.getText().toString().isEmpty() ? 0 : Double.parseDouble(etPlannedPayment.getText().toString());
                goal.link = etLink.getText().toString();
                goal.comment = etComment.getText().toString();

                AppDatabase.getInstance(this).goalDao().insert(goal);
                finish();
            } catch (Exception e) {
                Toast.makeText(this, "Проверьте правильность заполнения полей", Toast.LENGTH_SHORT).show();
            }
        });
    }
                  }
