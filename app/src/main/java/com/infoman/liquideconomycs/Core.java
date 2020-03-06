package com.infoman.liquideconomycs;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import org.bitcoinj.core.ECKey;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import androidx.core.util.Pair;

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
        return db.rawQuery("SELECT * FROM freeSpace where space="+recordlength, null);
    }

    public void deleteFreeSpace(long pos) {
        db.delete("freeSpace",  "pos = ?", new String[] { String.valueOf(pos) });
    }

    public void addPosInFreeSpaceMap(long pos, int keyNodeSize, int selfChildArraySize){
        cv.put("pos", pos);
        cv.put("space", 2+keyNodeSize+20+32+selfChildArraySize);
        db.insert("freeSpace", null, cv);
        cv.clear();
    }





    ///////////////////////////////////////////////////////////////////////////////////////////////

    /////////MyKey/////////////////////////////////////////////////////////////////////////////////
    public Pair getMyKey() {
        return myKey;
    }

    public void setMyKey() {
        Cursor query = db.rawQuery("SELECT * FROM users where privKey IS NOT NULL", null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey");
            int privKeyColIndex = query.getColumnIndex("privKey");
            myKey = new Pair(query.getBlob(pubKeyColIndex),query.getBlob(privKeyColIndex));
            //update self key in trie
            query.close();
        }else{
            ECKey myECKey=new ECKey();
            byte[] myPrivKey = myECKey.getPrivKeyBytes();
            byte[] myPubKey = myECKey.getPubKeyHash();
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
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /////////Sync/////////////////////////////////////////////////////////////////////////////////
    public void sendMsg(byte msgType, byte[] payload) {
        if(mClient != null && mClient.isConnected() && payload.length>0) {
            byte[] type = new byte[1];
            type[0] = (msgType == Utils.getHashs ? Utils.hashs : Utils.getHashs);
            byte[] sig = Utils.sigMsg((byte[]) getMyKey().second, type[0], payload);
            mClient.send(Bytes.concat(type, Ints.toByteArray(sig.length), sig, payload));
        }
    }

    public byte[] getPrefixByPos(long pos) {
        byte[] prefix = null;
        Cursor query = db.rawQuery("SELECT * FROM sync where pos="+pos, null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey");
            prefix = query.getBlob(pubKeyColIndex);
        }
        query.close();
        return prefix;
    }

    public void addPrefixByPos(long pos, byte[] key, byte[] age, boolean del){
        cv.put("pos", pos);
        cv.put("pubKey", key);
        cv.put("age", age);
        cv.put("del", del);
        db.insert("sync", null, cv);
        cv.clear();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
}