package com.infoman.liquideconomycs;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import static com.infoman.liquideconomycs.Utils.ACTION_OPTIMIZE;
public class DBOptimizeServiceIntent extends IntentService {

    private Core app;

    public DBOptimizeServiceIntent() {
        super("DBOptimizeServiceIntent");
    }

    // called by activity to communicate to service
    public static void startActionOptimise(Context context) {
        Intent intent = new Intent(context, DBOptimizeServiceIntent.class)
            .setAction(ACTION_OPTIMIZE);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //android.os.Debug.waitForDebugger();
        app = (Core) getApplicationContext();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_OPTIMIZE.equals(action)) {
                ////////////////////////////////////////////////////////////////
                optimize();
                ////////////////////////////////////////////////////////////////
            }
        }
    }

    private void optimize() {
        Cursor query = app.getFreeSpaceWitchCompress();
        int s, ss;
        long p, sp;
        while(query.getCount() > 0 && query.moveToFirst()){
            p = query.getLong(query.getColumnIndex("pos"));
            s = query.getInt(query.getColumnIndex("space"));
            sp = query.getLong(query.getColumnIndex("Second_pos"));
            ss = query.getInt(query.getColumnIndex("Second_space"));

            app.insertFreeSpaceWitchCompressTrieFile(p, s, sp, ss);
            query.close();
            query = app.getFreeSpaceWitchCompress();
        }
        query.close();
    }
}
