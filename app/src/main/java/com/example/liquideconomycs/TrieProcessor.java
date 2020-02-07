package com.example.liquideconomycs;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.apache.http.message.BasicNameValuePair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class TrieProcessor extends IntentService {

    DBHelper dbHelper;
    SQLiteDatabase db;
    Trie      mTrie;
    public static final String EXTRA_MASTER = "com.example.liquideconomycs.TrieProcessor.extra.MASTER";
    public static final String EXTRA_CMD = "com.example.liquideconomycs.TrieProcessor.extra.CMD";
    //input fnc
    private static final String ACTION_GetHash = "com.example.liquideconomycs.TrieProcessor.action.GetHash";
    private static final String ACTION_Insert = "com.example.liquideconomycs.TrieProcessor.action.Insert";
    private static final String ACTION_Delete = "com.example.liquideconomycs.TrieProcessor.action.Delete";
    private static final String ACTION_Find = "com.example.liquideconomycs.TrieProcessor.action.Find";

    //input params
    private static final String EXTRA_POS = "com.example.liquideconomycs.TrieProcessor.extra.POS";
    private static final String EXTRA_KEY = "com.example.liquideconomycs.TrieProcessor.extra.KEY";
    private static final String EXTRA_VALUE = "com.example.liquideconomycs.TrieProcessor.extra.VALUE";

    public static final String BROADCAST_ACTION_ANSWER = "com.example.liquideconomycs.TrieProcessor.broadcast_action.ANSWER";
    public static final String EXTRA_ANSWER = "com.example.liquideconomycs.TrieProcessor.extra.ANSWER";


    // called by activity to communicate to service
    public static void startActionGetHash(Context context, String master, long pos) {
        Intent intent = new Intent(context, TrieProcessor.class);
        intent.setAction(ACTION_GetHash);

        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_POS, pos);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionInsert(Context context, String master, byte[] key, byte[] value) {

        Intent intent = new Intent(context, TrieProcessor.class);

        intent.setAction(ACTION_Insert);

        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_KEY, key);
        intent.putExtra(EXTRA_VALUE, value);
        context.startService(intent);
    }

    public TrieProcessor() throws FileNotFoundException {
        super("TrieProcessor");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(dbHelper==null) {
            Context context = getApplicationContext();
            dbHelper = new DBHelper(context);
            db = dbHelper.getWritableDatabase();
            try {
                mTrie = new Trie(getApplicationContext().getFilesDir().getAbsolutePath()+"/trie", db);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }


        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_GetHash.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "GetHash";
                final long pos = intent.getLongExtra(EXTRA_POS,0L);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, mTrie.getHash(pos));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Insert.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Insert";
                final byte[] key = intent.getByteArrayExtra(EXTRA_KEY);
                final byte[] value = intent.getByteArrayExtra(EXTRA_VALUE);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, mTrie.insert(key, value, 0L));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Delete.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Delete";
                final byte[] key = intent.getByteArrayExtra(EXTRA_KEY);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, mTrie.delete(key, 0L));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Find.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Find";
                final byte[] key = intent.getByteArrayExtra(EXTRA_KEY);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, mTrie.find(key, 0L));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }
        }
    }

    // called to send data to Activity
    public void broadcastActionMsg(String master, String cmd, byte[] answer) {
        Intent intent = new Intent(BROADCAST_ACTION_ANSWER);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_CMD, cmd);
        intent.putExtra(EXTRA_ANSWER, answer);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.sendBroadcast(intent);
    }


}
/*
mExtraHeaders = Arrays.asList(new BasicNameValuePair("Cookie", "session=abcd"));
        mClient = new WebSocketClient(new WebSocketClient.Listener() {

private static final String TAG = "WebSocketClient";

@Override
public void onConnect() {
        Log.d(TAG, "Connected!");
        //wsOnConnected();
        }

@Override
public void onMessage(String message) {
        Log.d(TAG, String.format("Got string message! %s", message));
        }

@Override
public void onMessage(byte[] data) {
        Log.d(TAG, String.format("Got binary message! %s", data.toString()));
        }

@Override
public void onDisconnect(int code, String reason) {
        Log.d(TAG, String.format("Disconnected! Code: %d Reason: %s", code, reason));
        }

@Override
public void onError(Exception error) {
        Log.e(TAG, "Error!", error);
        }

        }, mExtraHeaders);
        String hostPort="";
        mClient.connect(URI.create(hostPort));
        */