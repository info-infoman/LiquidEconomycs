package com.infoman.liquideconomycs;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;

import com.google.common.primitives.Bytes;

import org.bitcoinj.core.ECKey;

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

            androidx.preference.EditTextPreference editTextPreference2 = getPreferenceManager().findPreference("maxSyncPubKeyInSession");
            assert editTextPreference2 != null;
            editTextPreference2.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED));
        }
    }

    private void insertDemoInTrie(){
        Context c = getApplicationContext();
        ECKey myECKey;
        byte[] myPubKey;
        Log.d("app.test", "START");
        for(int i=0;i<10000;i++) {
            myECKey = new ECKey();
            myPubKey = myECKey.getPubKeyHash();
            if(Bytes.concat(myPubKey).length != 20){
                Log.d("app.trie", "ERROR! inserted key to small");
            }
            app.pubKeysForInsert.add(myPubKey);
        }
        Log.d("app.test", "START INSERT " + getDayMilliByIndex_(-2));
        app.insertNewKeys(getDayMilliByIndex_(-2));
        Log.d("app.test", "END INSERT");
        Log.d("app.test", "START UPDATE " + getDayMilliByIndex_(-0));
        app.insertNewKeys(getDayMilliByIndex_(-0));
        Log.d("app.test", "END UPDATE");
        /*for(int i=0;i<10000;i++) {
            myECKey = new ECKey();
            myPubKey = myECKey.getPubKeyHash();
            if(Bytes.concat(myPubKey).length != 20){
                Log.d("app.trie", "ERROR! inserted key to small");
            }
            app.startActionInsert(myPubKey, 0);
        }*/
    }
}