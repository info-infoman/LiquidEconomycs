package com.infoman.liquideconomycs;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.infoman.liquideconomycs.sync.ServiceIntent;
import com.infoman.liquideconomycs.sync.WebSocketClient;

import org.bitcoinj.core.ECKey;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import androidx.core.util.Pair;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static com.infoman.liquideconomycs.Utils.ACTION_START_SYNC;
import static com.infoman.liquideconomycs.Utils.EXTRA_PROVIDE_SERVICE;
import static com.infoman.liquideconomycs.Utils.EXTRA_SIGNAL_SERVER;
import static com.infoman.liquideconomycs.Utils.EXTRA_TOKEN;
import static com.infoman.liquideconomycs.Utils.getDayMilliByIndex_;
import static java.lang.Integer.parseInt;

public class Core extends Application {

    private DBHelper dbHelper;
    private static SQLiteDatabase db;
    public static Pair myKey;
    public WebSocketClient mClient;
    public long dateTimeLastSync;
    public int maxAge;
    public List<byte[]> pubKeysForInsert;
    public boolean provideService;
    public int maxSyncPubKeyInSession;
    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        SharedPreferences sharedPref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        maxSyncPubKeyInSession = parseInt(sharedPref.getString("maxSyncPubKeyInSession", "50000"));
        maxAge = parseInt(sharedPref.getString("maxAge", "30"));
        dbHelper = new DBHelper(context);
        db = dbHelper.getWritableDatabase();
        pubKeysForInsert = new ArrayList<>();
        setMyKey();
        deleteOldKeys();
    }

    public boolean find(byte[] pubKey){
        boolean r = false;
        Cursor query = db.rawQuery("SELECT ONE pubKey FROM main where pubKey =" + pubKey, null);
        if (query.moveToFirst()) {
            r = true;
        }
        query.close();
        return r;
    }

    public void insertNewKeys(int day) {
        try {
            db.beginTransaction();
            String sql = " INSERT INTO main (pubKey, age) VALUES (?, ?)";
            SQLiteStatement statement = db.compileStatement(sql);
            ListIterator<byte[]> itrv = pubKeysForInsert.listIterator();
            while (itrv.hasNext()) {
                byte[] pubKey = itrv.next();
                statement.bindBlob(1, pubKey); // These match to the two question marks in the sql string
                statement.bindLong(2, day);
                statement.executeInsert();
            }
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            Log.d("SQLException Error:", String.valueOf(e));
        } finally {
            db.endTransaction();
        }
    }

    private void deleteOldKeys() {
        try {
            db.beginTransaction();

            String sql = " DELETE FROM main WHERE age < ?";
            SQLiteStatement statement = db.compileStatement(sql);
            statement.bindLong(1, getDayMilliByIndex_(- maxAge));
            statement.executeUpdateDelete();

            String sql_ = " DELETE FROM mainCount WHERE age < ?";
            SQLiteStatement statement_ = db.compileStatement(sql_);
            statement_.bindLong(1, getDayMilliByIndex_(- maxAge));
            statement_.executeUpdateDelete();

            db.setTransactionSuccessful();
        } catch (SQLException e) {
            Log.d("SQLException Error:", String.valueOf(e));
        } finally {
            db.endTransaction();
        }
    }

    /////////MyKey/////////////////////////////////////////////////////////////////////////////////
    public Pair getMyKey() {
        return myKey;
    }

    public void setMyKey() {
        ContentValues cv = new ContentValues();
        Cursor query = db.rawQuery("SELECT * FROM users where privKey IS NOT NULL", null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey"), privKeyColIndex = query.getColumnIndex("privKey");
            myKey = new Pair(query.getBlob(pubKeyColIndex), query.getBlob(privKeyColIndex));
            //update self key in trie

        } else {
            ECKey myECKey = new ECKey();
            byte[] myPrivKey = myECKey.getPrivKeyBytes(), myPubKey = ECKey.fromPrivate(myPrivKey).getPubKey();
            //todo ask manifest
            cv.put("pubKey", myPubKey);
            cv.put("privKey", myPrivKey);
            db.insert("users", null, cv);
            //startActionInsert(this, "Core", myPubKey, Utils.ageToBytes());
            cv.clear();
            setMyKey();
        }
        query.close();
    }

    ////////sync///////////////////////////////////////////////////////////////////////////////////
    public void sendMsg(byte msgType, byte[] payload) {
        if (mClient.mListener != null && mClient.isConnected() && payload.length > 0) {
            byte[] type = new byte[1];
            type[0] = msgType;
            mClient.send(Bytes.concat(type, payload));
        }
    }

    public void insert(byte[] dataForInsert, int age) {
        pubKeysForInsert.clear();
        for (int i = 0; i < dataForInsert.length;) {
            pubKeysForInsert.add(Utils.getBytesPart(dataForInsert, i, 20));
            i = i + 20;
            if(i > maxSyncPubKeyInSession * 20){
                break;
            }
        }
        insertNewKeys(-age);
    }

    public void generateAnswer(int age) {

        byte[] answer = new byte[1];
        answer[0] = (byte) age;
        //LIMIT row_count OFFSET offset;
        Cursor query_ = db.rawQuery("SELECT count FROM mainCount where age ="+getDayMilliByIndex_(-age), null);
        if(query_.getCount() == 0){
            return;
        }
        query_.moveToNext();
        int countColIndex = query_.getColumnIndex("count");
        long rnd = Utils.getRandomNumber(0L, query_.getLong(countColIndex));
        query_.close();

        Cursor query = db.rawQuery("SELECT pubKey FROM main where age ="+getDayMilliByIndex_(-age)
                + " LIMIT "+ maxSyncPubKeyInSession +" OFFSET "+rnd, null);
        int pubKeyColIndex = query.getColumnIndex("pubKey");
        while (query.moveToNext()) {
            answer = Bytes.concat(answer, query.getBlob(pubKeyColIndex));
        }
        if(answer.length > 1) {
            sendMsg(Utils.hashs, answer);
        }
        query.close();
    }

    public void startActionSync(String signalServer, String token) {
        if(provideService) {
            SharedPreferences sharedPref = getDefaultSharedPreferences(this);
            signalServer = sharedPref.getString("Signal_server_URL", "");
            token = sharedPref.getString("Signal_server_Token", "");
        }
        Intent intent = new Intent(this, ServiceIntent.class)
                .setAction(ACTION_START_SYNC)
                .putExtra(EXTRA_SIGNAL_SERVER, signalServer)
                .putExtra(EXTRA_TOKEN, token)
                .putExtra(EXTRA_PROVIDE_SERVICE, provideService);
        Utils.startIntent(this, intent);
    }

}