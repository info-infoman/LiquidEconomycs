package com.infoman.liquideconomycs;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.infoman.liquideconomycs.sync.ServiceIntent;
import com.infoman.liquideconomycs.sync.WebSocketClient;

import org.bitcoinj.core.ECKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import androidx.core.util.Pair;

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
    public int maxAge;
    public int maxSyncPubKeyInSession;
    public WebSocketClient mClient;
    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        SharedPreferences sharedPref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        maxSyncPubKeyInSession = parseInt(sharedPref.getString("maxSyncPubKeyInSession", "50000"));
        maxAge = parseInt(sharedPref.getString("maxAge", "30"));
        dbHelper = new DBHelper(context);
        db = dbHelper.getWritableDatabase();
        setMyKey();
        deleteOldKeys();
    }

    public boolean find(byte[] pubKey){
        boolean r = false;
        String sPubKey = "x'" + Utils.byteToHex(pubKey) + "'";
        Cursor query = db.rawQuery("SELECT pubKey FROM main where pubKey = " + sPubKey, null);
        if (query.moveToFirst()) {
            r = true;
        }
        query.close();
        return r;
    }

    public void insertNewKeys(int day, List<byte[]> pubKeysForInsert) {
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
    public void setMyKey() {
        ContentValues cv = new ContentValues();
        Cursor query = db.rawQuery("SELECT * FROM users where privKey IS NOT NULL", null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey"), privKeyColIndex = query.getColumnIndex("privKey");
            myKey = new Pair(query.getBlob(pubKeyColIndex), query.getBlob(privKeyColIndex));
        } else {
            ECKey myECKey = new ECKey();
            byte[] myPrivKey = myECKey.getPrivKeyBytes(), myPubKey = ECKey.fromPrivate(myPrivKey).getPubKey();
            //todo ask manifest
            cv.put("pubKey", myPubKey);
            cv.put("privKey", myPrivKey);
            db.insert("users", null, cv);
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
        List<byte[]> pubKeysForInsert = new ArrayList<>();
        for (int i = 0; i < dataForInsert.length;) {
            pubKeysForInsert.add(Utils.getBytesPart(dataForInsert, i, 20));
            i = i + 20;
            if(i > maxSyncPubKeyInSession * 20){
                break;
            }
        }
        insertNewKeys(getDayMilliByIndex_(-age), pubKeysForInsert);
    }

    public void generateAnswer(int age) {
        byte[] answer = new byte[1];
        answer[0] = (byte) age;
        //LIMIT row_count OFFSET offset;
        Cursor queryMainCount = db.rawQuery("SELECT count_ FROM mainCount " +
                "where age ="+getDayMilliByIndex_(-age), null);
        if(queryMainCount.moveToNext()) {
            int countColIndex = queryMainCount.getColumnIndex("count_");
            long rnd = Utils.getRandomNumber(0L, queryMainCount.getLong(countColIndex));
            Cursor queryMain = db.rawQuery("SELECT pubKey FROM main where age ="+getDayMilliByIndex_(-age)
                    + " LIMIT "+ maxSyncPubKeyInSession +" OFFSET "+rnd, null);
            int pubKeyColIndex = queryMain.getColumnIndex("pubKey");
            while (queryMain.moveToNext()) {
                answer = Bytes.concat(answer, queryMain.getBlob(pubKeyColIndex));
            }
            queryMain.close();
        }
        queryMainCount.close();
        sendMsg(Utils.hashs, answer);
    }

    public String startActionSync(String server, String token, boolean provideService) {
        boolean serverIsStarted = false;
        if(provideService) {
            //get def server
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String defServer = sharedPref.getString("Signal_server_URL", "");
            Pair serverState = getSyncServer(defServer);
            server = (String) serverState.first;
            serverIsStarted = (boolean) serverState.second;
        }
        if(!serverIsStarted  && !Objects.equals(server, "")) {
            Intent intent = new Intent(this, ServiceIntent.class)
                    .setAction(ACTION_START_SYNC)
                    .putExtra(EXTRA_SIGNAL_SERVER, server)
                    .putExtra(EXTRA_TOKEN, token)
                    .putExtra(EXTRA_PROVIDE_SERVICE, provideService);
            Utils.startIntent(this, intent);
        }
        return server;
    }

    /* get defServer state if defServer is set
    or get any connected server if defServer not connected
    or last active server if defServer is't set*/
    private Pair getSyncServer(String server) {
        boolean isConnected = false;
        Cursor query = db.rawQuery("SELECT server FROM syncServers where server = ? " +
                "AND (" + new Date().getTime() + " - dateTimeLastSync) / 1000 < 60 LIMIT 1", new String[]{server});
        int countColIndex = query.getColumnIndex("server");
        if(query.moveToFirst()) {
            server = query.getString(countColIndex);
            isConnected = true;
        }else {
            query = db.rawQuery("SELECT server FROM syncServers where ("
                    + new Date().getTime() + " - dateTimeLastSync) / 1000 < 60 LIMIT 1", null);
            countColIndex = query.getColumnIndex("server");
            if (query.moveToFirst()) {
                server = query.getString(countColIndex);
                isConnected = true;
            } else if(Objects.equals(server, "")) {
                query = db.rawQuery("SELECT server FROM syncServers " +
                        "ORDER BY dateTimeLastSync DESC LIMIT 1", null);
                if (query.moveToFirst()) {
                    server = query.getString(countColIndex);
                }
            }
        }
        query.close();
        return new Pair(server, isConnected);
    }

    public void insertOrUpdateSyncServer(String signalServer) {
        try {
            db.beginTransaction();
            String sql = " INSERT INTO syncServers (server, dateTimeLastSync) VALUES (?, ?)";
            SQLiteStatement statement = db.compileStatement(sql);
            statement.bindString(1, signalServer); // These match to the two question marks in the sql string
            statement.bindLong(2, new Date().getTime());
            statement.executeInsert();
            db.setTransactionSuccessful();
        } catch (SQLException e) {
            Log.d("SQLException Error:", String.valueOf(e));
        } finally {
            db.endTransaction();
        }
    }

    //stat
    public ArrayList getStat(){
        ArrayList res = new ArrayList<>();
        for(int i = 0; i < maxAge; i++){
            int count = 0;
            Cursor query = db.rawQuery("SELECT count_ FROM mainCount WHERE age = "+getDayMilliByIndex_(-i), null);
            int countColIndex = query.getColumnIndex("count_");
            if(query.moveToFirst()) {
                count = query.getInt(countColIndex);
            }
            res.add(count);
            query.close();
        }
        return res;
    }

    public boolean themeIsNight() {
        boolean res = false;
        switch (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_YES:
                res = true;
                break;
            case Configuration.UI_MODE_NIGHT_NO:
                res = false;
                break;
        }
        return res;
    }
}