package com.infoman.liquideconomycs;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import androidx.core.util.Pair;

import static androidx.constraintlayout.widget.Constraints.TAG;
import static com.infoman.liquideconomycs.Utils.ACTION_DELETE;
import static com.infoman.liquideconomycs.Utils.ACTION_FIND;
import static com.infoman.liquideconomycs.Utils.ACTION_GENERATE_ANSWER;
import static com.infoman.liquideconomycs.Utils.ACTION_GET_HASH;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT;
import static com.infoman.liquideconomycs.Utils.ACTION_START_SYNC;
import static com.infoman.liquideconomycs.Utils.ACTION_STOP_SERVICE;
import static com.infoman.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_AGE;
import static com.infoman.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_CMD;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.EXTRA_MSG_TYPE;
import static com.infoman.liquideconomycs.Utils.EXTRA_PAYLOAD;
import static com.infoman.liquideconomycs.Utils.EXTRA_POS;
import static com.infoman.liquideconomycs.Utils.EXTRA_PROVIDE_SERVICE;
import static com.infoman.liquideconomycs.Utils.EXTRA_PUBKEY;
import static com.infoman.liquideconomycs.Utils.EXTRA_SIGNAL_SERVER;
import static com.infoman.liquideconomycs.Utils.EXTRA_TOKEN;
import static com.infoman.liquideconomycs.Utils.copyAssetFolder;

public class Core extends Application {

    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private ContentValues cv;
    public Pair myKey;
    public byte[] clientPubKey;
    public RandomAccessFile trie;
    public WebSocketClient mClient;
    public long dateTimeLastSync;
    public int waitingIntentCount;

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        dbHelper = new DBHelper(context);
        db = dbHelper.getWritableDatabase();
        cv = new ContentValues();
        waitingIntentCount = 0;
        //isSynchronized = false;

        setMyKey();
        ///////////init trie//////////////////////////////////////////////////////
        String nodeDir = context.getFilesDir().getAbsolutePath() + "/trie";
        File nodeDirReference = new File(nodeDir);
        while (!nodeDirReference.exists()) {
            copyAssetFolder(context.getAssets(), "trie", nodeDir);
        }
        /////////////////////////////////////////////////////////////////////////////
        if (nodeDirReference.exists()) {
            try {
                trie = new RandomAccessFile(context.getFilesDir().getAbsolutePath() + "/trie" + "/trie.dat", "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        //MySingleton.initInstance();
    }

    /////////TRIE//////////////////////////////////////////////////////////////////////////////////
    public Cursor getFreeSpace(int recordlength) {
        return db.rawQuery("SELECT * FROM freeSpace where space>=" + recordlength + " ORDER BY space ASC", null);
    }

    public Cursor checkExistFreeSpace(long pos) {
        return db.rawQuery("SELECT * FROM freeSpace where pos=" + pos, null);
    }

    //TODO optimize for paralell(limit>1)
    public Cursor getFreeSpaceWitchCompress() {
        return db.rawQuery("SELECT " +
                "freeSpaceFirst.id, " +
                "freeSpaceFirst.pos, " +
                "freeSpaceFirst.space, " +
                "freeSpaceSecond.id AS Second_id, " +
                "freeSpaceSecond.pos AS Second_pos, " +
                "freeSpaceSecond.space AS Second_space " +
                "FROM freeSpace AS freeSpaceFirst " +
                "LEFT JOIN freeSpace AS freeSpaceSecond " +
                "ON freeSpaceSecond.pos + freeSpaceSecond.space = freeSpaceFirst.pos " +
                "or freeSpaceFirst.pos+freeSpaceFirst.space = freeSpaceSecond.pos" +
                " WHERE freeSpaceSecond.id IS NOT null", null);
    }

    public void deleteFreeSpace(long pos, int recordLength, int space) {
        db.delete("freeSpace", "pos = ?", new String[]{String.valueOf(pos)});
        if (recordLength < space) {
            insertFreeSpaceWitchOutCompressTrieFile(pos + recordLength, space - recordLength);
        }
    }

    public void addPosInFreeSpaceMap(long pos, int keyNodeSize, int selfChildArraySize) {
        insertFreeSpaceWitchOutCompressTrieFile(pos, 4 + keyNodeSize + 20 + 32 + selfChildArraySize);
    }

    public void insertFreeSpaceWitchOutCompressTrieFile(long pos, int space) {
        cv.put("pos", pos);
        cv.put("space", space);
        db.insert("freeSpace", null, cv);
        cv.clear();
    }

    public void insertFreeSpaceWitchCompressTrieFile(long pos, int space, long secondPos, int secondSpace) {
        deleteFreeSpace(pos, space, space);
        deleteFreeSpace(secondPos, secondSpace, secondSpace);
        if (pos > secondPos) {
            pos = secondPos;
        }
        space = space + secondSpace;
        insertFreeSpaceWitchOutCompressTrieFile(pos, space);
    }

    public void addForDelete(byte[] pubKey) {
        cv.put("pubKey", pubKey);
        db.insert("forDelete", null, cv);
        cv.clear();
    }

    public Cursor getPubKeysForDelete() {
        return db.rawQuery("SELECT * FROM forDelete", null);
    }

    public void clearTableForDelete() {
        db.delete("forDelete", null, null);
    }

    public void optimize() {
        Cursor query = getFreeSpaceWitchCompress();
        int s, ss;
        long p, sp;
        if (query.getCount() > 0) {
            while (query.moveToNext()) {
                p = query.getLong(query.getColumnIndex("pos"));
                s = query.getInt(query.getColumnIndex("space"));
                sp = query.getLong(query.getColumnIndex("Second_pos"));
                ss = query.getInt(query.getColumnIndex("Second_space"));
                Cursor checkExistQueryP = checkExistFreeSpace(p);
                Cursor checkExistQuerySP = checkExistFreeSpace(sp);
                if (checkExistQueryP.getCount() > 0 && checkExistQuerySP.getCount() > 0) {
                    insertFreeSpaceWitchCompressTrieFile(p, s, sp, ss);
                }
                checkExistQueryP.close();
                checkExistQuerySP.close();
            }
            query.close();
            optimize();
        }else {
            query.close();
        }
    }
    /////////MyKey/////////////////////////////////////////////////////////////////////////////////
    public Pair getMyKey() {
        return myKey;
    }

    public void setMyKey() {
        Cursor query = db.rawQuery("SELECT * FROM users where privKey IS NOT NULL", null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey"), privKeyColIndex = query.getColumnIndex("privKey");
            myKey = new Pair(query.getBlob(pubKeyColIndex), query.getBlob(privKeyColIndex));
            //update self key in trie
            query.close();
        } else {
            ECKey myECKey = new ECKey();
            byte[] myPrivKey = myECKey.getPrivKeyBytes(), myPubKey = ECKey.fromPrivate(myPrivKey).getPubKey();
            //todo ask manifest
            cv.put("pubKey", myPubKey);
            cv.put("privKey", myPrivKey);
            db.insert("users", null, cv);
            //startActionInsert(this, "Core", myPubKey, Utils.ageToBytes());
            cv.clear();
            query.close();
            setMyKey();
        }

    }

    /////////Sync/////////////////////////////////////////////////////////////////////////////////

    public void addClient(byte[] pubKey) {
        cv.put("pubKey", pubKey);
        db.insert("clients", null, cv);
        cv.clear();
    }

    public Cursor getClients() {
        return db.rawQuery("SELECT " +
                "clients.pubKey " +
                "FROM clients AS clients", null);
    }

    public void deleteClient(byte[] pubKey) {
        db.delete("clients", "pubKey = ?", new String[]{String.valueOf(pubKey)});
    }

    public Cursor getPrefixByPos(long pos) {
        return db.rawQuery("SELECT * FROM sync where pos=" + pos, null);
    }

    public void addPrefixByPos(long pos, byte[] key, byte[] age, boolean exist) {
        cv.put("pos", pos);
        cv.put("prefix", key);
        cv.put("age", age);
        cv.put("exist", exist ? 1 : 0);
        db.insert("sync", null, cv);
        cv.clear();
    }

    public void clearPrefixTable() {
        db.delete("sync", null, null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //intents
    //start
    //Trie
    public void startActionStopTrie(Context context){
        Utils.startIntent(context, new Intent(context, TrieServiceIntent.class).setAction(ACTION_STOP_SERVICE));
    }

    public void startActionGenerateAnswer(Context context, byte msgType, byte[] payload) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
                .setAction(ACTION_GENERATE_ANSWER)
                .putExtra(EXTRA_MSG_TYPE, msgType)
                .putExtra(EXTRA_PAYLOAD, payload);
        Utils.startIntent(context, intent);
    }

    public void startActionFind(Context context, String master, byte[] pubKey, long pos) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
                .setAction(ACTION_FIND)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_PUBKEY, pubKey)
                .putExtra(EXTRA_POS, pos);
        Utils.startIntent(context, intent);
    }

    public void startActionInsert(Context context, String master, byte[] pubKey, byte[] age) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
                .setAction(ACTION_INSERT)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_PUBKEY, pubKey)
                .putExtra(EXTRA_AGE, age);
        Utils.startIntent(context, intent);
    }

    public void startActionGetHash(Context context, String master, long pos) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
                .setAction(ACTION_GET_HASH)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_POS, pos);
        Utils.startIntent(context, intent);
    }

    public void startActionDelete(Context context, String master, byte[] pubKey, long pos) {
        Intent intent = new Intent(context, TrieServiceIntent.class)
                .setAction(ACTION_DELETE)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_PUBKEY, pubKey)
                .putExtra(EXTRA_POS, pos);
        Utils.startIntent(context, intent);
    }

    //Sync
    public void startActionSync(Context context, String master, String signalServer, String token, boolean Provide_service) {
        if(Provide_service) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            signalServer = sharedPref.getString("Signal_server_URL", "");
            token = sharedPref.getString("Signal_server_Token", "");
        }
        Intent intent = new Intent(context, SyncServiceIntent.class)
                .setAction(ACTION_START_SYNC)
                .putExtra(EXTRA_SIGNAL_SERVER, signalServer)
                .putExtra(EXTRA_PROVIDE_SERVICE, Provide_service)
                .putExtra(EXTRA_TOKEN, token)
                .putExtra(EXTRA_MASTER, master);
        Utils.startIntent(context, intent);
    }

    public void startActionStopSync(Context context) {
        Intent intent = new Intent(context, SyncServiceIntent.class).setAction(ACTION_STOP_SERVICE);
        Utils.startIntent(context, intent);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //broadcast
    public void broadcastActionMsg(String master, String cmd, byte[] answer) {
        Intent intent = new Intent(BROADCAST_ACTION_ANSWER)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_CMD, cmd)
                .putExtra(EXTRA_ANSWER, answer);
        sendBroadcast(intent);
    }



}