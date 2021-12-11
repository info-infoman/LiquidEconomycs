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
            // создаем таблицу с полями
            db.execSQL("create table users ("
                    + "id integer primary key autoincrement,"
                    + "pubKey BLOB,"
                    + "privKey BLOB" + ");");
            db.execSQL("create table freeSpace ("
                    + "id integer primary key autoincrement,"
                    + "pos LONG,"
                    + "space int" + ");");
            db.execSQL("create table sync ("
                    + "id integer primary key autoincrement,"
                    + "pos LONG,"
                    + "prefix BLOB,"
                    + "age BLOB,"
                    + "exist integer" + ");");
            db.execSQL("create table signalServers ("
                    + "id integer primary key autoincrement,"
                    + "host text,"
                    + "port integer" + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
}
