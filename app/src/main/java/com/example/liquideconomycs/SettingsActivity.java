package com.example.liquideconomycs;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.bitcoinj.core.ECKey;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import static com.example.liquideconomycs.TrieServiceIntent.*;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        final Button loadDemo = findViewById(R.id.loadDemoPubKeys);
        loadDemo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                insertDemoInTrie();
            }
        });

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