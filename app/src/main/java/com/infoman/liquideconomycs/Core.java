package com.infoman.liquideconomycs;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import com.infoman.liquideconomycs.sync.ServiceIntent;
import com.infoman.liquideconomycs.sync.WebSocketClient;
import com.infoman.liquideconomycs.trie.File;

import org.bitcoinj.core.ECKey;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

import androidx.core.util.Pair;

import static com.infoman.liquideconomycs.Utils.ACTION_FIND;
import static com.infoman.liquideconomycs.Utils.ACTION_GENERATE_ANSWER;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT;
import static com.infoman.liquideconomycs.Utils.ACTION_START_SYNC;
import static com.infoman.liquideconomycs.Utils.BROADCAST_ACTION_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_AGE;
import static com.infoman.liquideconomycs.Utils.EXTRA_ANSWER;
import static com.infoman.liquideconomycs.Utils.EXTRA_CMD;
import static com.infoman.liquideconomycs.Utils.EXTRA_MASTER;
import static com.infoman.liquideconomycs.Utils.EXTRA_MSG_TYPE;
import static com.infoman.liquideconomycs.Utils.EXTRA_PAYLOAD;
import static com.infoman.liquideconomycs.Utils.EXTRA_PROVIDE_SERVICE;
import static com.infoman.liquideconomycs.Utils.EXTRA_PUBKEY;
import static com.infoman.liquideconomycs.Utils.EXTRA_SIGNAL_SERVER;
import static com.infoman.liquideconomycs.Utils.EXTRA_TOKEN;
import static com.infoman.liquideconomycs.Utils.compareDate;
import static com.infoman.liquideconomycs.Utils.getDayMilliByIndex;
import static java.lang.Long.parseLong;

public class Core extends Application {

    private DBHelper dbHelper;
    private static SQLiteDatabase db;
    public static Pair myKey;
    public byte[] clientPubKey;
    public WebSocketClient mClient;
    public long dateTimeLastSync;
    public static int[] waitingIntentCounts;
    public long insertedPubKeyInSession;
    public static File[] files;

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        SharedPreferences sharedPref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        long maxAge = parseLong(sharedPref.getString("maxAge", "30"));
        files = new File[(int) maxAge];
        dbHelper = new DBHelper(context);
        db = dbHelper.getWritableDatabase();
        waitingIntentCounts = new int[(int) maxAge];
        insertedPubKeyInSession = 0L;
        setMyKey();
        ///////////create trie file//////////////////////////////////////////////////////
        String nodeDir = context.getFilesDir().getAbsolutePath() + "/trie";
        java.io.File nodeDirReference = new java.io.File(nodeDir);
        while (!nodeDirReference.exists()) {
            new java.io.File(nodeDir).mkdirs();
        }
        java.io.File[] trieFiles = nodeDirReference.listFiles();
        for(java.io.File file: trieFiles){
            long fileDate = parseLong(file.getName());
            if(compareDate(new Date(), new Date(fileDate))>maxAge){
                file.delete();
            }
        }

        for(int i = 0; i < maxAge; i++){
            long fileName = getDayMilliByIndex(i);
            java.io.File nodeFileReference = new java.io.File(getFilesDir().getAbsolutePath() + "/trie/" + fileName);
            if (!nodeFileReference.exists()) {
                try {
                    nodeFileReference.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                files[i] = new File(context,getFilesDir().getAbsolutePath() + "/trie" + "/" + fileName, "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        /////////////////////////////////////////////////////////////////////////////
    }

    /////////TRIE//////////////////////////////////////////////////////////////////////////////////
    public void insertNodeBlob(long file, long position, byte[] blob, String table) {
        ContentValues cv = new ContentValues();
        cv.put("file", file);
        cv.put("pos", position);
        cv.put("node", blob);
        db.insert(table, null, cv);
        cv.clear();
    }

    public Cursor getNodeBlobs(String table) {
        return db.rawQuery("SELECT * FROM " + table + " AS tableNodeBlobs", null);
    }

    public Cursor getFreeSpace(long file, int recordlength) {
        return db.rawQuery("SELECT * FROM freeSpace where space>=" + recordlength + " AND file = "+file+" ORDER BY space ASC Limit 1", null);
    }

    public Cursor getFreeSpaceWitchCompress(long file, long pos, int space) {
        return db.rawQuery("SELECT " +
                "freeSpace.id," +
                " CASE WHEN " + pos + " < freeSpace.pos "+
                " then "+
                    pos +
                " else " +
                    "freeSpace.pos " +
                " end as pos, " +
                " CASE WHEN "+ pos + " < freeSpace.pos AND freeSpace.pos + freeSpace.space < " + (pos + space) +
                " then " +
                    space +
                " else " +
                    " CASE WHEN freeSpace.pos < " + pos + " AND freeSpace.pos + freeSpace.space  BETWEEN " + pos + " AND " + (pos + space) +
                    " then " +
                        (pos + space) +" - freeSpace.pos " +
                    " else "+
                        " freeSpace.pos + freeSpace.space - " + pos +
                    " end " +
                " end as space " +
                " FROM freeSpace AS freeSpace " +
                " where freeSpace.file = "+file+" AND((freeSpace.pos < " + pos + " AND freeSpace.pos + freeSpace.space  BETWEEN " + pos + " AND " + (pos + space) + ")"+
                " or (" + pos + " < freeSpace.pos AND " + (pos + space) + " BETWEEN freeSpace.pos AND freeSpace.pos + freeSpace.space)" +
                " or ("+ pos + " < freeSpace.pos AND freeSpace.pos + freeSpace.space < " + (pos + space) + ")) limit 1", null);
    }

    public void insertFreeSpaceWitchCompressTrieFile(long file, long pos, int space) {
        ContentValues cv = new ContentValues();
        if (pos > 2070) {
            Cursor query = getFreeSpaceWitchCompress(file, pos, space);
            if (query.moveToFirst()) {
                long p = query.getLong(query.getColumnIndex("pos"));
                int s = query.getInt(query.getColumnIndex("space"));
                int id = query.getInt(query.getColumnIndex("id"));
                cv.put("pos", p);
                cv.put("space", s);
                // обновляем по id
                db.update("freeSpace", cv, "id = ?",
                        new String[]{String.valueOf(id)});
                cv.clear();
            } else {
                cv.put("file", file);
                cv.put("pos", pos);
                cv.put("space", space);
                db.insert("freeSpace", null, cv);
                cv.clear();
            }
            query.close();
        }
    }

    public void deleteFreeSpace(long file, int id, long pos, int recordLength, int space) {
        ContentValues cv = new ContentValues();
        if (recordLength < space) {
            cv.put("file", file);
            cv.put("pos", pos + recordLength);
            cv.put("space", space - recordLength);
            db.update("freeSpace", cv, "id = ?",
                    new String[] {String.valueOf(id)});
            cv.clear();
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

    /////////Sync/////////////////////////////////////////////////////////////////////////////////

    public void addClient(byte[] pubKey) {
        boolean clientIsFound = false;
        ContentValues cv = new ContentValues();
        Cursor query = db.rawQuery("SELECT clients.pubKey FROM clients", null);
        while (query.moveToNext()) {
            byte[] pk = query.getBlob(query.getColumnIndex("pubKey"));
            if (pubKey == pk) {
                clientIsFound = true;
                break;
            }
        }
        if (!clientIsFound) {
            cv.put("pubKey", pubKey);
            db.insert("clients", null, cv);
            cv.clear();
        }
    }

    public Cursor getClients() {
        return db.rawQuery("SELECT " +
                "clients.pubKey " +
                "FROM clients AS clients", null);
    }

    public Cursor getSyncTable() {
        return db.rawQuery("SELECT * FROM sync AS sync", null);
    }

    public Cursor getPrefixByPos(int index, long pos) {
        return db.rawQuery("SELECT * FROM sync where age = "+index+" AND pos = " + pos, null);
    }

    public void addPrefixByPos(long pos, byte[] key, int age, boolean exist) {
        ContentValues cv = new ContentValues();
        cv.put("pos", pos);
        cv.put("prefix", key);
        cv.put("age", age);
        cv.put("exist", exist ? 1 : 0);
        db.insert("sync", null, cv);
        cv.clear();
    }

    public void clearTable(String table) {
        db.delete(table, null, null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //intents
    //start
    //Trie

    public void startActionGenerateAnswer(byte msgType, byte[] payload) {
        Intent intent = new Intent(this, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_GENERATE_ANSWER)
                .putExtra(EXTRA_MSG_TYPE, msgType)
                .putExtra(EXTRA_PAYLOAD, payload);
        Utils.startIntent(this, intent);
    }

    public void startActionFind(String master, byte[] pubKey) {
        Intent intent = new Intent(this, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_FIND)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_PUBKEY, pubKey);
        Utils.startIntent(this, intent);
    }

    public void startActionInsert(byte[] pubKey, int age) {
        Intent intent = new Intent(this, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_INSERT)
                .putExtra(EXTRA_PUBKEY, pubKey)
                .putExtra(EXTRA_AGE, age);
        Utils.startIntent(this, intent);
    }

    //Sync
    public void startActionSync(String master, String signalServer, String token, boolean Provide_service) {
        if(Provide_service) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            signalServer = sharedPref.getString("Signal_server_URL", "");
            token = sharedPref.getString("Signal_server_Token", "");
        }
        Intent intent = new Intent(this, ServiceIntent.class)
                .setAction(ACTION_START_SYNC)
                .putExtra(EXTRA_SIGNAL_SERVER, signalServer)
                .putExtra(EXTRA_PROVIDE_SERVICE, Provide_service)
                .putExtra(EXTRA_TOKEN, token)
                .putExtra(EXTRA_MASTER, master);
        Utils.startIntent(this, intent);
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