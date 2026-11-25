package com.example.kopi;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private Switch switchNotifications;
    private TimePicker timePicker;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchNotifications = findViewById(R.id.switchNotifications);
        timePicker = findViewById(R.id.timePicker);
        Button btnSave = findViewById(R.id.btnSaveSettings);

        // Включаем строгий 24-часовой формат (убираем AM/PM)
        timePicker.setIs24HourView(true);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean("notif_enabled", true);
        int hour = prefs.getInt("notif_hour", 9);
        int minute = prefs.getInt("notif_minute", 0);

        switchNotifications.setChecked(isEnabled);
        timePicker.setHour(hour);
        timePicker.setMinute(minute);

        btnSave.setOnClickListener(v -> {
            int selectedHour = timePicker.getHour();
            int selectedMinute = timePicker.getMinute();

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("notif_enabled", switchNotifications.isChecked());
            editor.putInt("notif_hour", selectedHour);
            editor.putInt("notif_minute", selectedMinute);
            editor.apply();

            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
