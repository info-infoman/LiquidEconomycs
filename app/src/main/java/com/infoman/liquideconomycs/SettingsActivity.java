package com.infoman.liquideconomycs;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;

import org.bitcoinj.core.ECKey;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import static com.infoman.liquideconomycs.Utils.ACTION_INSERT;
import static com.infoman.liquideconomycs.Utils.ACTION_STOP_SERVICE;
import static com.infoman.liquideconomycs.Utils.EXTRA_AGE;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.EXTRA_PUBKEY;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        final Button loadDemo = findViewById(R.id.loadDemoPubKeys);
        loadDemo.setOnClickListener(v -> insertDemoInTrie());

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            androidx.preference.EditTextPreference editTextPreference = getPreferenceManager().findPreference("maxAge");
            assert editTextPreference != null;
            editTextPreference.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED));
        }
    }

    private void insertDemoInTrie(){
        Context c = getApplicationContext();
        ECKey myECKey = new ECKey();
        byte[] myPubKey = myECKey.getPubKeyHash(), age = Utils.ageToBytes();

        Intent intent = new Intent(c, TrieServiceIntent.class)
                .setAction(ACTION_INSERT)
                .putExtra(EXTRA_MASTER, "Main")
                .putExtra(EXTRA_PUBKEY, myPubKey)
                .putExtra(EXTRA_AGE, age);
        c.startService(intent);
        for(int i=0;i<10000;i++) {
            myECKey = new ECKey();
            myPubKey = myECKey.getPubKeyHash();
            age = Utils.ageToBytes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(new Intent(getApplicationContext(), TrieServiceIntent.class).setAction(ACTION_INSERT)
                        .putExtra(EXTRA_MASTER, "Main")
                        .putExtra(EXTRA_PUBKEY, myPubKey)
                        .putExtra(EXTRA_AGE, age));
            }else{
                startService(new Intent(getApplicationContext(), TrieServiceIntent.class).setAction(ACTION_INSERT)
                        .putExtra(EXTRA_MASTER, "Main")
                        .putExtra(EXTRA_PUBKEY, myPubKey)
                        .putExtra(EXTRA_AGE, age));
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), TrieServiceIntent.class).setAction(ACTION_STOP_SERVICE));
        }else{
            startService(new Intent(getApplicationContext(), TrieServiceIntent.class).setAction(ACTION_STOP_SERVICE));
        }
    }
}