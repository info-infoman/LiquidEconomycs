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

    public static final String EXTRA_MASTER = "com.example.liquideconomycs.TrieProcessor.extra.MASTER";
    public static final String EXTRA_CMD = "com.example.liquideconomycs.TrieProcessor.extra.CMD";
    private static final String EXTRA_nodeDir = "com.example.liquideconomycs.TrieProcessor.extra.nodeDir";
    //input fnc
    private static final String ACTION_GetHash = "com.example.liquideconomycs.TrieProcessor.action.GetHash";
    private static final String ACTION_Insert = "com.example.liquideconomycs.TrieProcessor.action.Insert";

    //input params
    private static final String EXTRA_POS = "com.example.liquideconomycs.TrieProcessor.extra.POS";
    private static final String EXTRA_KEY = "com.example.liquideconomycs.TrieProcessor.extra.KEY";
    private static final String EXTRA_VALUE = "com.example.liquideconomycs.TrieProcessor.extra.VALUE";


    public static final String BROADCAST_ACTION_ANSWER = "com.example.liquideconomycs.TrieProcessor.broadcast_action.ANSWER";
    public static final String EXTRA_ANSWER = "com.example.liquideconomycs.TrieProcessor.extra.ANSWER";


    // called by activity to communicate to service
    public static void startActionGetHash(Context context, String master, long pos, String nodeDir) {
        Intent intent = new Intent(context, TrieProcessor.class);
        intent.setAction(ACTION_GetHash);

        intent.putExtra(EXTRA_nodeDir, nodeDir);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_POS, pos);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionInsert(Context context, String master, byte[] key, byte[] value, String nodeDir) {

        Intent intent = new Intent(context, TrieProcessor.class);

        intent.setAction(ACTION_Insert);

        intent.putExtra(EXTRA_nodeDir, nodeDir);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_KEY, key);
        intent.putExtra(EXTRA_VALUE, value);
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
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "GetHash";
                final long pos = intent.getLongExtra(EXTRA_POS,0L);
                final String nodeDir = intent.getStringExtra(EXTRA_nodeDir);
                ////////////////////////////////////////////////////////////////
                try {
                    mTrie = new Trie(nodeDir);
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
                final String nodeDir = intent.getStringExtra(EXTRA_nodeDir);
                ////////////////////////////////////////////////////////////////
                try {
                    mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, mTrie.insert(0,key, value, 0L));
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