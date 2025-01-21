package com.infoman.liquideconomycs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;

import org.bitcoinj.core.ECKey;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import static com.infoman.liquideconomycs.Utils.getDayMilliByIndex_;
import static com.infoman.liquideconomycs.Utils.getRandomNumber;

public class SettingsActivity extends AppCompatActivity {
    private Core app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        app = (Core) getApplicationContext();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) final Button loadDemo = findViewById(R.id.loadDemo);
        loadDemo.setOnClickListener(v -> insertDemoInTrie());

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settingsBtn, new SettingsFragment())
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

            androidx.preference.EditTextPreference editTextPreference2 = getPreferenceManager().findPreference("maxSyncPubKeyInSession");
            assert editTextPreference2 != null;
            editTextPreference2.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED));
        }
    }

    private void insertDemoInTrie(){
        List<byte[]> pubKeysForInsert = new ArrayList<>();
        Context c = getApplicationContext();
        ECKey myECKey;
        byte[] myPubKey;
        for (int a = 0; a < app.maxAge; a++) {
            long rnd = getRandomNumber(0L,500);
            for (int i = 0; i < rnd; i++) {
                myECKey = new ECKey();
                myPubKey = myECKey.getPubKeyHash();
                pubKeysForInsert.add(myPubKey);
            }
            app.insertNewKeys(getDayMilliByIndex_(-a), pubKeysForInsert);
        }
    }
}