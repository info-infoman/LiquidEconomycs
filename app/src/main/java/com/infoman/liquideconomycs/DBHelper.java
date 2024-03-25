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
            db.execSQL("create table main ("
                    + "pubKey blob primary key,"
                    + "age integer);");
            db.execSQL("CREATE INDEX idx_date ON main (age);");
            db.execSQL("create table mainCount ("
                    + "age integer primary key,"
                    + "count Long);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
}
