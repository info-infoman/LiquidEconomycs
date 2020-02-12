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

import static com.example.liquideconomycs.Utils.*;

public class Sync extends IntentService {

    public static final String EXTRA_MASTER = "com.example.liquideconomycs.Sync.extra.MASTER";
    public static final String EXTRA_CMD = "com.example.liquideconomycs.Sync.extra.CMD";

    private static final String ACTION_Start = "com.example.liquideconomycs.Sync.action.Start";

    private static final String EXTRA_SIGNAL_SERVER = "com.example.liquideconomycs.Sync.extra.SIGNAL_SERVER";
    private static final String EXTRA_Slave = "com.example.liquideconomycs.Sync.extra.SLAVE";
    private static final String EXTRA_KEY = "com.example.liquideconomycs.Sync.extra.KEY";

    public static final String BROADCAST_ACTION_ANSWER = "com.example.liquideconomycs.Sync.broadcast_action.ANSWER";
    public static final String EXTRA_ANSWER = "com.example.liquideconomycs.Sync.extra.ANSWER";

    public WebSocketClient mClient;
    public Core app;

    public static void startActionSync(Context context, String signalServer, boolean slave) {
        Intent intent = new Intent(context, Sync.class);
        intent.setAction(ACTION_Start);
        intent.putExtra(EXTRA_SIGNAL_SERVER, signalServer);
        intent.putExtra(EXTRA_Slave, slave);
        context.startService(intent);
    }

    public Sync() throws FileNotFoundException {
        super("Sync");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Context context = getApplicationContext();
        mClient = null;
        app = (Core) getApplicationContext();
        Log.i("Trie", "Service: Sync is create");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_Start.equals(action)) {
                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER);
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_KEY);
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final boolean slave = intent.getBooleanExtra(EXTRA_Slave,false);
                final String cmd = "Sync";
                final Pair myKey = app.getMyKey();

                ////////////////////////////////////////////////////////////////
                Notification.Builder builder = new Notification.Builder(getBaseContext())
                        .setTicker("Sync") // use something from something from R.string
                        .setContentTitle("liquid economycs") // use something from something from
                        .setContentText("Sync liquid base") // use something from something from
                        .setProgress(0, 0, true); // display indeterminate progress

                startForeground(9991, builder.build());

                //todo sync processor
                List<BasicNameValuePair> mExtraHeaders = Arrays.asList(new BasicNameValuePair("Cookie", "session=abcd"));
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
                        byte msgType    = Utils.getBytesPart(data,0,1)[0];
                        int length      = Ints.fromByteArray(Utils.getBytesPart(data,1,4));
                        int sigLength   = Ints.fromByteArray(Utils.getBytesPart(data,5,4));
                        byte[] sig      = Utils.getBytesPart(data,9, sigLength);
                        byte[] Payload  = Utils.getBytesPart(data, sigLength, length);
                        //todo check sig

                        //slave - if not owner server - who give work
                        //
                        if((slave && msgType != Utils.getHashs) || (!slave && msgType != Utils.hashs)){
                            disconnect();
                        }else {
                            getAnswer(msgType, Payload);
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

                mClient.connect(URI.create(signalServer+(slave ? "/?myKey="+String.valueOf(myKey.first) : "/?slave="+String.valueOf(pubKey))));

                stopForeground(true);

                broadcastActionMsg(master, cmd, new byte[0]);
                ////////////////////////////////////////////////////////////////
            }
        }
    }

    private void disconnect() {
        mClient.disconnect();
    }

    private void getAnswer(byte type, byte[] payload) {
        byte[] answer = new byte[0];
        if(type == Utils.getHashs){
            for(int i=0;i < payload.length/8;i++){
                answer = Bytes.concat(answer, app.trieGetNodeWitchChildsHashs(Longs.fromByteArray(Utils.getBytesPart(payload,i*8, 8))));
            }
        }else{
            //todo analise and construct map
            for(int i = 0; i < payload.length;) {
                byte nodeType           = Utils.getBytesPart(payload, i, 1)[0];
                byte keySize            = Utils.getBytesPart(payload, i + 1, 1)[0];
                byte[] key              = Utils.getBytesPart(payload, i + 2, keySize);
                byte[] childsMap        = Utils.getBytesPart(payload, i + 2 + keySize, 32);
                int childsCountInMap    = Utils.getChildsCountInMap(childsMap);
                int len                 = childsCountInMap * (nodeType==Utils.LEAF ? 2 : 8);
                byte[] childsArray      = Utils.getBytesPart(payload, i + 2 + keySize + 32, len);
                i                       = i + 2 + keySize + 32 + len;

            }
        }
        //todo add head msg

        mClient.send(answer);
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