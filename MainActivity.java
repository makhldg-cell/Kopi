package com.example.kopi;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    EditText etName, etAmount, etLink, etComment;
    Spinner spinnerCurrency;
    Button btnSave;
    TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etName = findViewById(R.id.etName);
        etAmount = findViewById(R.id.etAmount);
        etLink = findViewById(R.id.etLink);
        etComment = findViewById(R.id.etComment);
        spinnerCurrency = findViewById(R.id.spinnerCurrency);
        btnSave = findViewById(R.id.btnSave);
        tvResult = findViewById(R.id.tvResult);

        // Настраиваем выпадающий список валют
        String[] currencies = {"RUB", "USD", "EUR"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrency.setAdapter(adapter);

        // Обработчик нажатия на кнопку
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveGoal();
            }
        });
    }

    private void saveGoal() {
        String name = etName.getText().toString().trim();
        String amountStr = etAmount.getText().toString().trim();
        String link = etLink.getText().toString().trim();
        String comment = etComment.getText().toString().trim();
        String currency = spinnerCurrency.getSelectedItem().toString();

        if (name.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "Введите название и сумму", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Сумма должна быть числом", Toast.LENGTH_SHORT).show();
            return;
        }

        // Выводим результат на экран
        String result = "Цель: " + name + "\n" +
                "Сумма: " + amount + " " + currency + "\n" +
                "Ссылка: " + (link.isEmpty() ? "нет" : link) + "\n" +
                "Комментарий: " + (comment.isEmpty() ? "нет" : comment);

        tvResult.setText(result);
        Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show();
    }
}