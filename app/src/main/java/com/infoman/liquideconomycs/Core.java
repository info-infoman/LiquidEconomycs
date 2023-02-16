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

import androidx.core.util.Pair;

import static com.infoman.liquideconomycs.Utils.ACTION_DELETE;
import static com.infoman.liquideconomycs.Utils.ACTION_FIND;
import static com.infoman.liquideconomycs.Utils.ACTION_GENERATE_ANSWER;
import static com.infoman.liquideconomycs.Utils.ACTION_GET_HASH;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT;
import static com.infoman.liquideconomycs.Utils.ACTION_INSERT_FREE_SPACE_IN_MAP;
import static com.infoman.liquideconomycs.Utils.ACTION_START_SYNC;
import static com.infoman.liquideconomycs.Utils.ACTION_STOP_SERVICE;
import static com.infoman.liquideconomycs.Utils.ACTION_STOP_TRIE;
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
import static com.infoman.liquideconomycs.Utils.EXTRA_SPACE;
import static com.infoman.liquideconomycs.Utils.EXTRA_TOKEN;
import static com.infoman.liquideconomycs.Utils.copyAssetFolder;

public class Core extends Application {

    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private ContentValues cv;
    public Pair myKey;
    public byte[] clientPubKey;
    public WebSocketClient mClient;
    public long dateTimeLastSync;
    public int waitingIntentCount;
    public File file;

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
        ///////////create trie file//////////////////////////////////////////////////////
        String nodeDir = context.getFilesDir().getAbsolutePath() + "/trie";
        java.io.File nodeDirReference = new java.io.File(nodeDir);
        while (!nodeDirReference.exists()) {
            copyAssetFolder(context.getAssets(), "trie", nodeDir);
        }
        /////////////////////////////////////////////////////////////////////////////
        //MySingleton.initInstance();
    }

    /////////TRIE//////////////////////////////////////////////////////////////////////////////////
    public void insertNodeBlob(long position, byte[] blob, String table) {
        cv.put("pos", position);
        cv.put("node", blob);
        db.insert(table, null, cv);
        cv.clear();
    }

    public Cursor getNodeBlobs(String table) {
        return db.rawQuery("SELECT * FROM " + table + " AS tableNodeBlobs", null);
    }

    public Cursor getFreeSpace(int recordlength) {
        return db.rawQuery("SELECT * FROM freeSpace where space>=" + recordlength + " ORDER BY space ASC Limit 1", null);
    }

    public Cursor getFreeSpaceWitchCompress(long pos, int space) {
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
                " where (freeSpace.pos < " + pos + " AND freeSpace.pos + freeSpace.space  BETWEEN " + pos + " AND " + (pos + space) + ")"+
                " or (" + pos + " < freeSpace.pos AND " + (pos + space) + " BETWEEN freeSpace.pos AND freeSpace.pos + freeSpace.space)" +
                " or ("+ pos + " < freeSpace.pos AND freeSpace.pos + freeSpace.space < " + (pos + space) + ") limit 1", null);
    }

    public void insertFreeSpaceWitchCompressTrieFile(long pos, int space) {
        if (pos > 2070) {
            Cursor query = getFreeSpaceWitchCompress(pos, space);
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
                cv.put("pos", pos);
                cv.put("space", space);
                db.insert("freeSpace", null, cv);
                cv.clear();
            }
            query.close();
        }
    }

    public void addForDelete(byte[] pubKey) {
        cv.put("pubKey", pubKey);
        db.insert("forDelete", null, cv);
        cv.clear();
    }

    public void deleteFreeSpace(int id, long pos, int recordLength, int space) {
        if (recordLength < space) {
            cv.put("pos", pos + recordLength);
            cv.put("space", space - recordLength);
            db.update("freeSpace", cv, "id = ?",
                    new String[] {String.valueOf(id)});
            cv.clear();
        }
    }

    public Cursor getPubKeysForDelete() {
        return db.rawQuery("SELECT * FROM forDelete", null);
    }

    public void clearTableForDelete() {
        db.delete("forDelete", null, null);
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

    public void clearTable(String table) {
        db.delete(table, null, null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //intents
    //start
    //Trie
    public void startActionStopTrie(){
        Utils.startIntent(this, new Intent(this, com.infoman.liquideconomycs.trie.ServiceIntent.class).setAction(ACTION_STOP_TRIE));
    }

    public void startActionGenerateAnswer(byte msgType, byte[] payload) {
        Intent intent = new Intent(this, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_GENERATE_ANSWER)
                .putExtra(EXTRA_MSG_TYPE, msgType)
                .putExtra(EXTRA_PAYLOAD, payload);
        Utils.startIntent(this, intent);
    }

    public void startActionFind(String master, byte[] pubKey, long pos) {
        Intent intent = new Intent(this, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_FIND)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_PUBKEY, pubKey)
                .putExtra(EXTRA_POS, pos);
        Utils.startIntent(this, intent);
    }

    public void startActionInsert(byte[] pubKey, byte[] age) {
        Intent intent = new Intent(this, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_INSERT)
                .putExtra(EXTRA_PUBKEY, pubKey)
                .putExtra(EXTRA_AGE, age);
        Utils.startIntent(this, intent);
    }

    public void startActionInsertFreeSpaceInMap(long pos, int space) {
        Intent intent = new Intent(this, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_INSERT_FREE_SPACE_IN_MAP)
                .putExtra(EXTRA_POS, pos)
                .putExtra(EXTRA_SPACE, space);
        Utils.startIntent(this, intent);
    }

    public void startActionGetHash(Context context, String master, long pos) {
        Intent intent = new Intent(context, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_GET_HASH)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_POS, pos);
        Utils.startIntent(context, intent);
    }

    public void startActionDelete(Context context, String master, byte[] pubKey, long pos) {
        Intent intent = new Intent(context, com.infoman.liquideconomycs.trie.ServiceIntent.class)
                .setAction(ACTION_DELETE)
                .putExtra(EXTRA_MASTER, master)
                .putExtra(EXTRA_PUBKEY, pubKey)
                .putExtra(EXTRA_POS, pos);
        Utils.startIntent(context, intent);
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

    public void startActionStopSync(Context context) {
        Intent intent = new Intent(context, ServiceIntent.class).setAction(ACTION_STOP_SERVICE);
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