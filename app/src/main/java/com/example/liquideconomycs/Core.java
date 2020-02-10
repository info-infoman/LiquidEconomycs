package com.example.liquideconomycs;

import android.app.IntentService;
import android.app.Notification;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.apache.http.message.BasicNameValuePair;
import org.bitcoinj.core.ECKey;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static org.bitcoinj.core.Utils.sha256hash160;

public class Core extends IntentService {

    Trie trie;
    SQLiteDatabase db;
    DBHelper dbHelper;
    ContentValues cv;
    private static byte ROOT = 1; //
    private static byte BRANCH = 2;
    private static byte LEAF = 3;

    public static final String EXTRA_MASTER = "com.example.liquideconomycs.Core.extra.MASTER";
    public static final String EXTRA_CMD = "com.example.liquideconomycs.Core.extra.CMD";

    //input fnc
    private static final String ACTION_GetHash = "com.example.liquideconomycs.Core.action.GetHash";
    private static final String ACTION_Insert = "com.example.liquideconomycs.Core.action.Insert";
    private static final String ACTION_Delete = "com.example.liquideconomycs.Core.action.Delete";
    private static final String ACTION_Find = "com.example.liquideconomycs.Core.action.Find";
    private static final String ACTION_Sync = "com.example.liquideconomycs.Core.action.Sync";

    private static final String ACTION_Test = "com.example.liquideconomycs.Core.action.Test";

    //input params
    private static final String EXTRA_POS = "com.example.liquideconomycs.Core.extra.POS";
    private static final String EXTRA_KEY = "com.example.liquideconomycs.Core.extra.KEY";
    private static final String EXTRA_VALUE = "com.example.liquideconomycs.Core.extra.VALUE";
    private static final String EXTRA_SIGNAL_SERVER = "com.example.liquideconomycs.Core.extra.SIGNAL_SERVER";
    private static final String EXTRA_Slave = "com.example.liquideconomycs.Core.extra.SLAVE";

    public static final String BROADCAST_ACTION_ANSWER = "com.example.liquideconomycs.Core.broadcast_action.ANSWER";
    public static final String EXTRA_ANSWER = "com.example.liquideconomycs.Core.extra.ANSWER";


    public static void startActionTest(Context context, String master){
        Intent intent = new Intent(context, Core.class);
        intent.setAction(ACTION_Test);
        intent.putExtra(EXTRA_MASTER, master);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionGetHash(Context context, String master, long pos) {
        Intent intent = new Intent(context, Core.class);
        intent.setAction(ACTION_GetHash);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_POS, pos);
        context.startService(intent);
    }

    // called by activity to communicate to service
    public static void startActionInsert(Context context, String master, byte[] key, byte[] value) {
        Intent intent = new Intent(context, Core.class);
        intent.setAction(ACTION_Insert);
        intent.putExtra(EXTRA_MASTER, master);
        intent.putExtra(EXTRA_KEY, key);
        intent.putExtra(EXTRA_VALUE, value);
        context.startService(intent);
    }

    public static void startActionSync(Context context, String signalServer, boolean slave, byte[] Key) {
        Intent intent = new Intent(context, Core.class);
        intent.setAction(ACTION_Sync);
        intent.putExtra(EXTRA_SIGNAL_SERVER, signalServer);
        intent.putExtra(EXTRA_Slave, slave);
        intent.putExtra(EXTRA_KEY, Key);
        context.startService(intent);
    }

    public Core() throws FileNotFoundException {
        super("Core");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        dbHelper        = new DBHelper(context);
        cv              = new ContentValues();
        db              = dbHelper.getWritableDatabase();
        try {
            trie = new Trie(db,context.getFilesDir().getAbsolutePath()+ "/trie"+"/trie.dat");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Log.i("Trie", "Service: Trie is create");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_Test.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "Test";
                ////////////////////////////////////////////////////////////////
                //mTrie = new Trie(nodeDir);

                Notification.Builder builder = new Notification.Builder(getBaseContext())
                        .setTicker("Test") // use something from something from R.string
                        .setContentTitle("title") // use something from something from
                        .setContentText("text") // use something from something from
                        .setProgress(0, 0, true); // display indeterminate progress

                startForeground(9999, builder.build());

                for(int i=0;i<10512;i++){
                    ECKey myECKey=new ECKey();
                    byte[] myPrivKey = myECKey.getPrivKeyBytes();
                    byte[] myPubKey = myECKey.getPubKeyHash();

                    short age = 2;
                    try {
                        trie.insert(myPubKey, Shorts.toByteArray(age), 0L);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                stopForeground(true);

                broadcastActionMsg(master, cmd, new byte[0]);
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_GetHash.equals(action)) {
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final String cmd = "GetHash";
                final long pos = intent.getLongExtra(EXTRA_POS,0L);
                ////////////////////////////////////////////////////////////////
                try {
                    //mTrie = new Trie(nodeDir);
                    broadcastActionMsg(master, cmd, trie.getHash(pos));
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
                    broadcastActionMsg(master, cmd, trie.insert(key, value, 0L));
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
                    broadcastActionMsg(master, cmd, trie.delete(key, 0L));
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
                    broadcastActionMsg(master, cmd, trie.find(key, 0L));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////////////////////////////////////////////////////////////////
            }

            if (ACTION_Sync.equals(action)) {
                final String signalServer = intent.getStringExtra(EXTRA_SIGNAL_SERVER);
                final byte[] pubKey = intent.getByteArrayExtra(EXTRA_KEY);
                final String master = intent.getStringExtra(EXTRA_MASTER);
                final boolean slave = intent.getBooleanExtra(EXTRA_Slave,false);
                final byte[] myKey = getMyKey();
                final String cmd = "Sync";

                ////////////////////////////////////////////////////////////////
                Notification.Builder builder = new Notification.Builder(getBaseContext())
                        .setTicker("Sync") // use something from something from R.string
                        .setContentTitle("liquid economycs") // use something from something from
                        .setContentText("Sync liquid base") // use something from something from
                        .setProgress(0, 0, true); // display indeterminate progress

                startForeground(9991, builder.build());

                //todo sync processor

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
                        byte msgType    = Protocol.getMsgType(data)[0];
                        int length      = Protocol.getLength(data);
                        int sigLength   = Protocol.getSigLength(data);
                        byte[] sig      = Protocol.getSig(data, sigLength);
                        byte[] Payload  = Protocol.getPayload(data, sigLength, length);
                        //todo check sig

                        //slave - if not owner server - who give work
                        //
                        if(slave && msgType==Protocol.getHashs){
                            //todo formed Hashs answer
                            Hashs(Payload, slave);
                        }else if(!slave && msgType==Protocol.Hashs){
                            //todo formed getHashs ask
                            getHashs(Payload, slave);
                        }else{
                            this.disconnect();//must close room on a server
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

                    private void Hashs(byte[] payload, boolean slave) {
                        mClient.send(payload);
                    }

                    private void getHashs(byte[] payload, boolean slave) {
                        mClient.send(payload);
                    }

                }, mExtraHeaders);
                mClient.connect(URI.create(signalServer+(slave ? "/?myKey="+String.valueOf(myKey) : "/?slave="+String.valueOf(pubKey))));


                stopForeground(true);

                broadcastActionMsg(master, cmd, new byte[0]);
                ////////////////////////////////////////////////////////////////
            }
        }
    }



    private byte[] getMyKey() {
        Cursor query = db.rawQuery("SELECT * FROM users where my=TRUE", null);
        if (query.moveToFirst()) {
            int pubKeyColIndex = query.getColumnIndex("pubKey");
            return query.getBlob(pubKeyColIndex);
        }
        return null;
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