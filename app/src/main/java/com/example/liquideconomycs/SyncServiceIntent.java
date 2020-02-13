package com.example.liquideconomycs;

import android.app.IntentService;
import android.app.Notification;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.apache.http.message.BasicNameValuePair;

import androidx.core.util.Pair;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static com.example.liquideconomycs.TrieServiceIntent.*;

public class SyncServiceIntent extends IntentService {

    private static final String ACTION_Start = "com.example.liquideconomycs.SyncServiceIntent.action.Start";
    private static final String EXTRA_SIGNAL_SERVER = "com.example.liquideconomycs.SyncServiceIntent.extra.SIGNAL_SERVER";
    private static final String EXTRA_Slave = "com.example.liquideconomycs.SyncServiceIntent.extra.SLAVE";
    private static final String EXTRA_KEY = "com.example.liquideconomycs.SyncServiceIntent.extra.KEY";
    private Core app;
    private boolean isSync;

    public static void startActionSync(Context context, String signalServer, boolean slave) {
        Intent intent = new Intent(context, SyncServiceIntent.class);
        intent.setAction(ACTION_Start);
        intent.putExtra(EXTRA_SIGNAL_SERVER, signalServer);
        intent.putExtra(EXTRA_Slave, slave);
        context.startService(intent);
    }

    public void sendMsg(byte msgType, byte[] payload) {
        if(app.mClient != null && app.mClient.isConnected()) {
            byte[] type = new byte[1];
            type[0] = msgType;
            //todo sig
            byte[] sig = payload;
            app.mClient.send(Bytes.concat(type, Ints.toByteArray(sig.length), sig, payload));
        }
    }

    public void disconnect() {
        if(app.mClient != null && app.mClient.isConnected()) {
            isSync = true;
            app.mClient.disconnect();
        }
    }

    public SyncServiceIntent() throws FileNotFoundException {
        super("SyncServiceIntent");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        app = (Core) getApplicationContext();
        Log.i("liquideconomycs", "Service: SyncServiceIntent is create");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_Start.equals(action)) {
                isSync = false;
                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER);
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_KEY);
                final boolean slave = intent.getBooleanExtra(EXTRA_Slave,false);
                final Pair myKey = app.getMyKey();

                ////////////////////////////////////////////////////////////////
                Notification.Builder builder = new Notification.Builder(getBaseContext())
                        .setTicker("SyncServiceIntent") // use something from something from R.string
                        .setContentTitle("liquid economycs") // use something from something from
                        .setContentText("Sync liquid base") // use something from something from
                        .setProgress(0, 0, true); // display indeterminate progress

                startForeground(9991, builder.build());

                //todo sync processor
                List<BasicNameValuePair> mExtraHeaders = Arrays.asList(new BasicNameValuePair("Cookie", "session=abcd"));
                app.mClient = new WebSocketClient(new WebSocketClient.Listener() {

                    private static final String TAG = "WebSocketClient";

                    @Override
                    public void onConnect() {
                        Log.d(TAG, "Connected!");
                        if(!slave){
                            sendMsg(Utils.getHashs, Longs.toByteArray(0L));
                        }
                    }

                    @Override
                    public void onMessage(String message) {
                        Log.d(TAG, String.format("Got string message! %s", message));
                    }

                    @Override
                    public void onMessage(byte[] data) {
                        Log.d(TAG, String.format("Got binary message! %s", data.toString()));
                        byte msgType    = Utils.getBytesPart(data,0,1)[0];
                        int length      = Ints.fromByteArray(Utils.getBytesPart(data,1,4));
                        int sigLength   = Ints.fromByteArray(Utils.getBytesPart(data,5,4));
                        byte[] sig      = Utils.getBytesPart(data,9, sigLength);
                        byte[] payload  = Utils.getBytesPart(data, sigLength, length);
                        //todo check sig

                        //slave - if not owner server - who give work
                        //
                        if((slave && msgType != Utils.getHashs) || (!slave && msgType != Utils.hashs)){
                            disconnect();
                        }else {
                            startActionGetAnswer(getApplicationContext(), "SyncServiceIntent", msgType, payload);
                        }

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

                app.mClient.connect(URI.create(signalServer+(slave ? "/?myKey="+String.valueOf(myKey.first) : "/?slave="+String.valueOf(pubKey))));

                while (!isSync){
                }

                stopForeground(true);

                ////////////////////////////////////////////////////////////////
            }
        }
    }
}