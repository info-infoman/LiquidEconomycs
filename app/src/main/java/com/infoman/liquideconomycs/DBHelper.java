package com.infoman.liquideconomycs;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBHelper extends SQLiteOpenHelper {

    private static final String LOG_TAG = "DBHelper: ";

    public DBHelper(Context context) {
            super(context, "myDB", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(LOG_TAG, "--- onCreate database ---");
            db.execSQL("create table syncServers ("
                    + "id integer primary key autoincrement,"
                    + "server String,"
                    + "dateTimeLastSync Long" + ");");
            db.execSQL("create table users ("
                    + "id integer primary key autoincrement,"
                    + "pubKey BLOB,"
                    + "privKey BLOB" + ");");
            db.execSQL("create table mainCount ("
                    + "age integer primary key,"
                    + "count_ Long);");
            db.execSQL("create table main ("
                    + "pubKey blob primary key,"
                    + "age integer);");
            db.execSQL("CREATE INDEX idx_date ON main (age);");
            db.execSQL("CREATE TRIGGER bulk_insert_main\n" +
                    "BEFORE INSERT ON main\n" +
                    "WHEN NOT EXISTS (SELECT pubKey FROM main WHERE pubKey = NEW.pubKey)\n" +
                    "BEGIN\n" +
                    "  INSERT INTO mainCount (age, count_) VALUES (NEW.age, 1);\n" +
                    "END;");
            db.execSQL("CREATE TRIGGER bulk_update_main\n" +
                    "BEFORE INSERT ON main\n" +
                    "WHEN EXISTS (SELECT pubKey FROM main WHERE pubKey = NEW.pubKey AND age + 0 < NEW.age + 0)\n" +
                    "BEGIN\n" +
                    "  UPDATE main SET age = (NEW.age)\n" +
                    "    WHERE pubKey = NEW.pubKey;\n" +
                    "  INSERT INTO mainCount (age, count_) VALUES (NEW.age, 1);\n" +
                    "  SELECT raise(IGNORE);\n" +
                    "END;");
            db.execSQL("CREATE TRIGGER bulk_new_update_main\n" +
                    "BEFORE UPDATE ON main\n" +
                    "BEGIN\n" +
                    "  INSERT INTO mainCount (age, count_) VALUES (OLD.age, -1);\n" +
                    "END;");
            db.execSQL("CREATE TRIGGER bulk_ignore_main\n" +
                    "BEFORE INSERT ON main\n" +
                    "WHEN EXISTS (SELECT pubKey FROM main WHERE pubKey = NEW.pubKey AND age + 0 >= NEW.age + 0)\n" +
                    "BEGIN\n" +
                    "  SELECT raise(IGNORE);\n" +
                    "END;");
            db.execSQL("CREATE TRIGGER bulk_update_mainCount\n" +
                    "BEFORE INSERT ON mainCount\n" +
                    "WHEN EXISTS (SELECT age, count_ FROM mainCount WHERE age = NEW.age)\n" +
                    "BEGIN\n" +
                    "  UPDATE mainCount\n" +
                    "    SET count_ = (count_ + NEW.count_)\n" +
                    "    WHERE age = NEW.age;\n" +
                    "  SELECT raise(IGNORE);\n" +
                    "END;");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }
}
