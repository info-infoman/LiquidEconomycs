package com.infoman.liquideconomycs;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import androidx.core.util.Pair;

import static com.infoman.liquideconomycs.Utils.LEAF;
import static com.infoman.liquideconomycs.Utils.copyAssetFolder;

public class Core extends Application {
    public long dateTimeLastSync;
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private ContentValues cv;
    public Pair myKey;
    public RandomAccessFile trie;
    public WebSocketClient mClient;
    public boolean isSynchronized;

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        dbHelper        = new DBHelper(context);
        db              = dbHelper.getWritableDatabase();
        cv              = new ContentValues();
        mClient         = null;
        isSynchronized  = false;

        setMyKey();
        ///////////init trie//////////////////////////////////////////////////////
        String nodeDir=context.getFilesDir().getAbsolutePath()+"/trie";
        File nodeDirReference=new File(nodeDir);
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
        return db.rawQuery("SELECT * FROM freeSpace where space>="+recordlength+" ORDER BY space ASC", null);
    }

    public Cursor checkExistFreeSpace(long pos) {
        return db.rawQuery("SELECT * FROM freeSpace where pos="+pos, null);
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
        if(recordLength<space) {
            insertFreeSpaceWitchOutCompressTrieFile(pos + recordLength, space - recordLength);
        }
    }

    public void addPosInFreeSpaceMap(long pos, int keyNodeSize, int selfChildArraySize){
        insertFreeSpaceWitchOutCompressTrieFile(pos, 4+keyNodeSize+20+32+selfChildArraySize);
    }

    public void insertFreeSpaceWitchOutCompressTrieFile(long pos, int space){
        cv.put("pos", pos);
        cv.put("space", space);
        db.insert("freeSpace", null, cv);
        cv.clear();
    }

    public void insertFreeSpaceWitchCompressTrieFile(long pos, int space, long secondPos, int secondSpace){
        deleteFreeSpace(pos, space, space);
        deleteFreeSpace(secondPos, secondSpace, secondSpace);
        if (pos > secondPos) {
            pos = secondPos;
        }
        space = space + secondSpace;
        insertFreeSpaceWitchOutCompressTrieFile(pos, space);
    }
    /////////MyKey/////////////////////////////////////////////////////////////////////////////////
    public Pair getMyKey() {
        return myKey;
    }

    public void setMyKey() {
        Cursor query = db.rawQuery("SELECT * FROM users where privKey IS NOT NULL", null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey"), privKeyColIndex = query.getColumnIndex("privKey");
            myKey = new Pair(query.getBlob(pubKeyColIndex),query.getBlob(privKeyColIndex));
            //update self key in trie
            query.close();
        }else{
            ECKey myECKey=new ECKey();
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
    public void sendMsg(byte msgType, byte[] payload) {
        if(mClient != null && mClient.isConnected() && payload.length>0) {
            byte[] type = new byte[1];
            type[0] = msgType;
            byte[] sig = Utils.Sig(
                    (byte[]) getMyKey().second,
                    Sha256Hash.hash(Bytes.concat(type, Utils.getBytesPart(payload,0, 8)))
            );
            mClient.send(Bytes.concat(type, Ints.toByteArray(sig.length), sig, payload));
        }
    }

    public Cursor getPrefixByPos(long pos) {
        return db.rawQuery("SELECT * FROM sync where pos="+pos, null);
    }

    public void addPrefixByPos(long pos, byte[] key, byte[] age, boolean exist, String history){
        cv.put("pos", pos);
        cv.put("prefix", key);
        cv.put("age", age);
        cv.put("history", history);
        cv.put("exist", exist ? 1 : 0);
        db.insert("sync", null, cv);
        cv.clear();
    }

    public void clearPrefixTable() {
        Cursor query = db.rawQuery("DELETE FROM sync", null);
        query.close();
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////
}