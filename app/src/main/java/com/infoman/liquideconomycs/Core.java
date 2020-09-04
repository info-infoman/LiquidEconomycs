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
import org.bitcoinj.core.SignatureDecodeException;

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
        return db.rawQuery("SELECT * FROM freeSpace where space>="+recordlength, null);
    }

    public void deleteFreeSpace(long pos, int recordLength, int space) {
        db.delete("freeSpace", "pos = ?", new String[]{String.valueOf(pos)});
        if(recordLength<space) {
            insertFreeSpaceWitchCompressTrieFile(pos + recordLength + 1, space - recordLength);
        }
    }

    public void addPosInFreeSpaceMap(long pos, int keyNodeSize, int selfChildArraySize){
        insertFreeSpaceWitchCompressTrieFile(pos, 4+keyNodeSize+20+32+selfChildArraySize);
    }

    public void insertFreeSpaceWitchCompressTrieFile(long pos, int space){
        long p;
        int posColIndex, spaceColIndex, s;
        Cursor startQ = db.rawQuery("SELECT * FROM freeSpace WHERE pos+space+1="+pos, null);
        Cursor endQ = db.rawQuery("SELECT * FROM freeSpace WHERE pos="+pos+1, null);
        if (startQ.getCount() > 0 && startQ.moveToFirst()) {
            posColIndex = startQ.getColumnIndex("pos");
            spaceColIndex = startQ.getColumnIndex("space");
            p = startQ.getLong(posColIndex);
            s = startQ.getInt(spaceColIndex);
            deleteFreeSpace(p, s, s);
            pos = p;
            space = space+s;
        }
        startQ.close();

        if (endQ.getCount() > 0 && endQ.moveToFirst()) {
            posColIndex = endQ.getColumnIndex("pos");
            spaceColIndex = endQ.getColumnIndex("space");
            p = endQ.getLong(posColIndex);
            s = endQ.getInt(spaceColIndex);
            deleteFreeSpace(p, s, s);
            space = space+s;
        }
        endQ.close();

        cv.put("pos", pos);
        cv.put("space", space);
        db.insert("freeSpace", null, cv);
        cv.clear();
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
            byte[] myPrivKey = myECKey.getPrivKeyBytes(), myPubKey = ECKey.fromPrivate((byte[]) myPrivKey).getPubKey();
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
    public void sendMsg(byte msgType, byte[] payload) throws SignatureDecodeException {
        if(mClient != null && mClient.isConnected() && payload.length>0) {
            byte[] type = new byte[1];
            type[0] = (msgType == Utils.getHashs ? Utils.hashs : Utils.getHashs);
            byte[] sig = Utils.Sig((byte[]) getMyKey().second, Sha256Hash.hash(Bytes.concat(type, payload)));
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

    public void addPrefixByPos(long pos, byte[] key, byte[] age){
        cv.put("pos", pos);
        cv.put("pubKey", key);
        cv.put("age", age);
        db.insert("sync", null, cv);
        cv.clear();
    }

    public void clearPrefixTable() {
        Cursor query = db.rawQuery("DELETE FROM sync", null);
        query.close();
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////
}