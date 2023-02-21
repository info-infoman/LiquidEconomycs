package com.infoman.liquideconomycs;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;

import com.google.common.primitives.Bytes;

import org.bitcoinj.core.ECKey;

import java.util.Date;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

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
        }
    }

    private void insertDemoInTrie(){


        Context c = getApplicationContext();
        ECKey myECKey;
        byte[] myPubKey, age, newPubKey, myPubKey_;
        age = Utils.ageToBytesTest();
        Date b = Utils.reconstructAgeFromBytes(age);
        for(int i=0;i<10;i++) {
            myECKey = new ECKey();
            myPubKey = myECKey.getPubKeyHash();
            if(Bytes.concat(myPubKey).length != 20){
                Log.d("app.trie", "ERROR! inserted key to small");
            }
            app.startActionInsert(myPubKey, age);
        }
        age = Utils.ageToBytes(new Date());
        b = Utils.reconstructAgeFromBytes(age);
        for(int i=0;i<10;i++) {
            myECKey = new ECKey();
            myPubKey = myECKey.getPubKeyHash();
            if(Bytes.concat(myPubKey).length != 20){
                Log.d("app.trie", "ERROR! inserted key to small");
            }
            app.startActionInsert(myPubKey, age);
        }

        app.startActionStopTrie();

        //delete Oldest Key

        /*
        myECKey = new ECKey();
        myPubKey = Utils.getBytesPart(myECKey.getPubKeyHash(), 0, 19);
        myPubKey_ = new byte[19];
        byte[] c_ = new byte[1];
        for(int i=0;i<2;i++) {
            c_[0] = (byte)i;
            newPubKey = Bytes.concat(myPubKey_, c_);
            age = Utils.ageToBytes();
            app.startActionInsert(app, "Main", newPubKey, age);
        }
        myPubKey_ = Bytes.concat(new byte[1], c_, new byte[17]);//00 01 00-17
        for(int i=0;i<2;i++) {
            c_[0] = (byte)i;
            newPubKey = Bytes.concat(myPubKey_, c_);
            age = Utils.ageToBytes();
            app.startActionInsert(app, "Main", newPubKey, age);
        }
        */

        //app.startActionStopTrie(app);
    }
}