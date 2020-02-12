package com.example.liquideconomycs;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

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

        setMyKey();

        //MySingleton.initInstance();
    }

    /////////TRIE//////////////////////////////////////////////////////////////////////////////////
    //simple
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

    //sync
    public byte[] trieGetNodeWitchChildsHashs(long pos) {
        return trie.getNodeWitchChildsHashs(pos);
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /////////MY///////////////////////////////////////////////////////////////////////////////////
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