package com.infoman.liquideconomycs;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {
    private Core app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        app = (Core) getApplicationContext();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.help_activity);
    }
}
