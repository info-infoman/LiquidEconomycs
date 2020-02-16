package com.example.liquideconomycs;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.view.ViewGroup;

public class SettingsActivity extends AppCompatActivity {

    private ViewGroup mainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mainLayout = (ViewGroup) findViewById(R.id.settings_layout);

        Toolbar toolbar = findViewById(R.id.toolbarS);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
