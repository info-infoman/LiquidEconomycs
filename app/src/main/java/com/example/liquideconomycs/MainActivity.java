package com.example.liquideconomycs;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.material.snackbar.Snackbar;

import org.bitcoinj.core.ECKey;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static com.example.liquideconomycs.SyncServiceIntent.startActionSync;
import static com.example.liquideconomycs.TrieServiceIntent.BROADCAST_ACTION_ANSWER;
import static com.example.liquideconomycs.TrieServiceIntent.EXTRA_ANSWER;
import static com.example.liquideconomycs.TrieServiceIntent.EXTRA_CMD;
import static com.example.liquideconomycs.TrieServiceIntent.EXTRA_MASTER;
import static com.example.liquideconomycs.TrieServiceIntent.startActionInsert;


public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String EXTRA_MESSAGE = "com.example.liquideconomycs.MESSAGE";

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


        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_ACTION_ANSWER);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.registerReceiver(mBroadcastReceiver, filter);



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

        final Button loadDemo = findViewById(R.id.LoadDemoPubKeys);
        loadDemo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                insertDemoInTrie();
            }
        });

        final Button connect = findViewById(R.id.Connect);

        connect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String signalServer = sharedPref.getString("Signal_server_URL", "");
                Core app_ = (Core) getApplicationContext();
                startActionSync(getApplicationContext(), signalServer, (byte[]) app_.myKey.first, true);
            }
        });


    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
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

    private void insertDemoInTrie(){
        for(int i=0;i<10512;i++){
            ECKey myECKey=new ECKey();
            byte[] myPubKey = myECKey.getPubKeyHash();

            byte[] age = Utils.ageToBytes();
            startActionInsert(getApplicationContext(), "Main", myPubKey, age);

        }
    }
}