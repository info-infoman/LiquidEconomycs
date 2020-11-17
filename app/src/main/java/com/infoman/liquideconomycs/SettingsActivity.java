package com.infoman.liquideconomycs;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import org.bitcoinj.core.ECKey;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import static com.infoman.liquideconomycs.DBOptimizeServiceIntent.startActionOptimise;
import static com.infoman.liquideconomycs.TrieServiceIntent.startActionInsert;
import static com.infoman.liquideconomycs.Utils.getBytesPart;

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

            androidx.preference.EditTextPreference editTextPreference = getPreferenceManager().findPreference("maxAge");
            editTextPreference.setOnBindEditTextListener(new androidx.preference.EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                }
            });
        }
    }

    private void insertDemoInTrie(){
        Context c = getApplicationContext();
        for(int i=0;i<10000;i++){
            ECKey myECKey=new ECKey();
            byte[] myPubKey = myECKey.getPubKeyHash(), age = Utils.ageToBytes();
            startActionInsert(c, "Main", myPubKey, age);

        }
        startActionOptimise(c);
    }
}