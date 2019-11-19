package com.example.liquideconomycs;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class Sync extends IntentService {
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    public static final String ACTION_SYNC = "com.example.liquideconomycs.Sync.action.SYNC";
    public static final String BROADCAST_ACTION_END = "com.example.liquideconomycs.Sync.action.END";

    // TODO: Rename parameters
    private static final String EXTRA_SIGNAL_SERVER = "com.example.liquideconomycs.Sync.extra.SIGNAL_SERVER";
    private static final String EXTRA_PUBKEY = "com.example.liquideconomycs.Sync.extra.PUBKEY";

    public Sync() {
        super("Sync");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionSync(Context context, String signalServer, byte[] pubKey) {
        Intent intent = new Intent(context, Sync.class);
        intent.setAction(ACTION_SYNC);
        intent.putExtra(EXTRA_SIGNAL_SERVER, signalServer);
        intent.putExtra(EXTRA_PUBKEY, pubKey);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SYNC.equals(action)) {
                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER);
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_PUBKEY);




                //todo sync
                broadcastActionMsg();
            }
        }
    }

    // called to send data to Activity
    public void broadcastActionMsg() {
        Intent intent = new Intent(BROADCAST_ACTION_END);
        LocalBroadcastManager bm = LocalBroadcastManager.getInstance(this);
        bm.sendBroadcast(intent);
    }

    private void connect(String hostPort){
        //socket
        List<BasicNameValuePair> mExtraHeaders = Arrays.asList(new BasicNameValuePair("Cookie", "session=abcd"));
        WebSocketClient mClient = new WebSocketClient(new WebSocketClient.Listener() {

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
        mClient.connect(URI.create(hostPort));
    }
}
