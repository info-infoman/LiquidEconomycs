package com.infoman.liquideconomycs;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBHelper extends SQLiteOpenHelper {

    private static final String LOG_TAG = "DBHelper: ";

    public DBHelper(Context context) {
            // конструктор суперкласса
            super(context, "myDB", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(LOG_TAG, "--- onCreate database ---");
            //our pubKeys
            db.execSQL("create table users ("
                    + "id integer primary key autoincrement,"
                    + "pubKey BLOB,"
                    + "privKey BLOB" + ");");
            //table for free space in trie file
            db.execSQL("create table freeSpace ("
                    + "id integer primary key autoincrement,"
                    + "file LONG,"
                    + "pos LONG,"
                    + "space int" + ");");
            //cache pubKeys for insert after sync
            db.execSQL("create table sync ("
                    + "id integer primary key autoincrement,"
                    + "pos LONG,"
                    + "prefix BLOB,"
                    + "age int,"
                    + "exist integer" + ");");
            //clients on session
            db.execSQL("create table clients ("
                    + "id integer primary key autoincrement,"
                    + "pubKey BLOB" + ");");
            //cache node in blob for save in trie file
            db.execSQL("create table cacheNewNodeBlobs ("
                    + "id integer primary key autoincrement,"
                    + "file LONG,"
                    + "pos LONG,"
                    + "node BLOB" + ");");
            //cache backup node in blob for recovery trie file
            db.execSQL("create table cacheOldNodeBlobs ("
                    + "id integer primary key autoincrement,"
                    + "file LONG,"
                    + "pos LONG,"
                    + "node BLOB" + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
}
