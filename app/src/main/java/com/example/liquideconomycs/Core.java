package com.example.liquideconomycs;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import androidx.core.util.Pair;

public class Core extends Application {
    private DBHelper dbHelper;
    private SQLiteDatabase db;
    private ContentValues cv;
    private Pair myKey;
    public RandomAccessFile trie;
    public WebSocketClient mClient;

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        dbHelper        = new DBHelper(context);
        db              = dbHelper.getWritableDatabase();
        cv              = new ContentValues();
        mClient         = null;

        try {
            trie = new RandomAccessFile(context.getFilesDir().getAbsolutePath()+ "/trie"+"/trie.dat", "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        setMyKey();

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
        Cursor query = db.rawQuery("SELECT * FROM users where privKey <> NULL", null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey");
            int privKeyColIndex = query.getColumnIndex("privKey");
            myKey = new Pair(query.getBlob(pubKeyColIndex),query.getBlob(privKeyColIndex));
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////

}