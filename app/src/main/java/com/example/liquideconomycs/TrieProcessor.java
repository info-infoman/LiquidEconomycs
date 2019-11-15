package com.example.liquideconomycs;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;

import org.apache.http.message.BasicNameValuePair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class TrieProcessor extends IntentService {
    private Trie      mTrie;
    private String nodeDir=getApplicationContext().getFilesDir().getAbsolutePath()+"/trie";

    //input params
    private static final String EXTRA_PARAM_POS = "com.example.liquideconomycs.TrieProcessor.extra.POS";
    private static final String EXTRA_PARAM_KEY = "com.example.liquideconomycs.TrieProcessor.extra.KEY";
    private static final String EXTRA_PARAM_VALUE = "com.example.liquideconomycs.TrieProcessor.extra.VALUE";
    //input fnc
    private static final String ACTION_GetHash = "com.example.liquideconomycs.TrieProcessor.action.GetHash";
    private static final String ACTION_Insert = "com.example.liquideconomycs.TrieProcessor.action.Insert";

    public static final String BROADCAST_ACTION_BAZ = "com.example.liquideconomycs.TrieProcessor.broadcast_action.BAZ";
    public static final String EXTRA_PARAM_B = "com.example.liquideconomycs.TrieProcessor.extra.PARAM_B";


    // called by activity to communicate to service
    public static void startActionGetHash(Context context, long pos) {
        Intent intent = new Intent(context, TrieProcessor.class);
        intent.setAction(ACTION_GetHash);
        intent.putExtra(EXTRA_PARAM_POS, pos);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionInsert(Context context, byte[] key, byte[] value) {
        Intent intent = new Intent(context, TrieProcessor.class);
        intent.setAction(ACTION_Insert);
        intent.putExtra(EXTRA_PARAM_KEY, key);
        intent.putExtra(EXTRA_PARAM_VALUE, value);
        context.startService(intent);
    }

    public TrieProcessor() {
        super("TrieProcessor");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_GetHash.equals(action)) {
                final long pos = intent.getLongExtra(EXTRA_PARAM_POS,0L);
                ////////////////////////////////////////////////////////////////
                try {
                    mTrie = new Trie(nodeDir);
                    broadcastActionBaz(mTrie.getHash(pos).toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Insert.equals(action)) {

                final byte[] key = intent.getByteArrayExtra(EXTRA_PARAM_KEY);
                final byte[] value = intent.getByteArrayExtra(EXTRA_PARAM_VALUE);
                ////////////////////////////////////////////////////////////////
                try {
                    mTrie = new Trie(nodeDir);
                    broadcastActionBaz(mTrie.insert(key,value).toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }
        }
    }

    // called to send data to Activity
    public void broadcastActionBaz(String param) {
        Intent intent = new Intent(BROADCAST_ACTION_BAZ);
        intent.putExtra(EXTRA_PARAM_B, param);
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