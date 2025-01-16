package com.infoman.liquideconomycs;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;

import org.bitcoinj.core.ECKey;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import static com.infoman.liquideconomycs.Utils.getDayMilliByIndex_;

public class SettingsActivity extends AppCompatActivity {
    private Core app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        app = (Core) getApplicationContext();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        /*final Button loadDemo = findViewById(R.id.loadDemoPubKeys);
        loadDemo.setOnClickListener(v -> insertDemoInTrie());*/

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
        for(int i=0;i<1000;i++) {
            myECKey = new ECKey();
            myPubKey = myECKey.getPubKeyHash();
            pubKeysForInsert.add(myPubKey);
        }
        app.insertNewKeys(getDayMilliByIndex_(-0), pubKeysForInsert);
    }
}