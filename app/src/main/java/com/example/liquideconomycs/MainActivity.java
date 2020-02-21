package com.example.liquideconomycs;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import static com.example.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.example.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.example.liquideconomycs.Utils.EXTRA_CMD;
import static com.example.liquideconomycs.Utils.EXTRA_MASTER;
import static com.example.liquideconomycs.Utils.EXTRA_MESSAGE;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final int MY_PERMISSION_REQUEST_INTERNET = 0;

    private ViewGroup mainLayout;

    // handler for received data from service
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BROADCAST_ACTION_ANSWER)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = intent.getStringExtra(EXTRA_CMD);
                final byte[] answer = intent.getByteArrayExtra(EXTRA_ANSWER);
                if(master=="Main"){

                }
                //resultTextView.setText(param);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mainLayout = (ViewGroup) findViewById(R.id.main_layout);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {} else {
            requestINTERNETPermission();
        }

        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));

        final Button Provide_service = findViewById(R.id.Provide_service);
        Provide_service.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                Intent intent;
                intent = new Intent(getApplicationContext(), ScanerActivity.class);
                intent.putExtra(EXTRA_MESSAGE, "Provide_service");
                startActivity(intent);
            }
        });

        final Button Accept_service = findViewById(R.id.Accept_service);
        Accept_service.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent;
                intent = new Intent(getApplicationContext(), ScanerActivity.class);
                intent.putExtra(EXTRA_MESSAGE, "Accept_service");

                startActivity(intent);
            }
        });

        final Button settings = findViewById(R.id.settings);
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent;
                intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
            }
        });



    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_ANSWER));
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                     @NonNull int[] grantResults) {
        AtomicBoolean ret= new AtomicBoolean(true);
        if (requestCode == MY_PERMISSION_REQUEST_INTERNET) {
            ret.set(false);
        }
        if(ret.get()){
            return;
        }
        if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(mainLayout, "Permission was granted.", Snackbar.LENGTH_SHORT).show();
        } else {
            Snackbar.make(mainLayout, "Permission request was denied.", Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    private void requestINTERNETPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
            Snackbar.make(mainLayout, "INTERNET access is required to display the INTERNET preview.",
                    Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                            Manifest.permission.INTERNET
                    }, MY_PERMISSION_REQUEST_INTERNET);
                }
            }).show();
        } else {
            Snackbar.make(mainLayout, "Permission is not available. Requesting INTERNET permission.",
                    Snackbar.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.INTERNET
            }, MY_PERMISSION_REQUEST_INTERNET);
        }
    }


}