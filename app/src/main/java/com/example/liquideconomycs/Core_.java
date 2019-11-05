package com.example.liquideconomycs;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;

import org.apache.http.message.BasicNameValuePair;

import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class Core_ extends IntentService {
    private static final String ACTION_FOO = "com.example.liquideconomycs.action.FOO";
    private static final String EXTRA_PARAM_A = "com.example.liquideconomycs.extra.PARAM_A";

    public static final String BROADCAST_ACTION_BAZ = "com.example.liquideconomycs.broadcast_action.FOO";
    public static final String EXTRA_PARAM_B = "com.example.liquideconomycs.extra.PARAM_B";

    // called by activity to communicate to service
    public static void startActionFoo(Context context, String param1) {
        Intent intent = new Intent(context, Core_.class);
        intent.setAction(ACTION_FOO);
        intent.putExtra(EXTRA_PARAM_A, param1);
        context.startService(intent);
    }

    public Core_() {
        super("Core_");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_FOO.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_PARAM_A);
                // do something
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