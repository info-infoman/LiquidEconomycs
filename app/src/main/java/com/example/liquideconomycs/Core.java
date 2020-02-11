package com.example.liquideconomycs;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.FileNotFoundException;
import java.io.IOException;

import androidx.core.util.Pair;

public class Core extends Application {
    private SQLiteDatabase db;
    private DBHelper dbHelper;
    private Trie trie;
    private Pair myKey;


    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        dbHelper        = new DBHelper(context);
        db              = dbHelper.getWritableDatabase();

        try {
            trie = new Trie(db,context.getFilesDir().getAbsolutePath()+ "/trie"+"/trie.dat");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Cursor query = db.rawQuery("SELECT * FROM users where my=TRUE", null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey");
            int privKeyColIndex = query.getColumnIndex("privKey");
            myKey = new Pair(query.getBlob(pubKeyColIndex),query.getBlob(privKeyColIndex));
        }

        //MySingleton.initInstance();
    }

    /////////TRIE//////////////////////////////////////////////////////////////////////////////////
    public byte[] trieGetHash(long pos) throws IOException {
        return trie.getHash(pos);
    }

    public byte[] trieFind(byte[] key, long pos) throws IOException {
        return trie.find(key, pos);
    }

    public byte[] trieDelete(byte[] key, long pos) throws IOException {
        return trie.delete(key, pos);
    }

    public byte[] trieInsert(byte[] key, byte[] age, long pos) throws IOException {
        return trie.insert(key, age, pos);
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /////////MY///////////////////////////////////////////////////////////////////////////////////
    public Pair getMyKey() {
        return myKey;
    }

    public void setMyKey(Pair myKey) {
        //return myKey;
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////

}